import datetime

from sqlalchemy import and_, delete, func, select
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

    async def get_previous(self, user_id: str, week_start: datetime.date) -> WeeklyPlan | None:
        """week_start 직전(week_start 미만 중 가장 가까운)의 plan."""
        result = await self.db.execute(
            select(WeeklyPlan)
            .where(and_(WeeklyPlan.user_id == user_id, WeeklyPlan.week_start < week_start))
            .order_by(WeeklyPlan.week_start.desc())
            .limit(1)
        )
        return result.scalar_one_or_none()

    async def get_recent(self, user_id: str, limit: int) -> list[WeeklyPlan]:
        """최근 N개의 plan을 week_start 내림차순으로 반환 (통계용)."""
        result = await self.db.execute(
            select(WeeklyPlan)
            .where(WeeklyPlan.user_id == user_id)
            .order_by(WeeklyPlan.week_start.desc())
            .limit(limit)
        )
        return list(result.scalars().all())

    async def upsert(self, user_id: str, week_start: datetime.date, day_plans: str) -> WeeklyPlan:
        plan = await self.get_by_user_and_week(user_id, week_start)
        if plan:
            plan.day_plans = day_plans
        else:
            plan = WeeklyPlan(user_id=user_id, week_start=week_start, day_plans=day_plans)
            self.db.add(plan)
        # id/created_at을 응답에 노출하려면 flush 필요 — service에서 _to_response로 변환
        await self.db.flush()
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

    async def count_by_user(self, user_id: str) -> int:
        """history envelope의 total_count 필드용 — Android 페이지 인디케이터 입력."""
        result = await self.db.execute(
            select(func.count())
            .select_from(WeeklyPlan)
            .where(WeeklyPlan.user_id == user_id)
        )
        return int(result.scalar_one())

    async def delete_all_by_user(self, user_id: str) -> None:
        await self.db.execute(delete(WeeklyPlan).where(WeeklyPlan.user_id == user_id))
