import datetime
import uuid

from sqlalchemy import and_, delete, func, select
from sqlalchemy.dialects.postgresql import insert as postgresql_insert
from sqlalchemy.dialects.sqlite import insert as sqlite_insert
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

    async def create(
        self, user_id: str, week_start: datetime.date, plan_mode: str, day_plans: str
    ) -> WeeklyPlan | None:
        """원자적으로 새 계획을 삽입한다. 동일 user/week가 있으면 기존 행을 건드리지 않고 None."""
        values = {
            "id": uuid.uuid4(),
            "user_id": user_id,
            "week_start": week_start,
            "plan_mode": plan_mode,
            "day_plans": day_plans,
        }
        dialect = self.db.get_bind().dialect.name
        insert = sqlite_insert if dialect == "sqlite" else postgresql_insert
        stmt = (
            insert(WeeklyPlan)
            .values(**values)
            .on_conflict_do_nothing(index_elements=["user_id", "week_start"])
            .returning(WeeklyPlan)
        )
        result = await self.db.execute(stmt)
        return result.scalar_one_or_none()

    async def delete_by_user_and_week(self, user_id: str, week_start: datetime.date) -> bool:
        """사용자가 손상 계획 초기화를 명시한 경우에만 해당 주 계획을 삭제한다."""
        result = await self.db.execute(
            delete(WeeklyPlan)
            .where(and_(WeeklyPlan.user_id == user_id, WeeklyPlan.week_start == week_start))
            .returning(WeeklyPlan.id)
        )
        return result.scalar_one_or_none() is not None

    async def get_history(self, user_id: str, page: int, size: int) -> tuple[list[WeeklyPlan], int]:
        """페이지 데이터 + 전체 카운트를 1쿼리로 반환. GET /weekly-plan/history 전용.

        기존엔 페이지 SELECT + 별도 COUNT(*) 로 2-round-trip 이었다. `count(*) over()`
        window 로 같은 쿼리에서 전체 행수를 함께 받아 round-trip 을 절반으로 줄인다.
        데이터 끝 너머의 빈 페이지는 window 행이 없어 total 을 못 읽으므로 그때만 count 폴백.
        """
        result = await self.db.execute(
            select(WeeklyPlan, func.count().over().label("total"))
            .where(WeeklyPlan.user_id == user_id)
            .order_by(WeeklyPlan.week_start.desc())
            .offset(page * size)
            .limit(size)
        )
        rows = result.all()
        if not rows:
            return [], await self.count_by_user(user_id)
        plans = [row[0] for row in rows]
        total = int(rows[0][1])
        return plans, total

    async def count_by_user(self, user_id: str) -> int:
        """History envelope 의 total_count 필드용 — Android 페이지 인디케이터 입력."""
        result = await self.db.execute(
            select(func.count()).select_from(WeeklyPlan).where(WeeklyPlan.user_id == user_id)
        )
        return int(result.scalar_one())

    async def delete_all_by_user(self, user_id: str) -> None:
        """회원 탈퇴 시 사용자의 전체 주간 plan 삭제."""
        await self.db.execute(delete(WeeklyPlan).where(WeeklyPlan.user_id == user_id))
