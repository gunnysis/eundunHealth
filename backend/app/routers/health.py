import logging

from fastapi import APIRouter, Depends, Request
from fastapi.responses import JSONResponse
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

logger = logging.getLogger(__name__)
router = APIRouter(tags=["health"])


def get_session_factory(request: Request) -> async_sessionmaker[AsyncSession]:
    """app.state 의 session factory 주입. 테스트에서 dependency_overrides 로 교체 가능.

    `request.app.state.session_factory` 직접 참조는 pytest ASGITransport(lifespan 우회)에서
    unset 이라, dependency 로 감싸 테스트 가능성을 확보한다.
    """
    # app.state 는 Any → 명시 타입 로컬로 받아 no-any-return(mypy strict) 회피
    factory: async_sessionmaker[AsyncSession] = request.app.state.session_factory
    return factory


@router.get("/health", operation_id="healthCheck")
async def health() -> dict[str, str]:
    """프로세스 가동 상태. JWT 불필요 — startup/liveness probe 전용 (DB 비검사)."""
    return {"status": "ok"}


@router.get("/health/ready", operation_id="healthReady")
async def health_ready(
    session_factory: async_sessionmaker[AsyncSession] = Depends(get_session_factory),
) -> JSONResponse:
    """Readiness probe — DB 연결 가능 시에만 200, 아니면 503 → 트래픽 차단.

    5초 주기 probe 라 글로벌 500 핸들러(Sentry 포착)를 안 타도록 여기서 직접 503.
    두 분기 모두 JSONResponse — union 반환의 OpenAPI/mypy 잡음 회피.
    """
    try:
        async with session_factory() as session:
            await session.execute(text("SELECT 1"))
    except Exception as e:  # noqa: BLE001 — probe 는 모든 DB 오류를 503 으로 환원
        logger.warning("readiness check failed: %r", e)
        return JSONResponse(status_code=503, content={"status": "not ready"})
    return JSONResponse(content={"status": "ready"})
