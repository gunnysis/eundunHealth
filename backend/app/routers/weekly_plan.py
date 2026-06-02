import datetime

from fastapi import APIRouter, Depends, Query
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.dependencies import get_current_user_id
from app.schemas.statistics import StatisticsResponse
from app.schemas.weekly_plan import (
    CompletionRequest,
    WeeklyPlanHistoryResponse,
    WeeklyPlanRequest,
    WeeklyPlanResponse,
)
from app.services.statistics_service import StatisticsService
from app.services.weekly_plan_service import WeeklyPlanService

router = APIRouter(tags=["weekly-plan"])


@router.get("/weekly-plan", response_model=WeeklyPlanResponse, operation_id="getWeeklyPlan")
async def get_plan(
    week_start: str = Query(default_factory=lambda: str(datetime.date.today()), alias="weekStart"),
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> WeeklyPlanResponse:
    """지정한 주(week_start)의 운동 계획을 반환한다. 없으면 404."""
    return await WeeklyPlanService(db).get_plan(user_id, week_start)


@router.post("/weekly-plan", response_model=WeeklyPlanResponse, operation_id="createWeeklyPlan")
async def create_plan(
    req: WeeklyPlanRequest,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> WeeklyPlanResponse:
    """주간 운동 계획을 생성하거나 교체한다. ExerciseDB 기반 자동 생성 결과를 저장한다."""
    return await WeeklyPlanService(db).upsert_plan(user_id, req)


@router.patch("/weekly-plan/complete", operation_id="updateDayCompletion")
async def complete(
    req: CompletionRequest,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> dict[str, str]:
    """특정 요일의 운동 완료 여부를 갱신한다. 모든 항목 완료 시 배지를 자동 부여한다."""
    await WeeklyPlanService(db).update_completion(user_id, req)
    return {"status": "ok"}


@router.get("/weekly-plan/history", response_model=WeeklyPlanHistoryResponse, operation_id="getWeeklyPlanHistory")
async def history(
    page: int = Query(0, ge=0),
    size: int = Query(10, ge=1, le=50),
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> WeeklyPlanHistoryResponse:
    """과거 주간 계획 목록을 페이지 단위로 반환한다. 최신 순 정렬."""
    return await WeeklyPlanService(db).get_history(user_id, page, size)


@router.get("/weekly-plan/previous", response_model=WeeklyPlanResponse | None, operation_id="getPreviousWeeklyPlan")
async def previous(
    week_start: str = Query(default_factory=lambda: str(datetime.date.today()), alias="weekStart"),
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> WeeklyPlanResponse | None:
    """기준 주 직전의 plan. 없으면 null — Android가 첫 사용자/공백 주를 구분하도록."""
    return await WeeklyPlanService(db).get_previous_plan(user_id, week_start)


@router.get("/weekly-plan/statistics", response_model=StatisticsResponse, operation_id="getStatistics")
async def statistics(
    weeks: int = Query(12, ge=1, le=52),
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> StatisticsResponse:
    """최근 N주간 완료율과 연속 streak 통계를 반환한다."""
    return await StatisticsService(db).get_statistics(user_id, weeks)
