import datetime

from fastapi import APIRouter, Depends, Query
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.dependencies import get_current_user_id
from app.schemas.weekly_plan import CompletionRequest, WeeklyPlanRequest, WeeklyPlanResponse
from app.services.weekly_plan_service import WeeklyPlanService

router = APIRouter(tags=["weekly-plan"])


@router.get("/weekly-plan", response_model=WeeklyPlanResponse)
async def get_plan(
    week_start: str = Query(default_factory=lambda: str(datetime.date.today())),
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
):
    return await WeeklyPlanService(db).get_plan(user_id, week_start)


@router.post("/weekly-plan")
async def create_plan(
    req: WeeklyPlanRequest,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
):
    await WeeklyPlanService(db).upsert_plan(user_id, req)
    return {"status": "ok"}


@router.patch("/weekly-plan/complete")
async def complete(
    req: CompletionRequest,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
):
    await WeeklyPlanService(db).update_completion(user_id, req)
    return {"status": "ok"}


@router.get("/weekly-plan/history", response_model=list[WeeklyPlanResponse])
async def history(
    page: int = Query(0, ge=0),
    size: int = Query(10, ge=1, le=50),
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
):
    return await WeeklyPlanService(db).get_history(user_id, page, size)
