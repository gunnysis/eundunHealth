import pytest


@pytest.mark.asyncio
async def test_create_and_get_plan(client, sample_plan):
    resp = await client.post("/weekly-plan", json=sample_plan)
    assert resp.status_code == 200

    resp = await client.get("/weekly-plan", params={"weekStart": sample_plan["weekStart"]})
    assert resp.status_code == 200
    assert resp.json()["weekStart"] == sample_plan["weekStart"]


@pytest.mark.asyncio
async def test_get_plan_not_found(client):
    resp = await client.get("/weekly-plan", params={"weekStart": "2099-01-01"})
    assert resp.status_code == 404


@pytest.mark.asyncio
async def test_history_empty(client):
    resp = await client.get("/weekly-plan/history")
    assert resp.status_code == 200
    # envelope 응답 — Android HistoryViewModel이 totalCount를 페이지 인디케이터로 사용한다
    assert resp.json() == {"plans": [], "totalCount": 0, "page": 0, "size": 10}


@pytest.mark.asyncio
async def test_history_with_data(client, sample_plan):
    await client.post("/weekly-plan", json=sample_plan)
    await client.post(
        "/weekly-plan",
        json={"weekStart": "2026-06-01", "dayPlans": sample_plan["dayPlans"]},
    )

    resp = await client.get("/weekly-plan/history")
    assert resp.status_code == 200
    body = resp.json()
    assert len(body["plans"]) == 2
    assert body["totalCount"] == 2
    assert body["page"] == 0
    assert body["size"] == 10
