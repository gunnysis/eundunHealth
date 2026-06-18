import logging
import uuid
from collections.abc import AsyncIterator, Awaitable, Callable
from contextlib import asynccontextmanager
from contextvars import ContextVar

import sentry_sdk
from fastapi import FastAPI, Request, Response
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

from app import __version__
from app.config import get_settings
from app.exceptions import AppException
from app.routers import account, auth, badge, goal, health, legal, profile, weekly_plan

logger = logging.getLogger(__name__)

# 요청 상관관계 ID — 미들웨어가 요청마다 설정, 로그 포맷(%(request_id)s)에 포함해
# 다중 replica(min/max 1/3) 환경에서 한 요청의 로그를 추적할 핸들을 제공한다.
request_id_ctx: ContextVar[str] = ContextVar("request_id", default="-")


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
    rid = request.headers.get("X-Request-ID") or uuid.uuid4().hex[:12]
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
    # 422 응답 시 어떤 필드/값이 거부됐는지 console 로그로 즉시 진단 가능하게 한다.
    # 응답 body 는 fastapi default 와 동일하므로 클라이언트 호환성 영향 없음.
    logger.warning(
        "Request validation failed | path=%s method=%s body=%r errors=%s",
        request.url.path,
        request.method,
        exc.body,
        exc.errors(),
    )
    return JSONResponse(status_code=422, content={"detail": exc.errors()})


@app.exception_handler(Exception)
async def unhandled_exception_handler(request: Request, exc: Exception) -> JSONResponse:
    logger.exception("Unhandled exception")
    if sentry_sdk.is_initialized():
        sentry_sdk.capture_exception(exc)
    return JSONResponse(status_code=500, content={"code": "INTERNAL_ERROR", "message": "서버 내부 오류"})


app.include_router(health.router)
app.include_router(auth.router)
app.include_router(profile.router)
app.include_router(weekly_plan.router)
app.include_router(badge.router)
app.include_router(account.router)
app.include_router(goal.router)
app.include_router(legal.router)
