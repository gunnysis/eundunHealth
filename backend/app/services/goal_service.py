from sqlalchemy.ext.asyncio import AsyncSession

from app.repositories.goal_repo import GoalRepository
from app.schemas.goal import GoalRequest, GoalResponse


class GoalService:
    def __init__(self, db: AsyncSession):
        self.repo = GoalRepository(db)

    async def list_goals(self, user_id: str) -> list[GoalResponse]:
        goals = await self.repo.get_all(user_id)
        return [
            GoalResponse(
                goal_type=g.goal_type,
                target_value=g.target_value,
                created_at=str(g.created_at),
            )
            for g in goals
        ]

    async def upsert_goal(self, user_id: str, req: GoalRequest) -> GoalResponse:
        goal = await self.repo.upsert(user_id, req.goal_type, req.target_value)
        # repo.upsert이 새 row면 created_at이 server_default라 아직 None일 수 있다.
        # commit 후 refresh가 보장돼야 하지만 응답 형식상 현재 시각으로 안전 fallback.
        from datetime import datetime
        created_at = goal.created_at if goal.created_at is not None else datetime.utcnow()
        return GoalResponse(
            goal_type=goal.goal_type,
            target_value=goal.target_value,
            created_at=str(created_at),
        )
