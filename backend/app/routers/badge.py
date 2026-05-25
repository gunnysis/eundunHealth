from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.dependencies import get_current_user_id
from app.schemas.badge import BadgeResponse
from app.services.badge_service import BadgeService

router = APIRouter(tags=["badges"])


@router.get("/badges", response_model=list[BadgeResponse], operation_id="getBadges")
async def get_badges(
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> list[BadgeResponse]:
    return await BadgeService(db).get_badges(user_id)


@router.post("/badges/{key}", response_model=BadgeResponse, operation_id="awardBadge")
async def award_badge(
    key: str,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> BadgeResponse:
    return await BadgeService(db).award_badge(user_id, key)
