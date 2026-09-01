import logging
import re
import uuid
from collections.abc import AsyncIterator, Awaitable, Callable
from contextlib import asynccontextmanager
from contextvars import ContextVar

import sentry_sdk
from fastapi import FastAPI, Request, Response
from fastapi.encoders import jsonable_encoder
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

from app import __version__
from app.config import get_settings
from app.exceptions import AppException
from app.routers import account, badge, goal, health, legal, profile, weekly_plan

logger = logging.getLogger(__name__)

# 요청 상관관계 ID — 미들웨어가 요청마다 설정, 로그 포맷(%(request_id)s)에 포함해
# 다중 replica(min/max 1/3) 환경에서 한 요청의 로그를 추적할 핸들을 제공한다.
request_id_ctx: ContextVar[str] = ContextVar("request_id", default="-")

# 클라이언트가 보낸 X-Request-ID 를 채택할 조건. 이 값은 로그 포맷 `[%(request_id)s]` 에
# 그대로 들어가므로 개행을 허용하면 **로그에 가짜 줄을 삽입**할 수 있고(CWE-117), 길이 제한이
# 없으면 로그 볼륨 증폭에 쓰인다. 흔한 추적 ID(W3C traceparent 의 trace-id, UUID hex,
# `req_42` 같은 형태)는 전부 이 문자 집합 안에 들어간다.
_REQUEST_ID_RE = re.compile(r"^[A-Za-z0-9_-]{1,64}$")


class _RequestIdLogFilter(logging.Filter):
    """모든 LogRecord 에 현재 요청의 request_id 를 주입(요청 밖이면 '-')."""

    def filter(self, record: logging.LogRecord) -> bool:
        record.request_id = request_id_ctx.get()
        return True


# 로깅은 모듈 import 시점에 1회 구성한다. 과거엔 lifespan startup 안에서 basicConfig 를
# 호출해 uvicorn 이 root 로거를 선점한 경우 no-op 이 되던 footgun 이 있었다. force=True 로
# 기존 root 핸들러를 재설정하고 request_id 필터를 부착한다.
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s [%(request_id)s] %(name)s: %(message)s",
    force=True,
)
for _handler in logging.getLogger().handlers:
    _handler.addFilter(_RequestIdLogFilter())


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    settings = get_settings()

    # DB 엔진 + 세션팩토리 1회 생성 → app.state에 저장
    # pool_pre_ping: idle 후 PG/방화벽이 끊은 연결을 checkout 시 SELECT 1 로 사전 검증해
    # warm baseline 인스턴스의 첫 요청이 "connection closed" 500 으로 떨어지는 것을 방지한다.
    engine = create_async_engine(
        settings.database_url, pool_size=3, max_overflow=0, pool_pre_ping=True
    )
    app.state.session_factory = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)

    if settings.sentry_dsn:
        sentry_sdk.init(
            dsn=settings.sentry_dsn,
            traces_sample_rate=1.0 if settings.environment == "development" else 0.2,
            environment=settings.environment,
        )

    yield

    # 종료 시 엔진 정리
    await engine.dispose()


app = FastAPI(title="eundunHealth API", version=__version__, lifespan=lifespan)

# CORS — starlette 0.49+에서 lifespan 내부 add_middleware가 금지되어 모듈 레벨에서 등록.
# get_settings()는 @lru_cache이므로 환경변수만 1회 읽으며, 테스트는 dependency_override로
# 라우터 레벨에서 settings를 갈아끼우므로 CORS 구성이 테스트에 미치는 영향은 없다.
_cors_settings = get_settings()
app.add_middleware(
    CORSMiddleware,
    allow_origins=_cors_settings.cors_origins,
    allow_methods=["*"],
    allow_headers=["*"],
)


# 요청 상관관계 미들웨어 — 모듈 레벨 등록(룰 4: lifespan 내부 add_middleware 금지).
# CORS 뒤에 등록되어 가장 바깥에서 동작 → 모든 처리 전에 request_id 를 설정한다.
@app.middleware("http")
async def request_id_middleware(
    request: Request, call_next: Callable[[Request], Awaitable[Response]]
) -> Response:
    supplied = request.headers.get("X-Request-ID")
    # 형식 위반이면 400 이 아니라 조용히 자체 생성으로 대체한다 — 중간 프록시가 붙인 헤더
    # 때문에 정상 요청이 깨지면 안 된다. 추적성보다 로그 무결성이 우선이다.
    rid = supplied if supplied and _REQUEST_ID_RE.match(supplied) else uuid.uuid4().hex[:12]
    token = request_id_ctx.set(rid)
    try:
        response = await call_next(request)
    finally:
        request_id_ctx.reset(token)
    response.headers["X-Request-ID"] = rid
    return response


# 글로벌 에러 핸들러
@app.exception_handler(AppException)
async def app_exception_handler(request: Request, exc: AppException) -> JSONResponse:
    return JSONResponse(
        status_code=exc.status_code,
        content={"code": exc.code, "message": exc.message},
    )


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError) -> JSONResponse:
    """422 를 **값 없이** 로깅한다.

    Pydantic 의 `ErrorDetails` 는 `input`("The input provided for validation")을 포함하고
    `exc.body` 는 요청 원문이다. 그대로 남기면 `PUT /profile` 의 키·몸무게·체지방·근육량이
    Log Analytics 로 흘러간다 — 건강 데이터를 진단 편의로 축적할 이유가 없다.

    진단에 필요한 것은 **어느 필드가 어떤 규칙을 어겼는지**이므로 `loc`·`type`·`msg` 만 남긴다.
    응답 body 는 그대로 둔다 — 받는 주체가 자기 자신이고, 바꾸면 API 호환성이 깨진다.

    다만 응답은 FastAPI 자체 핸들러와 똑같이 `jsonable_encoder` 를 통과시킨다(설치본
    `fastapi/exception_handlers.py` 실측). 현재 스키마는 `Field(ge/le)` 제약뿐이라 `errors()`
    가 이미 JSON 직렬화 가능하지만(3 케이스 `json.dumps` 실측 OK), `Decimal`·`date` 나
    `ValueError` 를 `ctx` 에 담는 커스텀 validator 가 하나라도 들어오면 **응답 렌더링 단계**에서
    터져 422 가 500 으로 바뀐다. 핸들러 밖에서 터지므로 원인 추적도 어렵다.

    출처: https://pydantic.dev/docs/validation/latest/errors/errors/
    """
    redacted = [
        {"loc": e.get("loc"), "type": e.get("type"), "msg": e.get("msg")} for e in exc.errors()
    ]
    logger.warning(
        "Request validation failed | path=%s method=%s errors=%s",
        request.url.path,
        request.method,
        redacted,
    )
    return JSONResponse(status_code=422, content={"detail": jsonable_encoder(exc.errors())})


@app.exception_handler(Exception)
async def unhandled_exception_handler(request: Request, exc: Exception) -> JSONResponse:
    logger.exception("Unhandled exception")
    if sentry_sdk.is_initialized():
        sentry_sdk.capture_exception(exc)
    return JSONResponse(status_code=500, content={"code": "INTERNAL_ERROR", "message": "서버 내부 오류"})


app.include_router(health.router)
app.include_router(profile.router)
app.include_router(weekly_plan.router)
app.include_router(badge.router)
app.include_router(account.router)
app.include_router(goal.router)
app.include_router(legal.router)
