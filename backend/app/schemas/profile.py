from typing import Annotated

from pydantic import Field, field_validator

from app.schemas.base import CamelSchema
from app.schemas.weekly_plan import WorkoutPlanMode


class UserProfileRequest(CamelSchema):
    height_cm: Annotated[float, Field(ge=50, le=300)]
    weight_kg: Annotated[float, Field(ge=10, le=500)]
    gender: Annotated[str, Field(pattern="^(male|female|unspecified)$")] = "unspecified"
    body_fat_pct: Annotated[float | None, Field(ge=1, le=70)] = None
    muscle_mass_kg: Annotated[float | None, Field(ge=1, le=200)] = None
    rest_days: list[int] = Field(default_factory=lambda: [7], min_length=1, max_length=6)

    @field_validator("rest_days")
    @classmethod
    def validate_rest_days(cls, v: list[int]) -> list[int]:
        """휴식일 배열의 요일 범위(1~7) 및 중복 제거 후 개수(1~6개)를 검증한다."""
        if any(not isinstance(d, int) or isinstance(d, bool) or d < 1 or d > 7 for d in v):
            raise ValueError("요일 번호는 1(월)부터 7(일) 사이의 정수여야 합니다.")
        unique_days = sorted(set(v))
        if not (1 <= len(unique_days) <= 6):
            raise ValueError("휴식일은 최소 1일에서 최대 6일 사이로 지정해야 합니다.")
        return unique_days


class UserProfileResponse(CamelSchema):
    user_id: str
    height_cm: float
    weight_kg: float
    gender: str
    body_fat_pct: float | None
    muscle_mass_kg: float | None
    rest_days: list[int] = Field(default_factory=lambda: [7])
    default_workout_plan_mode: WorkoutPlanMode | None = None


class DefaultWorkoutPlanModeRequest(CamelSchema):
    default_workout_plan_mode: WorkoutPlanMode | None
