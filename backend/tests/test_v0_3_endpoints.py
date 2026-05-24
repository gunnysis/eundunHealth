"""v0.3 신규 엔드포인트 테스트.

- GET /profile/history: 프로필 변경 이력 (PUT /profile마다 한 줄 추가)
- GET/PUT /goals: 목표 upsert / 목록
- PUT /profile + rest_day: 휴식일 변경
"""
import pytest

# === GET /profile/history ===

@pytest.mark.asyncio
async def test_profile_history_records_every_upsert(client):
    # 3번 PUT (각기 다른 weight) — SQLite의 server_default가 1초 정밀도라
    # 정렬은 set 비교로 검증한다. 프로덕션 PostgreSQL은 마이크로초 정밀도라 안전.
    await client.put("/profile", json={"heightCm": 175.0, "weightKg": 70.0})
    await client.put("/profile", json={"heightCm": 175.0, "weightKg": 71.5})
    await client.put("/profile", json={"heightCm": 175.0, "weightKg": 72.0})

    resp = await client.get("/profile/history")
    assert resp.status_code == 200
    entries = resp.json()
    assert len(entries) == 3
    weights = {e["weightKg"] for e in entries}
    assert weights == {70.0, 71.5, 72.0}


@pytest.mark.asyncio
async def test_profile_history_empty_for_new_user(client):
    resp = await client.get("/profile/history")
    assert resp.status_code == 200
    assert resp.json() == []


@pytest.mark.asyncio
async def test_profile_history_limit_query(client):
    # 5번 PUT 후 limit=3 → 3개만 반환 (SQLite 정밀도 한계로 어떤 3개인지는 비결정)
    for w in [70.0, 71.0, 72.0, 73.0, 74.0]:
        await client.put("/profile", json={"heightCm": 175.0, "weightKg": w})
    resp = await client.get("/profile/history", params={"limit": 3})
    assert resp.status_code == 200
    body = resp.json()
    assert len(body) == 3
    # 반환된 3개는 모두 우리가 PUT한 값 중 일부여야 함
    assert all(e["weightKg"] in {70.0, 71.0, 72.0, 73.0, 74.0} for e in body)


# === GET/PUT /goals ===

@pytest.mark.asyncio
async def test_goals_empty_by_default(client):
    resp = await client.get("/goals")
    assert resp.status_code == 200
    assert resp.json() == []


@pytest.mark.asyncio
async def test_goal_upsert_creates_then_updates(client):
    # 첫 PUT — 생성
    resp = await client.put("/goals", json={"goalType": "weight", "targetValue": 70.0})
    assert resp.status_code == 200
    assert resp.json()["goalType"] == "weight"
    assert resp.json()["targetValue"] == 70.0

    # 두 번째 PUT — 같은 type → 값만 갱신 (row 1개 유지)
    resp = await client.put("/goals", json={"goalType": "weight", "targetValue": 68.0})
    assert resp.status_code == 200
    assert resp.json()["targetValue"] == 68.0

    resp = await client.get("/goals")
    assert len(resp.json()) == 1


@pytest.mark.asyncio
async def test_goals_two_types_coexist(client):
    await client.put("/goals", json={"goalType": "weight", "targetValue": 70.0})
    await client.put("/goals", json={"goalType": "body_fat", "targetValue": 18.0})

    resp = await client.get("/goals")
    body = resp.json()
    assert len(body) == 2
    types = sorted(g["goalType"] for g in body)
    assert types == ["body_fat", "weight"]


@pytest.mark.asyncio
async def test_goal_rejects_invalid_type(client):
    resp = await client.put("/goals", json={"goalType": "muscle_mass", "targetValue": 30.0})
    assert resp.status_code == 422


@pytest.mark.asyncio
async def test_goal_rejects_non_positive_value(client):
    resp = await client.put("/goals", json={"goalType": "weight", "targetValue": 0})
    assert resp.status_code == 422


# === PUT /profile + rest_day (§L) ===

@pytest.mark.asyncio
async def test_profile_rest_day_default_is_7(client):
    # rest_day 미지정 → 기본 7 (Sunday)
    await client.put("/profile", json={"heightCm": 175.0, "weightKg": 70.0})
    resp = await client.get("/profile")
    assert resp.json()["restDay"] == 7


@pytest.mark.asyncio
async def test_profile_rest_day_custom(client):
    # 휴식일을 수요일로 변경
    await client.put("/profile", json={"heightCm": 175.0, "weightKg": 70.0, "restDay": 3})
    resp = await client.get("/profile")
    assert resp.json()["restDay"] == 3


@pytest.mark.asyncio
async def test_profile_rest_day_out_of_range_rejected(client):
    resp = await client.put("/profile", json={"heightCm": 175.0, "weightKg": 70.0, "restDay": 8})
    assert resp.status_code == 422


# === 배지 keys (§N) — VALID_BADGE_KEYS 확장 검증 ===

@pytest.mark.asyncio
async def test_award_v0_3_milestone_badge(client):
    # first_workout은 v0.3에서 신규로 허용된 배지 키
    resp = await client.post("/badges/first_workout")
    assert resp.status_code == 200

    resp = await client.get("/badges")
    keys = [b["badgeKey"] for b in resp.json()]
    assert "first_workout" in keys


@pytest.mark.asyncio
async def test_award_goal_achievement_badge(client):
    resp = await client.post("/badges/goal_weight_achieved")
    assert resp.status_code == 200
