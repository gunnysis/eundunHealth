import pytest
from httpx import AsyncClient


@pytest.mark.asyncio
async def test_generate_meal_plan(
    client: AsyncClient,
    sample_profile: dict,  # DB에 mock 프로필이 있다고 가정
    monkeypatch: pytest.MonkeyPatch,
):
    # AI 클라이언트를 Mock 처리하여 실제 API 호출 방지
    async def mock_generate(*args, **kwargs):
        from app.schemas.meal_plan import DailyMealPlan, MealInfo, WeeklyMealPlanResponse
        
        return WeeklyMealPlanResponse(
            weekly_plan=[
                DailyMealPlan(
                    day="2026-09-21",
                    is_rest_day=False,
                    meals=[
                        MealInfo(
                            meal_type="점심",
                            menu_name="현미밥과 닭가슴살 샐러드",
                            calories=500,
                            protein_g=40,
                            carbs_g=50,
                            fat_g=10
                        )
                    ]
                )
            ],
            summary="건강한 한 주 되세요!"
        )

    # _build_user_prompt 등 내부 메서드가 아닌, 실제 generate_meal_plan 자체를 패치
    monkeypatch.setattr(
        "app.routers.meal_plan.AzureMaaSMealClient.generate_meal_plan",
        mock_generate
    )

    # 1. 프로필 생성 (식단 생성 시 프로필 정보가 필요함)
    await client.put(
        "/profile",
        json={"heightCm": 175.0, "weightKg": 70.0}
    )

    response = await client.post(
        "/meals/generate"
    )
    
    assert response.status_code == 200
    data = response.json()
    assert "weeklyPlan" in data
    assert "summary" in data
    assert data["summary"] == "건강한 한 주 되세요!"
    assert len(data["weeklyPlan"]) == 1
    assert data["weeklyPlan"][0]["day"] == "2026-09-21"

    # GET 검증 (방금 저장된 식단 조회)
    get_res = await client.get(
        "/meals/current"
    )
    assert get_res.status_code == 200
    get_data = get_res.json()
    assert get_data["summary"] == "건강한 한 주 되세요!"
    assert len(get_data["weeklyPlan"]) == 1
    assert get_data["weeklyPlan"][0]["day"] == "2026-09-21"
    assert get_data["weeklyPlan"][0]["meals"][0]["menuName"] == "현미밥과 닭가슴살 샐러드"
