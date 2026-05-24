from sqlalchemy import and_, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.goal import Goal


class GoalRepository:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def get_all(self, user_id: str) -> list[Goal]:
        result = await self.db.execute(
            select(Goal).where(Goal.user_id == user_id).order_by(Goal.goal_type)
        )
        return list(result.scalars().all())

    async def get_by_type(self, user_id: str, goal_type: str) -> Goal | None:
        result = await self.db.execute(
            select(Goal).where(and_(Goal.user_id == user_id, Goal.goal_type == goal_type))
        )
        return result.scalar_one_or_none()

    async def upsert(self, user_id: str, goal_type: str, target_value: float) -> Goal:
        existing = await self.get_by_type(user_id, goal_type)
        if existing:
            existing.target_value = target_value
            return existing
        goal = Goal(user_id=user_id, goal_type=goal_type, target_value=target_value)
        self.db.add(goal)
        return goal
