from fastapi import APIRouter

router = APIRouter(tags=["health"])


@router.get("/health", operation_id="healthCheck")
async def health() -> dict[str, str]:
    """서버 가동 상태를 확인한다. JWT 불필요 — probe 전용."""
    return {"status": "ok"}
