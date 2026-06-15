import datetime

from sqlalchemy import and_, delete, func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.weekly_plan import WeeklyPlan


class WeeklyPlanRepository:
    """주간 운동 plan 의 DB 접근. user_id + week_start 복합 키."""

    def __init__(self, db: AsyncSession):
        self.db = db

    async def get_by_user_and_week(
        self, user_id: str, week_start: datetime.date, for_update: bool = False
    ) -> WeeklyPlan | None:
        """UserId + weekStart 로 plan 1건 조회. v0.1 INC: userId 필터링 누락 시 다른 사용자 데이터 노출 위험.

        for_update=True 면 행 잠금(SELECT ... FOR UPDATE)으로 동시 read-modify-write 를 직렬화한다
        (완료 토글 PATCH 의 lost-update 방지). SQLite 는 무시하므로 테스트에 무해.
        """
        stmt = select(WeeklyPlan).where(and_(WeeklyPlan.user_id == user_id, WeeklyPlan.week_start == week_start))
        if for_update:
            stmt = stmt.with_for_update()
        result = await self.db.execute(stmt)
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
            select(WeeklyPlan).where(WeeklyPlan.user_id == user_id).order_by(WeeklyPlan.week_start.desc()).limit(limit)
        )
        return list(result.scalars().all())

    async def upsert(self, user_id: str, week_start: datetime.date, day_plans: str) -> WeeklyPlan:
        """주간 plan upsert 후 flush. id/created_at 노출을 위해 flush 필수 — service 의 _to_response 전제."""
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
        """페이지네이션 기반 plan 이력 조회. GET /weekly-plan/history 엔드포인트 전용."""
        result = await self.db.execute(
            select(WeeklyPlan)
            .where(WeeklyPlan.user_id == user_id)
            .order_by(WeeklyPlan.week_start.desc())
            .offset(page * size)
            .limit(size)
        )
        return list(result.scalars().all())

    async def count_by_user(self, user_id: str) -> int:
        """History envelope 의 total_count 필드용 — Android 페이지 인디케이터 입력."""
        result = await self.db.execute(
            select(func.count()).select_from(WeeklyPlan).where(WeeklyPlan.user_id == user_id)
        )
        return int(result.scalar_one())

    async def delete_all_by_user(self, user_id: str) -> None:
        """회원 탈퇴 시 사용자의 전체 주간 plan 삭제."""
        await self.db.execute(delete(WeeklyPlan).where(WeeklyPlan.user_id == user_id))
