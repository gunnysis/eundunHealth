import logging
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
async def lifespan(app: FastAPI):
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

    # CORS — lifespan 내부에서 settings 참조 (모듈 레벨 get_settings() 호출 방지)
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    yield

    # 종료 시 엔진 정리
    await engine.dispose()


app = FastAPI(title="eundunHealth API", lifespan=lifespan)


# 글로벌 에러 핸들러
@app.exception_handler(AppException)
async def app_exception_handler(request: Request, exc: AppException):
    return JSONResponse(
        status_code=exc.status_code,
        content={"code": exc.code, "message": exc.message},
    )


@app.exception_handler(Exception)
async def unhandled_exception_handler(request: Request, exc: Exception):
    logger.exception("Unhandled exception")
    if sentry_sdk.is_initialized():
        sentry_sdk.capture_exception(exc)
    return JSONResponse(status_code=500, content={"code": "INTERNAL_ERROR", "message": "서버 내부 오류"})


app.include_router(health.router)
app.include_router(profile.router)
app.include_router(weekly_plan.router)
app.include_router(badge.router)
app.include_router(account.router)
