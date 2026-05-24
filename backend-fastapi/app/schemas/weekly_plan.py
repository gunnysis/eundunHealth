from app.schemas.base import CamelSchema


class WeeklyPlanRequest(CamelSchema):
    week_start: str
    day_plans: str  # JSON string


class WeeklyPlanResponse(CamelSchema):
    week_start: str
    day_plans: str
    created_at: str | None = None


class CompletionRequest(CamelSchema):
    week_start: str
    day_index: int
    exercise_index: int
    completed: bool
