from pydantic import Field

from app.schemas.base import CamelSchema


class UserProfileRequest(CamelSchema):
    height_cm: float = Field(ge=50, le=300)
    weight_kg: float = Field(ge=10, le=500)
    body_fat_pct: float | None = Field(None, ge=1, le=70)
    muscle_mass_kg: float | None = Field(None, ge=1, le=200)
    rest_day: int = Field(default=7, ge=1, le=7)


class UserProfileResponse(CamelSchema):
    height_cm: float
    weight_kg: float
    body_fat_pct: float | None
    muscle_mass_kg: float | None
    rest_day: int = 7
