from sqlalchemy import and_, delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.goal import Goal


class GoalRepository:
    """목표(Goal) 레코드의 DB 접근. user_id + goal_type 복합 유니크 (v0.3 신규)."""

    def __init__(self, db: AsyncSession):
        self.db = db

    async def get_all(self, user_id: str) -> list[Goal]:
        """사용자의 전체 목표 목록을 goal_type 오름차순으로 반환."""
        result = await self.db.execute(select(Goal).where(Goal.user_id == user_id).order_by(Goal.goal_type))
        return list(result.scalars().all())

    async def get_by_type(self, user_id: str, goal_type: str) -> Goal | None:
        """goal_type(weight / body_fat) 으로 목표 1건 조회."""
        result = await self.db.execute(select(Goal).where(and_(Goal.user_id == user_id, Goal.goal_type == goal_type)))
        return result.scalar_one_or_none()

    async def upsert(self, user_id: str, goal_type: str, target_value: float) -> Goal:
        """목표 upsert. 기존 레코드가 있으면 target_value 만 갱신, 없으면 신규 삽입 후 flush+refresh."""
        existing = await self.get_by_type(user_id, goal_type)
        if existing:
            existing.target_value = target_value
            return existing
        goal = Goal(user_id=user_id, goal_type=goal_type, target_value=target_value)
        self.db.add(goal)
        # created_at은 server_default라 flush 후에야 채워짐 — 응답 노출 위해 refresh 필수
        await self.db.flush()
        await self.db.refresh(goal)
        return goal

    async def delete_all_by_user(self, user_id: str) -> None:
        """회원 탈퇴 시 사용자의 전체 목표 삭제."""
        await self.db.execute(delete(Goal).where(Goal.user_id == user_id))
