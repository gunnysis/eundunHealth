from datetime import date

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.models.meal_plan import MealPlan, MealPlanItem
from app.schemas.meal_plan import WeeklyMealPlanResponse


class MealPlanService:
    """Service class for managing user meal plans in the database."""

    def __init__(self, db: AsyncSession):
        self.db = db

    async def get_current_plan(self, user_id: str, week_start_date: date) -> WeeklyMealPlanResponse | None:
        """Fetch the current meal plan for a specific user and week start date."""
        stmt = select(MealPlan).where(
            MealPlan.user_id == user_id,
            MealPlan.week_start_date == week_start_date
        ).options(selectinload(MealPlan.items))
        
        result = await self.db.execute(stmt)
        plan = result.scalar_one_or_none()
        
        if not plan:
            return None
            
        # 맵핑 (MealPlan -> WeeklyMealPlanResponse)
        # items 배열을 요일별(day)로 그룹화하여 DailyMealPlan 리스트를 만듭니다.
        daily_plans = {}
        for item in plan.items:
            if item.day not in daily_plans:
                daily_plans[item.day] = {
                    "day": item.day,
                    "is_rest_day": item.is_rest_day,
                    "meals": []
                }
            daily_plans[item.day]["meals"].append({
                "meal_type": item.meal_type,
                "menu_name": item.menu_name,
                "calories": item.calories,
                "protein_g": item.protein_g,
                "carbs_g": item.carbs_g,
                "fat_g": item.fat_g,
            })
            
        weekly_plan_list = list(daily_plans.values())
        return WeeklyMealPlanResponse(weekly_plan=weekly_plan_list, summary=plan.summary)

    async def save_plan(
        self, user_id: str, week_start_date: date, meal_plan_data: dict | WeeklyMealPlanResponse
    ) -> None:
        """Save a new meal plan into the database, replacing the existing one if any."""
        if isinstance(meal_plan_data, WeeklyMealPlanResponse):
            meal_plan_data = meal_plan_data.model_dump()
            
        # 기존 주간 식단이 있으면 삭제
        stmt = select(MealPlan).where(
            MealPlan.user_id == user_id,
            MealPlan.week_start_date == week_start_date
        )
        result = await self.db.execute(stmt)
        existing_plan = result.scalar_one_or_none()
        
        if existing_plan:
            await self.db.delete(existing_plan)
            
        # 새 식단 저장
        new_plan = MealPlan(
            user_id=user_id,
            week_start_date=week_start_date,
            summary=meal_plan_data.get("summary", "")
        )
        self.db.add(new_plan)
        await self.db.flush() # new_plan.id를 얻기 위해
        
        for daily in meal_plan_data.get("weekly_plan", []):
            day = daily.get("day")
            is_rest_day = daily.get("is_rest_day", False)
            for meal in daily.get("meals", []):
                new_item = MealPlanItem(
                    plan_id=new_plan.id,
                    day=day,
                    is_rest_day=is_rest_day,
                    meal_type=meal.get("meal_type"),
                    menu_name=meal.get("menu_name"),
                    calories=meal.get("calories"),
                    protein_g=meal.get("protein_g"),
                    carbs_g=meal.get("carbs_g"),
                    fat_g=meal.get("fat_g"),
                )
                self.db.add(new_item)
                
        await self.db.commit()
