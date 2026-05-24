import pytest


@pytest.mark.asyncio
async def test_create_and_get_plan(client, sample_plan):
    resp = await client.post("/weekly-plan", json=sample_plan)
    assert resp.status_code == 200

    resp = await client.get("/weekly-plan", params={"week_start": sample_plan["weekStart"]})
    assert resp.status_code == 200
    assert resp.json()["weekStart"] == sample_plan["weekStart"]


@pytest.mark.asyncio
async def test_get_plan_not_found(client):
    resp = await client.get("/weekly-plan", params={"week_start": "2099-01-01"})
    assert resp.status_code == 404


@pytest.mark.asyncio
async def test_history_empty(client):
    resp = await client.get("/weekly-plan/history")
    assert resp.status_code == 200
    assert resp.json() == []


@pytest.mark.asyncio
async def test_history_with_data(client, sample_plan):
    await client.post("/weekly-plan", json=sample_plan)
    await client.post(
        "/weekly-plan",
        json={"weekStart": "2026-06-01", "dayPlans": sample_plan["dayPlans"]},
    )

    resp = await client.get("/weekly-plan/history")
    assert resp.status_code == 200
    assert len(resp.json()) == 2
