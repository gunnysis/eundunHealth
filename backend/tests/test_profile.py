import pytest
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from app.models.user_profile_history import UserProfileHistory


@pytest.mark.asyncio
async def test_create_and_get_profile(client, sample_profile):
    resp = await client.put("/profile", json=sample_profile)
    assert resp.status_code == 200

    resp = await client.get("/profile")
    assert resp.status_code == 200
    data = resp.json()
    assert data["heightCm"] == sample_profile["heightCm"]
    assert data["weightKg"] == sample_profile["weightKg"]


@pytest.mark.asyncio
async def test_get_profile_not_found(client):
    resp = await client.get("/profile")
    assert resp.status_code == 404


@pytest.mark.asyncio
async def test_update_profile(client, sample_profile):
    await client.put("/profile", json=sample_profile)
    resp = await client.put("/profile", json={"heightCm": 180.0, "weightKg": 75.0})
    assert resp.status_code == 200

    resp = await client.get("/profile")
    data = resp.json()
    assert data["heightCm"] == 180.0
    assert data["weightKg"] == 75.0


@pytest.mark.asyncio
async def test_update_profile_blocks_unsupported_client_from_resetting_rest_days(client, sample_profile):
    """구버전(<=40) 클라이언트가 PUT /profile을 호출해 rest_days를 초기화하는 것을 차단한다."""
    await client.put("/profile", json={**sample_profile, "restDays": [3, 6, 7]})
    blocked = await client.put(
        "/profile",
        json=sample_profile,
        headers={"Eundun-Client-Version-Code": "40"},
    )
    assert blocked.status_code == 403
    assert blocked.json()["code"] == "client_update_required"
    assert blocked.json()["minimumSupportedVersionCode"] == 41

    got = await client.get("/profile")
    assert got.json()["restDays"] == [3, 6, 7]


@pytest.mark.asyncio
async def test_update_default_workout_mode_does_not_add_measurement_history(client, db_engine, sample_profile):
    await client.put("/profile", json=sample_profile)
    session_factory = async_sessionmaker(db_engine, class_=AsyncSession, expire_on_commit=False)

    async def history_count() -> int:
        async with session_factory() as session:
            return int(await session.scalar(select(func.count()).select_from(UserProfileHistory)) or 0)

    before = await history_count()
    resp = await client.put("/v2/profile/default-workout-plan-mode", json={"defaultWorkoutPlanMode": "home"})
    assert resp.status_code == 200
    assert resp.json()["defaultWorkoutPlanMode"] == "home"
    assert await history_count() == before

    profile = await client.get("/profile")
    assert profile.json()["defaultWorkoutPlanMode"] == "home"


@pytest.mark.asyncio
async def test_update_default_workout_mode_requires_existing_profile(client):
    resp = await client.put("/v2/profile/default-workout-plan-mode", json={"defaultWorkoutPlanMode": "gym"})
    assert resp.status_code == 404
