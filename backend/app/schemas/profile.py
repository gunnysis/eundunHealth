from typing import Annotated

from pydantic import Field

from app.schemas.base import CamelSchema


class UserProfileRequest(CamelSchema):
    height_cm: Annotated[float, Field(ge=50, le=300)]
    weight_kg: Annotated[float, Field(ge=10, le=500)]
    body_fat_pct: Annotated[float | None, Field(ge=1, le=70)] = None
    muscle_mass_kg: Annotated[float | None, Field(ge=1, le=200)] = None
    rest_day: Annotated[int, Field(ge=1, le=7)] = 7


class UserProfileResponse(CamelSchema):
    user_id: str
    height_cm: float
    weight_kg: float
    body_fat_pct: float | None
    muscle_mass_kg: float | None
    rest_day: int = 7
