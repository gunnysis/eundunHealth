from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.user_profile_history import UserProfileHistory


class ProfileHistoryRepository:
    """신체 지표 변경 이력(UserProfileHistory) 의 DB 접근. PUT /profile 호출마다 스냅샷 적재."""

    def __init__(self, db: AsyncSession):
        self.db = db

    async def record(
        self,
        user_id: str,
        height_cm: float,
        weight_kg: float,
        body_fat_pct: float | None,
        muscle_mass_kg: float | None,
    ) -> UserProfileHistory:
        """프로필 업데이트 시점의 신체 지표를 이력 테이블에 스냅샷으로 기록."""
        entry = UserProfileHistory(
            user_id=user_id,
            height_cm=height_cm,
            weight_kg=weight_kg,
            body_fat_pct=body_fat_pct,
            muscle_mass_kg=muscle_mass_kg,
        )
        self.db.add(entry)
        return entry

    async def list_recent(self, user_id: str, limit: int = 50) -> list[UserProfileHistory]:
        """최근 N건의 이력을 recorded_at 내림차순으로 반환. GET /profile/history 엔드포인트 전용."""
        result = await self.db.execute(
            select(UserProfileHistory)
            .where(UserProfileHistory.user_id == user_id)
            .order_by(UserProfileHistory.recorded_at.desc())
            .limit(limit)
        )
        return list(result.scalars().all())
