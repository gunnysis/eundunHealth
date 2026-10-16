from typing import Annotated

from pydantic import Field, model_validator

from app.schemas.base import CamelSchema


class MealInfo(CamelSchema):
    meal_type: Annotated[str, Field(min_length=1, max_length=50, description="식사 구분")]
    menu_name: Annotated[str, Field(min_length=1, max_length=200, description="식단 메뉴 이름")]
    calories: Annotated[int, Field(ge=0, le=5000, description="예상 칼로리 (kcal)")]
    protein_g: Annotated[int, Field(ge=0, le=1000, description="단백질 함량 (g)")]
    carbs_g: Annotated[int, Field(ge=0, le=1000, description="탄수화물 함량 (g)")]
    fat_g: Annotated[int, Field(ge=0, le=1000, description="지방 함량 (g)")]


class DailyMealPlan(CamelSchema):
    day: Annotated[str, Field(min_length=1, max_length=20, description="요일 (예: 월요일)")]
    is_rest_day: bool = Field(..., description="휴식일 여부 (휴식일인 경우 가벼운 식단 구성)")
    meals: Annotated[list[MealInfo], Field(min_length=1, max_length=6, description="해당 일자의 식사 목록")]


class WeeklyMealPlanResponse(CamelSchema):
    weekly_plan: Annotated[list[DailyMealPlan], Field(min_length=7, max_length=7, description="7일 주간 식단")]
    summary: Annotated[str, Field(min_length=1, max_length=1000, description="참고용 요약 및 주의사항")]

    @model_validator(mode="after")
    def validate_unique_days(self) -> WeeklyMealPlanResponse:
        """Reject duplicate day labels before AI output reaches the database."""
        days = [day.day for day in self.weekly_plan]
        if len(set(days)) != len(days):
            raise ValueError("weekly_plan day values must be unique")
        return self
