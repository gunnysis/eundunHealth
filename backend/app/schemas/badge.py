from app.schemas.base import CamelSchema


class BadgeResponse(CamelSchema):
    badge_key: str
    earned_at: str
