from sqlalchemy.ext.asyncio import AsyncSession

from app.exceptions import NotFoundException
from app.repositories.profile_repo import ProfileRepository
from app.schemas.profile import UserProfileRequest, UserProfileResponse


class ProfileService:
    def __init__(self, db: AsyncSession):
        self.repo = ProfileRepository(db)

    async def get_profile(self, user_id: str) -> UserProfileResponse:
        profile = await self.repo.get_by_user_id(user_id)
        if not profile:
            raise NotFoundException("프로필이 존재하지 않습니다")
        return UserProfileResponse.model_validate(profile)

    async def upsert_profile(self, user_id: str, req: UserProfileRequest) -> None:
        await self.repo.upsert(user_id, req.model_dump(exclude_unset=True))
