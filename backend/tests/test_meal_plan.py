import pytest
from httpx import AsyncClient
from pydantic import ValidationError

from app.config import Settings, get_settings
from app.main import app
from app.schemas.meal_plan import DailyMealPlan, MealInfo, WeeklyMealPlanResponse


def test_meal_plan_rejects_duplicate_days():
    """AI 출력은 서로 다른 7일로 구성되어야 한다."""
    meal = MealInfo(meal_type="점심", menu_name="현미밥", calories=500, protein_g=20, carbs_g=60, fat_g=10)
    with pytest.raises(ValidationError, match="day values must be unique"):
        WeeklyMealPlanResponse(
            weekly_plan=[DailyMealPlan(day="월요일", is_rest_day=False, meals=[meal]) for _ in range(7)],
            summary="참고용 식단입니다.",
        )


@pytest.mark.asyncio
async def test_generate_meal_plan_fails_closed_when_ai_is_disabled(client: AsyncClient):
    app.dependency_overrides[get_settings] = lambda: Settings(
        database_url="sqlite+aiosqlite:///:memory:",
        deepseek_endpoint="https://test-endpoint.openai.azure.com/openai/v1",
        deepseek_key="test-key",
        meal_ai_enabled=False,
    )

    response = await client.post("/meals/generate", headers={"X-AI-Data-Processing-Consent": "true"})

    assert response.status_code == 503
    assert response.json()["code"] == "MEAL_AI_DISABLED"


@pytest.mark.asyncio
async def test_generate_meal_plan_requires_explicit_consent(client: AsyncClient):
    missing = await client.post("/meals/generate")
    declined = await client.post("/meals/generate", headers={"X-AI-Data-Processing-Consent": "false"})

    assert missing.status_code == 422
    assert declined.status_code == 400
    assert declined.json()["code"] == "AI_DATA_PROCESSING_CONSENT_REQUIRED"


@pytest.mark.asyncio
async def test_generate_meal_plan(
    client: AsyncClient,
    sample_profile: dict,  # DB에 mock 프로필이 있다고 가정
    monkeypatch: pytest.MonkeyPatch,
):
    call_count = 0

    # AI 클라이언트를 Mock 처리하여 실제 API 호출 방지
    async def mock_generate(*args, **kwargs):
        nonlocal call_count
        call_count += 1
        from app.schemas.meal_plan import DailyMealPlan, MealInfo, WeeklyMealPlanResponse

        return WeeklyMealPlanResponse(
            weekly_plan=[
                DailyMealPlan(
                    day=f"2026-09-{day:02d}",
                    is_rest_day=False,
                    meals=[
                        MealInfo(
                            meal_type="점심",
                            menu_name="현미밥과 닭가슴살 샐러드",
                            calories=500,
                            protein_g=40,
                            carbs_g=50,
                            fat_g=10,
                        )
                    ],
                )
                for day in range(21, 28)
            ],
            summary="건강한 한 주 되세요!",
        )

    # _build_user_prompt 등 내부 메서드가 아닌, 실제 generate_meal_plan 자체를 패치
    monkeypatch.setattr("app.routers.meal_plan.AzureMaaSMealClient.generate_meal_plan", mock_generate)

    # 1. 프로필 생성 (식단 생성 시 프로필 정보가 필요함)
    await client.put("/profile", json={"heightCm": 175.0, "weightKg": 70.0})

    consent_header = {"X-AI-Data-Processing-Consent": "true"}
    response = await client.post("/meals/generate", headers=consent_header)

    assert response.status_code == 200
    data = response.json()
    assert "weeklyPlan" in data
    assert "summary" in data
    assert data["summary"] == "건강한 한 주 되세요!"
    assert len(data["weeklyPlan"]) == 7
    assert data["weeklyPlan"][0]["day"] == "2026-09-21"

    # GET 검증 (방금 저장된 식단 조회)
    get_res = await client.get("/meals/current")
    assert get_res.status_code == 200
    get_data = get_res.json()
    assert get_data["summary"] == "건강한 한 주 되세요!"
    assert len(get_data["weeklyPlan"]) == 7
    assert get_data["weeklyPlan"][0]["day"] == "2026-09-21"
    assert get_data["weeklyPlan"][0]["meals"][0]["menuName"] == "현미밥과 닭가슴살 샐러드"

    # 같은 주 재생성도 unique(user_id, week_start_date) 충돌 없이 교체되어야 한다.
    regenerate = await client.post("/meals/generate", headers=consent_header)
    assert regenerate.status_code == 200
    assert call_count == 2
