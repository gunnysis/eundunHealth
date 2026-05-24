from typing import Annotated, Literal

from pydantic import Field

from app.schemas.base import CamelSchema


class GoalRequest(CamelSchema):
    """PUT /goals — goal_type별 1개. 동일 type 재등록 시 target_value 갱신."""

    goal_type: Literal["weight", "body_fat"]
    target_value: Annotated[float, Field(gt=0, le=500)]


class GoalResponse(CamelSchema):
    goal_type: str
    target_value: float
    created_at: str


class ProfileHistoryEntry(CamelSchema):
    height_cm: float
    weight_kg: float
    body_fat_pct: float | None
    muscle_mass_kg: float | None
    recorded_at: str
