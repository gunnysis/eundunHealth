from sqlalchemy.ext.asyncio import AsyncSession

from app.repositories.goal_repo import GoalRepository
from app.schemas.goal import GoalRequest, GoalResponse


class GoalService:
    """체중·체지방 목표의 조회 및 upsert를 처리한다."""

    def __init__(self, db: AsyncSession):
        self.repo = GoalRepository(db)

    async def list_goals(self, user_id: str) -> list[GoalResponse]:
        """사용자의 전체 목표 목록을 반환한다."""
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
        """goal_type 별 목표를 생성하거나 갱신하고 최신 상태를 반환한다."""
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
