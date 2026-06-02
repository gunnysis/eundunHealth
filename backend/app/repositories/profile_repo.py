from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.user_profile import UserProfile


class ProfileRepository:
    """사용자 프로필(UserProfile) 의 DB 접근. user_id PK, 최대 1행."""

    def __init__(self, db: AsyncSession):
        self.db = db

    async def get_by_user_id(self, user_id: str) -> UserProfile | None:
        """user_id 로 프로필 1건 조회. 미존재 시 None 반환."""
        result = await self.db.execute(
            select(UserProfile).where(UserProfile.user_id == user_id)
        )
        return result.scalar_one_or_none()

    async def upsert(self, user_id: str, data: dict[str, object]) -> UserProfile:
        """프로필 upsert. 기존 레코드가 있으면 필드 갱신, 없으면 신규 삽입."""
        profile = await self.get_by_user_id(user_id)
        if profile:
            for key, value in data.items():
                setattr(profile, key, value)
        else:
            profile = UserProfile(user_id=user_id, **data)
            self.db.add(profile)
        return profile

    async def delete_by_user_id(self, user_id: str) -> None:
        """회원 탈퇴 시 사용자의 프로필 행 삭제."""
        profile = await self.get_by_user_id(user_id)
        if profile:
            await self.db.delete(profile)
