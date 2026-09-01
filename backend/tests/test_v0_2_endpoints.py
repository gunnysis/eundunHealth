"""v0.2 신규 엔드포인트 테스트.

- GET /weekly-plan/previous: 직전 주 plan 또는 null
- GET /weekly-plan/statistics: 최근 N주 완료율 + 스트릭
"""
import json

import pytest


def _day(rest: bool = False, completed: bool = False) -> dict:
    """한 day 객체 — 실제 저장 형태처럼 day 단위 isCompleted 를 포함한다.

    통계는 day.isCompleted 로 완료를 판정하므로(Home 과 동일) 운동 단위 completed 와 함께
    day.isCompleted 도 일치시켜 둔다.
    """
    if rest:
        return {"isRestDay": True, "isCompleted": False, "exercises": []}
    return {
        "isRestDay": False,
        "isCompleted": completed,
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
    resp = await client.get("/weekly-plan/previous", params={"weekStart": "2026-05-25"})
    assert resp.status_code == 200
    assert resp.json() is None


@pytest.mark.asyncio
async def test_previous_returns_closest_earlier_plan(client):
    # 두 주의 plan 등록 후 더 최근 주 기준으로 직전 plan 조회
    await client.post("/weekly-plan", json=_plan_payload("2026-05-04"))
    await client.post("/weekly-plan", json=_plan_payload("2026-05-18"))

    resp = await client.get("/weekly-plan/previous", params={"weekStart": "2026-05-25"})
    assert resp.status_code == 200
    assert resp.json()["weekStart"] == "2026-05-18"


@pytest.mark.asyncio
async def test_previous_excludes_same_week(client):
    await client.post("/weekly-plan", json=_plan_payload("2026-05-25"))

    resp = await client.get("/weekly-plan/previous", params={"weekStart": "2026-05-25"})
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


@pytest.mark.asyncio
async def test_statistics_uses_day_is_completed_even_with_empty_exercises(client):
    """통계는 Home 화면과 동일하게 day.isCompleted 로 완료를 판정한다.

    과거 R8 keep 갭으로 운동이 빈(exercises=[]) day 가 저장됐어도, day.isCompleted=True 면
    완료로 집계되어야 한다(이전엔 빈 운동일을 통째로 skip 해 이력이 영구 왜곡됐음 — H1).
    """
    days = [
        {"isRestDay": False, "isCompleted": True, "exercises": []},
        {"isRestDay": False, "isCompleted": False, "exercises": []},
        {"isRestDay": True, "isCompleted": False, "exercises": []},
    ]
    await client.post("/weekly-plan", json={"weekStart": "2026-05-25", "dayPlans": json.dumps(days)})

    resp = await client.get("/weekly-plan/statistics")
    # 운동일 2개(빈 운동 포함) 중 1개 완료 → 0.5
    assert resp.json()["weeklyRates"][0]["completionRate"] == pytest.approx(0.5)


@pytest.mark.asyncio
async def test_statistics_all_rest_days_is_zero_not_error(client):
    """운동일이 0인 주(전부 휴식)는 0/0 이다 — 0.0 으로 떨어져야지 500 이 되면 안 된다."""
    await client.post("/weekly-plan", json=_plan_payload("2026-05-04", workout_days=0))

    resp = await client.get("/weekly-plan/statistics")
    assert resp.status_code == 200
    assert resp.json()["weeklyRates"][0]["completionRate"] == 0.0


@pytest.mark.asyncio
async def test_statistics_unparseable_day_plans_is_zero_not_error(client, db_engine):
    """DB 의 day_plans 가 깨져 있어도 통계 전체가 죽으면 안 된다 — 그 주만 0.0.

    스키마 변경·수동 수정·부분 쓰기로 실제로 생길 수 있는 상태다. 한 주의 파싱 실패가
    12주 차트 전체를 500 으로 만들면 사용자는 통계 화면 자체를 못 본다.
    """
    from sqlalchemy import text
    from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

    await client.post("/weekly-plan", json=_plan_payload("2026-05-04", completed_workout_days=3))

    session_factory = async_sessionmaker(db_engine, class_=AsyncSession, expire_on_commit=False)
    async with session_factory() as session:
        await session.execute(text("UPDATE weekly_plans SET day_plans = 'not-json'"))
        await session.commit()

    resp = await client.get("/weekly-plan/statistics")
    assert resp.status_code == 200
    assert resp.json()["weeklyRates"][0]["completionRate"] == 0.0
