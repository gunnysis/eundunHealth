from __future__ import annotations

from fastapi import APIRouter, Depends, Query, Response, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.dependencies import get_current_user_id
from app.schemas.smile_training import (
    SmileTrainingDeleteResponse,
    SmileTrainingSessionRequest,
    SmileTrainingSessionResponse,
    SmileTrainingSessionsPageResponse,
    SmileTrainingStatisticsResponse,
)
from app.services.smile_training_service import SmileTrainingService

router = APIRouter(prefix="/smile-training", tags=["smile-training"])


@router.post(
    "/sessions",
    response_model=SmileTrainingSessionResponse,
    operation_id="recordSmileTrainingSession",
)
async def record_session(
    req: SmileTrainingSessionRequest,
    response: Response,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> SmileTrainingSessionResponse:
    """스마일 트레이닝 세션을 멱등 기록한다. 신규는 201, 동일 payload 재전송은 200을 반환한다."""
    result, created = await SmileTrainingService(db).record_session(user_id, req)
    response.status_code = status.HTTP_201_CREATED if created else status.HTTP_200_OK
    return result


@router.get(
    "/sessions",
    response_model=SmileTrainingSessionsPageResponse,
    operation_id="listSmileTrainingSessions",
)
async def list_sessions(
    cursor: str | None = Query(default=None),
    limit: int = Query(default=20, ge=1, le=50),
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> SmileTrainingSessionsPageResponse:
    """인증 사용자의 스마일 트레이닝 세션 이력을 최신순(occurred_at DESC, id DESC) 커서 기반으로 조회한다."""
    return await SmileTrainingService(db).list_sessions(user_id=user_id, cursor=cursor, limit=limit)


@router.get(
    "/statistics",
    response_model=SmileTrainingStatisticsResponse,
    operation_id="getSmileTrainingStatistics",
)
async def get_statistics(
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> SmileTrainingStatisticsResponse:
    """인증 사용자의 KST 기준 스마일 트레이닝 연속일·당일 완료 여부·누적 통계를 반환한다."""
    return await SmileTrainingService(db).get_statistics(user_id)


@router.delete(
    "/sessions",
    response_model=SmileTrainingDeleteResponse,
    operation_id="deleteAllSmileTrainingSessions",
)
async def delete_all_sessions(
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> SmileTrainingDeleteResponse:
    """인증 사용자의 모든 스마일 트레이닝 세션을 삭제한다."""
    deleted = await SmileTrainingService(db).delete_all_by_user(user_id)
    return SmileTrainingDeleteResponse(status="deleted", deleted_count=deleted)
