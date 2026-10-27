import datetime
from typing import Any

from fastapi import APIRouter, Depends, Query, Response, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.compatibility import require_supported_android_client
from app.database import get_db
from app.dependencies import get_current_user_id
from app.schemas.statistics import StatisticsResponse
from app.schemas.weekly_plan import (
    ApiErrorResponse,
    ClientUpdateRequiredErrorResponse,
    CompletionRequest,
    WeeklyPlanExistsErrorResponse,
    WeeklyPlanHistoryResponse,
    WeeklyPlanResponse,
    WeeklyPlanV2Request,
)
from app.services.statistics_service import StatisticsService
from app.services.weekly_plan_service import WeeklyPlanService

router = APIRouter(prefix="/v2/weekly-plan", tags=["weekly-plan-v2"])

ERROR_RESPONSES: dict[int | str, dict[str, Any]] = {
    403: {"model": ClientUpdateRequiredErrorResponse, "description": "지원 종료된 Android 클라이언트"},
    409: {"model": WeeklyPlanExistsErrorResponse, "description": "동일 주 계획이 이미 존재함"},
}
WRITE_ERROR_RESPONSES: dict[int | str, dict[str, Any]] = {
    403: ERROR_RESPONSES[403],
}
DELETE_ERROR_RESPONSES: dict[int | str, dict[str, Any]] = {
    **WRITE_ERROR_RESPONSES,
    404: {"model": ApiErrorResponse, "description": "해당 주 계획이 없음"},
}


@router.get("", response_model=WeeklyPlanResponse, operation_id="getWeeklyPlanV2")
async def get_plan(
    week_start: str = Query(default_factory=lambda: str(datetime.date.today()), alias="weekStart"),
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> WeeklyPlanResponse:
    """지정한 주(week_start)의 운동 계획을 반환한다. 없으면 404."""
    return await WeeklyPlanService(db).get_plan(user_id, week_start)


@router.post(
    "",
    response_model=WeeklyPlanResponse,
    status_code=status.HTTP_201_CREATED,
    responses=ERROR_RESPONSES,
    operation_id="createWeeklyPlanV2",
)
async def create_plan(
    req: WeeklyPlanV2Request,
    user_id: str = Depends(get_current_user_id),
    _: None = Depends(require_supported_android_client),
    db: AsyncSession = Depends(get_db),
) -> WeeklyPlanResponse:
    """새 주간 운동 계획을 생성한다. 동일 주 계획은 변경하지 않고 409를 반환한다."""
    return await WeeklyPlanService(db).create_plan(user_id, req)


@router.delete(
    "",
    status_code=status.HTTP_204_NO_CONTENT,
    responses=DELETE_ERROR_RESPONSES,
    operation_id="deleteWeeklyPlanV2",
)
async def delete_plan(
    week_start: str = Query(alias="weekStart"),
    user_id: str = Depends(get_current_user_id),
    _: None = Depends(require_supported_android_client),
    db: AsyncSession = Depends(get_db),
) -> Response:
    """손상 계획 복구를 사용자가 명시한 경우 해당 주 계획을 삭제한다."""
    await WeeklyPlanService(db).delete_plan(user_id, week_start)
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.patch("/complete", responses=WRITE_ERROR_RESPONSES, operation_id="updateDayCompletionV2")
async def complete(
    req: CompletionRequest,
    user_id: str = Depends(get_current_user_id),
    _: None = Depends(require_supported_android_client),
    db: AsyncSession = Depends(get_db),
) -> dict[str, str]:
    """특정 요일의 운동 완료 여부를 갱신한다. 배지는 클라이언트가 POST /badges/{key}로 직접 부여한다."""
    await WeeklyPlanService(db).update_completion(user_id, req)
    return {"status": "ok"}


@router.get("/history", response_model=WeeklyPlanHistoryResponse, operation_id="getWeeklyPlanHistoryV2")
async def history(
    page: int = Query(0, ge=0),
    size: int = Query(10, ge=1, le=50),
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> WeeklyPlanHistoryResponse:
    """과거 주간 계획 목록을 페이지 단위로 반환한다. 최신 순 정렬."""
    return await WeeklyPlanService(db).get_history(user_id, page, size)


@router.get("/previous", response_model=WeeklyPlanResponse | None, operation_id="getPreviousWeeklyPlanV2")
async def previous(
    week_start: str = Query(default_factory=lambda: str(datetime.date.today()), alias="weekStart"),
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> WeeklyPlanResponse | None:
    """기준 주 직전의 plan. 없으면 null — Android가 첫 사용자/공백 주를 구분하도록."""
    return await WeeklyPlanService(db).get_previous_plan(user_id, week_start)


@router.get("/statistics", response_model=StatisticsResponse, operation_id="getStatisticsV2")
async def statistics(
    weeks: int = Query(12, ge=1, le=52),
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> StatisticsResponse:
    """최근 N주간 완료율과 연속 streak 통계를 반환한다."""
    return await StatisticsService(db).get_statistics(user_id, weeks)
