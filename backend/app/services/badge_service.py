from sqlalchemy.ext.asyncio import AsyncSession

from app.exceptions import BadRequestException, ConflictException
from app.models.badge import Badge
from app.repositories.badge_repo import VALID_BADGE_KEYS, BadgeRepository
from app.schemas.badge import BadgeResponse


def _to_response(badge: Badge) -> BadgeResponse:
    return BadgeResponse(badge_key=badge.badge_key, earned_at=str(badge.earned_at))


class BadgeService:
    """배지 조회 및 신규 수여를 담당한다. 중복 수여는 ConflictException으로 차단한다."""

    def __init__(self, db: AsyncSession):
        self.repo = BadgeRepository(db)

    async def get_badges(self, user_id: str) -> list[BadgeResponse]:
        """사용자가 보유한 모든 배지를 획득 일시와 함께 반환한다."""
        badges = await self.repo.get_all_by_user(user_id)
        return [_to_response(b) for b in badges]

    async def award_badge(self, user_id: str, badge_key: str) -> BadgeResponse:
        """획득한 배지를 그대로 반환 — Android CheckAndAwardBadgesUseCase가 earnedAt을 받아 UI에 표시."""
        if badge_key not in VALID_BADGE_KEYS:
            raise BadRequestException(f"유효하지 않은 배지: {badge_key}")
        if await self.repo.exists(user_id, badge_key):
            raise ConflictException(f"이미 획득한 배지: {badge_key}")
        badge = await self.repo.award(user_id, badge_key)
        return _to_response(badge)
