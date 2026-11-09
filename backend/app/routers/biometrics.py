from __future__ import annotations

from fastapi import APIRouter, Depends, Query
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.dependencies import get_current_user_id
from app.schemas.biometrics import (
    BiometricDailyListResponse,
    BiometricDeleteResponse,
    BiometricSyncRequest,
    BiometricSyncResponse,
)
from app.services.biometric_service import BiometricService

router = APIRouter(prefix="/v2/biometrics", tags=["biometrics"])


@router.post(
    "/sync",
    response_model=BiometricSyncResponse,
    operation_id="syncBiometricDailySummaries",
)
async def sync_biometric_summaries(
    req: BiometricSyncRequest,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> BiometricSyncResponse:
    """Wear OS 6 워치에서 수집된 일일 생체·마음 상태 요약을 `(user_id, record_date)` 기준으로 멱등 업서트한다."""
    return await BiometricService(db).sync_daily_summaries(user_id=user_id, req=req)


@router.get(
    "/daily",
    response_model=BiometricDailyListResponse,
    operation_id="getBiometricDailySummaries",
)
async def get_biometric_daily_summaries(
    days: int = Query(default=14, ge=1, le=90),
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> BiometricDailyListResponse:
    """인증 사용자의 최근 `days`일치 워치 일일 생체·마음 상태 요약을 최신순으로 조회한다."""
    return await BiometricService(db).list_daily_summaries(user_id=user_id, days=days)


@router.delete(
    "/daily",
    response_model=BiometricDeleteResponse,
    operation_id="deleteBiometricDailySummaries",
)
async def delete_biometric_daily_summaries(
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> BiometricDeleteResponse:
    """인증 사용자의 모든 워치 일일 생체·마음 상태 요약 기록을 삭제한다 (Play 데이터 안전 단독 삭제 지원)."""
    deleted = await BiometricService(db).delete_all_by_user(user_id=user_id)
    return BiometricDeleteResponse(status="deleted", deleted_count=deleted)
