from fastapi import APIRouter, Depends, Query
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.dependencies import get_current_user_id
from app.schemas.goal import ProfileHistoryEntry
from app.schemas.profile import UserProfileRequest, UserProfileResponse
from app.services.profile_service import ProfileService

router = APIRouter(tags=["profile"])


@router.get("/profile", response_model=UserProfileResponse, operation_id="getProfile")
async def get_profile(
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> UserProfileResponse:
    return await ProfileService(db).get_profile(user_id)


@router.put("/profile", operation_id="updateProfile")
async def upsert_profile(
    req: UserProfileRequest,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> dict[str, str]:
    await ProfileService(db).upsert_profile(user_id, req)
    return {"status": "ok"}


@router.get("/profile/history", response_model=list[ProfileHistoryEntry], operation_id="getProfileHistory")
async def get_profile_history(
    limit: int = Query(50, ge=1, le=200),
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> list[ProfileHistoryEntry]:
    """프로필 변경 이력 — 시간 오름차순 (차트 친화)."""
    return await ProfileService(db).get_history(user_id, limit)
