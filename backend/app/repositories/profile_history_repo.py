from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.user_profile_history import UserProfileHistory


class ProfileHistoryRepository:
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
        result = await self.db.execute(
            select(UserProfileHistory)
            .where(UserProfileHistory.user_id == user_id)
            .order_by(UserProfileHistory.recorded_at.desc())
            .limit(limit)
        )
        return list(result.scalars().all())
