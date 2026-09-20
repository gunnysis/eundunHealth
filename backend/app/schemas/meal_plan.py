from pydantic import Field

from app.schemas.base import CamelSchema


class MealInfo(CamelSchema):
    meal_type: str = Field(..., description="식사 구분 (예: 점심, 저녁, 운동 후 간식 등)")
    menu_name: str = Field(..., description="식단 메뉴 이름 (한국어, 한식 위주 권장)")
    calories: int = Field(..., description="예상 칼로리 (kcal)")
    protein_g: int = Field(..., description="단백질 함량 (g)")
    carbs_g: int = Field(..., description="탄수화물 함량 (g)")
    fat_g: int = Field(..., description="지방 함량 (g)")


class DailyMealPlan(CamelSchema):
    day: str = Field(..., description="요일 (예: 월요일)")
    is_rest_day: bool = Field(..., description="휴식일 여부 (휴식일인 경우 가벼운 식단 구성)")
    meals: list[MealInfo] = Field(..., description="해당 일자의 식사 목록 (보통 2끼 구성)")


class WeeklyMealPlanResponse(CamelSchema):
    weekly_plan: list[DailyMealPlan] = Field(..., description="월요일부터 토요일까지의 주간 식단 리스트")
    summary: str = Field(..., description="이번 주 식단 구성의 핵심 요약 및 조언 (1~2문장)")
