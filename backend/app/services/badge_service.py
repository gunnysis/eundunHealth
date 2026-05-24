from sqlalchemy.ext.asyncio import AsyncSession

from app.exceptions import BadRequestException, ConflictException
from app.repositories.badge_repo import VALID_BADGE_KEYS, BadgeRepository
from app.schemas.badge import BadgeResponse


class BadgeService:
    def __init__(self, db: AsyncSession):
        self.repo = BadgeRepository(db)

    async def get_badges(self, user_id: str) -> list[BadgeResponse]:
        badges = await self.repo.get_all_by_user(user_id)
        return [BadgeResponse(badge_key=b.badge_key, earned_at=str(b.earned_at)) for b in badges]

    async def award_badge(self, user_id: str, badge_key: str) -> None:
        if badge_key not in VALID_BADGE_KEYS:
            raise BadRequestException(f"유효하지 않은 배지: {badge_key}")
        if await self.repo.exists(user_id, badge_key):
            raise ConflictException(f"이미 획득한 배지: {badge_key}")
        await self.repo.award(user_id, badge_key)
