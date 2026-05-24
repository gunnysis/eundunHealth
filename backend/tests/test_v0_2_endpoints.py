"""v0.2 신규 엔드포인트 테스트.

- GET /weekly-plan/previous: 직전 주 plan 또는 null
- GET /weekly-plan/statistics: 최근 N주 완료율 + 스트릭
"""
import json

import pytest


def _day(rest: bool = False, completed: bool = False) -> dict:
    """한 day 객체 — completed=True면 모든 운동이 완료됐다고 본다."""
    if rest:
        return {"isRestDay": True, "exercises": []}
    return {
        "isRestDay": False,
        "exercises": [{"id": "e1", "completed": completed}, {"id": "e2", "completed": completed}],
    }


def _plan_payload(week_start: str, completed_workout_days: int = 0, workout_days: int = 5) -> dict:
    """workout_days만큼 workout day, 나머지는 rest로. completed_workout_days만 100% 완료."""
    days = []
    for i in range(7):
        if i < workout_days:
            days.append(_day(rest=False, completed=(i < completed_workout_days)))
        else:
            days.append(_day(rest=True))
    return {"weekStart": week_start, "dayPlans": json.dumps(days)}


# === GET /weekly-plan/previous ===

@pytest.mark.asyncio
async def test_previous_returns_null_when_no_prior_plan(client):
    resp = await client.get("/weekly-plan/previous", params={"week_start": "2026-05-25"})
    assert resp.status_code == 200
    assert resp.json() is None


@pytest.mark.asyncio
async def test_previous_returns_closest_earlier_plan(client):
    # 두 주의 plan 등록 후 더 최근 주 기준으로 직전 plan 조회
    await client.post("/weekly-plan", json=_plan_payload("2026-05-04"))
    await client.post("/weekly-plan", json=_plan_payload("2026-05-18"))

    resp = await client.get("/weekly-plan/previous", params={"week_start": "2026-05-25"})
    assert resp.status_code == 200
    assert resp.json()["weekStart"] == "2026-05-18"


@pytest.mark.asyncio
async def test_previous_excludes_same_week(client):
    await client.post("/weekly-plan", json=_plan_payload("2026-05-25"))

    resp = await client.get("/weekly-plan/previous", params={"week_start": "2026-05-25"})
    assert resp.status_code == 200
    assert resp.json() is None


# === GET /weekly-plan/statistics ===

@pytest.mark.asyncio
async def test_statistics_empty_user(client):
    resp = await client.get("/weekly-plan/statistics")
    assert resp.status_code == 200
    data = resp.json()
    assert data == {"weeklyRates": [], "currentStreak": 0, "longestStreak": 0}


@pytest.mark.asyncio
async def test_statistics_completion_rate(client):
    # 5 운동일 중 3일 완료 = 0.6, 2일 완료 = 0.4
    await client.post("/weekly-plan", json=_plan_payload("2026-05-04", completed_workout_days=3))
    await client.post("/weekly-plan", json=_plan_payload("2026-05-11", completed_workout_days=2))

    resp = await client.get("/weekly-plan/statistics")
    data = resp.json()
    # 차트는 오름차순
    assert [r["weekStart"] for r in data["weeklyRates"]] == ["2026-05-04", "2026-05-11"]
    assert data["weeklyRates"][0]["completionRate"] == pytest.approx(0.6)
    assert data["weeklyRates"][1]["completionRate"] == pytest.approx(0.4)


@pytest.mark.asyncio
async def test_statistics_streaks(client):
    # 최근 3주: 100/0/100/100/100 (오래된→최근) → current=3, longest=3
    await client.post("/weekly-plan", json=_plan_payload("2026-04-13", completed_workout_days=5))
    await client.post("/weekly-plan", json=_plan_payload("2026-04-20", completed_workout_days=0))
    await client.post("/weekly-plan", json=_plan_payload("2026-04-27", completed_workout_days=5))
    await client.post("/weekly-plan", json=_plan_payload("2026-05-04", completed_workout_days=5))
    await client.post("/weekly-plan", json=_plan_payload("2026-05-11", completed_workout_days=5))

    resp = await client.get("/weekly-plan/statistics")
    data = resp.json()
    assert data["currentStreak"] == 3
    assert data["longestStreak"] == 3


@pytest.mark.asyncio
async def test_statistics_respects_weeks_param(client):
    # 13주 plan 등록 후 weeks=5로 조회하면 5개만
    for i in range(13):
        await client.post(
            "/weekly-plan",
            json=_plan_payload(f"2026-0{2 + i // 4}-{(1 + (i % 4) * 7):02d}", completed_workout_days=5),
        )

    resp = await client.get("/weekly-plan/statistics", params={"weeks": 5})
    assert len(resp.json()["weeklyRates"]) == 5


@pytest.mark.asyncio
async def test_statistics_rejects_weeks_above_52(client):
    resp = await client.get("/weekly-plan/statistics", params={"weeks": 53})
    assert resp.status_code == 422
