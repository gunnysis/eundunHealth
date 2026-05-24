import datetime

from sqlalchemy import and_, delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.weekly_plan import WeeklyPlan


class WeeklyPlanRepository:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def get_by_user_and_week(self, user_id: str, week_start: datetime.date) -> WeeklyPlan | None:
        result = await self.db.execute(
            select(WeeklyPlan).where(
                and_(WeeklyPlan.user_id == user_id, WeeklyPlan.week_start == week_start)
            )
        )
        return result.scalar_one_or_none()

    async def upsert(self, user_id: str, week_start: datetime.date, day_plans: str) -> WeeklyPlan:
        plan = await self.get_by_user_and_week(user_id, week_start)
        if plan:
            plan.day_plans = day_plans
        else:
            plan = WeeklyPlan(user_id=user_id, week_start=week_start, day_plans=day_plans)
            self.db.add(plan)
        return plan

    async def get_history(self, user_id: str, page: int, size: int) -> list[WeeklyPlan]:
        result = await self.db.execute(
            select(WeeklyPlan)
            .where(WeeklyPlan.user_id == user_id)
            .order_by(WeeklyPlan.week_start.desc())
            .offset(page * size)
            .limit(size)
        )
        return list(result.scalars().all())

    async def delete_all_by_user(self, user_id: str) -> None:
        await self.db.execute(delete(WeeklyPlan).where(WeeklyPlan.user_id == user_id))
