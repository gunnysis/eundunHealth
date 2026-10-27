from typing import Any

from fastapi import APIRouter, Depends, Query
from sqlalchemy.ext.asyncio import AsyncSession

from app.compatibility import require_supported_android_client
from app.database import get_db
from app.dependencies import get_current_user_id
from app.schemas.goal import ProfileHistoryEntry
from app.schemas.profile import DefaultWorkoutPlanModeRequest, UserProfileRequest, UserProfileResponse
from app.schemas.weekly_plan import ApiErrorResponse, ClientUpdateRequiredErrorResponse
from app.services.profile_service import ProfileService

router = APIRouter(tags=["profile"])

PREFERENCE_ERROR_RESPONSES: dict[int | str, dict[str, Any]] = {
    403: {"model": ClientUpdateRequiredErrorResponse, "description": "지원 종료된 Android 클라이언트"},
    404: {"model": ApiErrorResponse, "description": "프로필이 없음"},
}
PROFILE_WRITE_ERROR_RESPONSES: dict[int | str, dict[str, Any]] = {
    403: PREFERENCE_ERROR_RESPONSES[403],
}


@router.get("/profile", response_model=UserProfileResponse, operation_id="getProfile")
async def get_profile(
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> UserProfileResponse:
    """현재 인증 사용자의 프로필을 반환한다. 없으면 404."""
    return await ProfileService(db).get_profile(user_id)


@router.put(
    "/profile",
    responses=PROFILE_WRITE_ERROR_RESPONSES,
    operation_id="updateProfile",
)
async def upsert_profile(
    req: UserProfileRequest,
    user_id: str = Depends(get_current_user_id),
    _: None = Depends(require_supported_android_client),
    db: AsyncSession = Depends(get_db),
) -> dict[str, str]:
    """프로필을 생성하거나 갱신한다. 변경 이력에 스냅샷을 자동 기록한다."""
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


@router.put(
    "/v2/profile/default-workout-plan-mode",
    response_model=UserProfileResponse,
    responses=PREFERENCE_ERROR_RESPONSES,
    operation_id="updateDefaultWorkoutPlanModeV2",
)
async def update_default_workout_plan_mode(
    req: DefaultWorkoutPlanModeRequest,
    user_id: str = Depends(get_current_user_id),
    _: None = Depends(require_supported_android_client),
    db: AsyncSession = Depends(get_db),
) -> UserProfileResponse:
    """신체 측정 이력을 추가하지 않고 다음 계획의 기본 환경만 변경한다."""
    return await ProfileService(db).update_default_workout_plan_mode(user_id, req)
