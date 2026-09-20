from typing import Annotated

from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.dependencies import get_current_user_id
from app.exceptions import AppException
from app.schemas.meal_plan import WeeklyMealPlanResponse
from app.services.meal_ai_client import AzureMaaSMealClient
from app.services.meal_plan_service import MealPlanService
from app.services.profile_service import ProfileService
from app.services.weekly_plan_service import WeeklyPlanService

router = APIRouter(prefix="/meals", tags=["meals"])


@router.post("/generate", response_model=WeeklyMealPlanResponse)
async def generate_meal_plan(
    user_id: Annotated[str, Depends(get_current_user_id)],
    db: Annotated[AsyncSession, Depends(get_db)],
) -> WeeklyMealPlanResponse:
    """사용자의 프로필 및 이번 주 운동 계획을 바탕으로 주간 맞춤형 식단을 자동 생성한다."""
    profile_service = ProfileService(db)
    weekly_plan_service = WeeklyPlanService(db)

    # 1. 프로필 조회 (예외 처리 내장됨)
    profile = await profile_service.get_profile(user_id)
    
    # 성별 필드는 현재 스키마에 없으므로 (향후 추가될 예정), 기본값(또는 유추)으로 계산
    # BMR 계산 (Mifflin-St Jeor 공식 기준 대략적 산출)
    # 남성(기본 가정): 10 * weight + 6.25 * height - 5 * age + 5 (나이는 임의 30세 가정)
    bmr = (10 * profile.weight_kg) + (6.25 * profile.height_cm) - (5 * 30) + 5
    tdee = bmr * 1.55  # 적당한 활동량 가정
    
    # 매크로 목표 (탄단지 4:4:2 비율)
    total_calories = int(tdee)
    protein_g = int((total_calories * 0.4) / 4)
    carbs_g = int((total_calories * 0.4) / 4)
    fat_g = int((total_calories * 0.2) / 9)
    
    target_macros = {
        "total_calories": total_calories,
        "protein_g": protein_g,
        "carbs_g": carbs_g,
        "fat_g": fat_g,
    }

    profile_summary = f"""
    - 키: {profile.height_cm}cm
    - 몸무게: {profile.weight_kg}kg
    - 체지방률: {profile.body_fat_pct or '모름'}%
    - 골격근량: {profile.muscle_mass_kg or '모름'}kg
    """

    # 2. 이번 주 운동 계획 조회
    import datetime
    
    # 이번 주 월요일 계산
    today = datetime.date.today()
    monday = today - datetime.timedelta(days=today.weekday())
    week_start = monday.isoformat()
    
    workout_dates = []
    try:
        plan = await weekly_plan_service.get_plan(user_id, week_start)
        # plan.day_plans는 JSON string이므로 파싱 필요
        import json
        day_plans = json.loads(plan.day_plans)
        for dp in day_plans:
            # day format: "2026-09-21"
            date = dp.get("date")
            exercises = dp.get("exercises", [])
            if date and exercises:
                workout_dates.append(date)
    except Exception:
        # 계획이 없거나 파싱 에러 시 빈 목록
        pass

    # 3. AI 클라이언트 호출
    ai_client = AzureMaaSMealClient()
    try:
        meal_plan = await ai_client.generate_meal_plan(
            profile_summary=profile_summary,
            target_macros=target_macros,
            workout_dates=workout_dates
        )
        
        # 4. DB에 식단 저장
        meal_service = MealPlanService(db)
        await meal_service.save_plan(user_id, monday, meal_plan)
        
        return meal_plan
    except Exception as e:
        raise AppException(
            status_code=500,
            code="MEAL_GENERATE_FAILED",
            message="식단 자동 생성에 실패했습니다."
        ) from e


@router.get("/current", response_model=WeeklyMealPlanResponse)
async def get_current_meal_plan(
    user_id: Annotated[str, Depends(get_current_user_id)],
    db: Annotated[AsyncSession, Depends(get_db)],
) -> WeeklyMealPlanResponse:
    """이번 주의 식단(DB에 저장된 가장 최근 생성본)을 반환한다."""
    import datetime
    today = datetime.date.today()
    monday = today - datetime.timedelta(days=today.weekday())
    
    meal_service = MealPlanService(db)
    plan = await meal_service.get_current_plan(user_id, monday)
    
    if not plan:
        raise AppException(
            status_code=404,
            code="MEAL_PLAN_NOT_FOUND",
            message="이번 주에 생성된 식단이 없습니다."
        )
        
    return plan
