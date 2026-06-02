from sqlalchemy import and_, delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.badge import Badge

VALID_BADGE_KEYS = {
    # v0.1 — 주간 완료 + 스트릭
    "week_1_complete",
    "week_2_complete",
    "streak_3weeks",
    # v0.3 §N — 마일스톤 (워크아웃 누적 + 장기 스트릭)
    "first_workout",
    "workouts_10",
    "workouts_50",
    "streak_8weeks",
    # v0.3 §N — 목표 달성
    "goal_weight_achieved",
    "goal_body_fat_achieved",
}


class BadgeRepository:
    """배지(Badge) 레코드의 DB 접근. user_id + badge_key 복합 유니크."""

    def __init__(self, db: AsyncSession):
        self.db = db

    async def get_all_by_user(self, user_id: str) -> list[Badge]:
        """사용자의 전체 획득 배지 목록 반환."""
        result = await self.db.execute(select(Badge).where(Badge.user_id == user_id))
        return list(result.scalars().all())

    async def award(self, user_id: str, badge_key: str) -> Badge:
        """배지 수여 및 flush. earned_at 은 server_default 이므로 refresh 필수."""
        badge = Badge(user_id=user_id, badge_key=badge_key)
        self.db.add(badge)
        # earned_at은 server_default라 flush 후에야 채워짐 — Android awardBadge 응답에 노출하려면 필요
        await self.db.flush()
        await self.db.refresh(badge)
        return badge

    async def exists(self, user_id: str, badge_key: str) -> bool:
        """해당 badge_key 가 이미 수여됐는지 확인. 중복 수여 방지 가드."""
        result = await self.db.execute(
            select(Badge).where(and_(Badge.user_id == user_id, Badge.badge_key == badge_key))
        )
        return result.scalar_one_or_none() is not None

    async def delete_all_by_user(self, user_id: str) -> None:
        """회원 탈퇴 시 사용자의 전체 배지 삭제."""
        await self.db.execute(delete(Badge).where(Badge.user_id == user_id))
