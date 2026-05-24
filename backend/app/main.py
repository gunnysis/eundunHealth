import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

import sentry_sdk
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

from app.config import get_settings
from app.exceptions import AppException
from app.routers import account, badge, health, profile, weekly_plan

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    settings = get_settings()

    # DB 엔진 + 세션팩토리 1회 생성 → app.state에 저장
    engine = create_async_engine(settings.database_url, pool_size=3, max_overflow=0)
    app.state.session_factory = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)

    if settings.sentry_dsn:
        sentry_sdk.init(
            dsn=settings.sentry_dsn,
            traces_sample_rate=1.0 if settings.environment == "development" else 0.2,
            environment=settings.environment,
        )
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")

    yield

    # 종료 시 엔진 정리
    await engine.dispose()


app = FastAPI(title="eundunHealth API", lifespan=lifespan)

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


# 글로벌 에러 핸들러
@app.exception_handler(AppException)
async def app_exception_handler(request: Request, exc: AppException) -> JSONResponse:
    return JSONResponse(
        status_code=exc.status_code,
        content={"code": exc.code, "message": exc.message},
    )


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
