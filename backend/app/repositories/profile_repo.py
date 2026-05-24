from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.user_profile import UserProfile


class ProfileRepository:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def get_by_user_id(self, user_id: str) -> UserProfile | None:
        result = await self.db.execute(
            select(UserProfile).where(UserProfile.user_id == user_id)
        )
        return result.scalar_one_or_none()

    async def upsert(self, user_id: str, data: dict[str, object]) -> UserProfile:
        profile = await self.get_by_user_id(user_id)
        if profile:
            for key, value in data.items():
                setattr(profile, key, value)
        else:
            profile = UserProfile(user_id=user_id, **data)
            self.db.add(profile)
        return profile

    async def delete_by_user_id(self, user_id: str) -> None:
        profile = await self.get_by_user_id(user_id)
        if profile:
            await self.db.delete(profile)
