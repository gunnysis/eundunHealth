import datetime

from fastapi import APIRouter, Depends, Query
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.dependencies import get_current_user_id
from app.schemas.statistics import StatisticsResponse
from app.schemas.weekly_plan import CompletionRequest, WeeklyPlanRequest, WeeklyPlanResponse
from app.services.statistics_service import StatisticsService
from app.services.weekly_plan_service import WeeklyPlanService

router = APIRouter(tags=["weekly-plan"])


@router.get("/weekly-plan", response_model=WeeklyPlanResponse)
async def get_plan(
    week_start: str = Query(default_factory=lambda: str(datetime.date.today())),
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> WeeklyPlanResponse:
    return await WeeklyPlanService(db).get_plan(user_id, week_start)


@router.post("/weekly-plan")
async def create_plan(
    req: WeeklyPlanRequest,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> dict[str, str]:
    await WeeklyPlanService(db).upsert_plan(user_id, req)
    return {"status": "ok"}


@router.patch("/weekly-plan/complete")
async def complete(
    req: CompletionRequest,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> dict[str, str]:
    await WeeklyPlanService(db).update_completion(user_id, req)
    return {"status": "ok"}


@router.get("/weekly-plan/history", response_model=list[WeeklyPlanResponse])
async def history(
    page: int = Query(0, ge=0),
    size: int = Query(10, ge=1, le=50),
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> list[WeeklyPlanResponse]:
    return await WeeklyPlanService(db).get_history(user_id, page, size)


@router.get("/weekly-plan/previous", response_model=WeeklyPlanResponse | None)
async def previous(
    week_start: str = Query(default_factory=lambda: str(datetime.date.today())),
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> WeeklyPlanResponse | None:
    """기준 주 직전의 plan. 없으면 null — Android가 첫 사용자/공백 주를 구분하도록."""
    return await WeeklyPlanService(db).get_previous_plan(user_id, week_start)


@router.get("/weekly-plan/statistics", response_model=StatisticsResponse)
async def statistics(
    weeks: int = Query(12, ge=1, le=52),
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> StatisticsResponse:
    return await StatisticsService(db).get_statistics(user_id, weeks)
