from typing import Annotated

from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.dependencies import get_current_user_id
from app.exceptions import AppException, NotFoundException
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

    gender_str = "여성" if profile.gender == "female" else "남성" if profile.gender == "male" else "성별 무관"
    profile_summary = f"""
    - 성별: {gender_str}
    - 키: {profile.height_cm}cm
    - 몸무게: {profile.weight_kg}kg
    - 체지방률: {profile.body_fat_pct or "모름"}%
    - 골격근량: {profile.muscle_mass_kg or "모름"}kg
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
    except NotFoundException:
        # 운동 계획이 없는 정상적인 첫 사용자 경로만 빈 목록으로 처리한다.
        pass

    # 3. AI 클라이언트 호출
    ai_client = AzureMaaSMealClient()
    try:
        meal_plan = await ai_client.generate_meal_plan(profile_summary=profile_summary, workout_dates=workout_dates)

        # 4. DB에 식단 저장
        meal_service = MealPlanService(db)
        await meal_service.save_plan(user_id, monday, meal_plan)

        return meal_plan
    except Exception as e:
        if isinstance(e, AppException):
            raise
        raise AppException(
            status_code=500, code="MEAL_GENERATE_FAILED", message="식단 자동 생성에 실패했습니다."
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
        raise AppException(status_code=404, code="MEAL_PLAN_NOT_FOUND", message="이번 주에 생성된 식단이 없습니다.")

    return plan
