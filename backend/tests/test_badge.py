import pytest


@pytest.mark.asyncio
async def test_award_and_list_badges(client):
    resp = await client.post("/badges/week_1_complete")
    assert resp.status_code == 200

    resp = await client.get("/badges")
    assert resp.status_code == 200
    assert len(resp.json()) == 1
    assert resp.json()[0]["badgeKey"] == "week_1_complete"


@pytest.mark.asyncio
async def test_award_duplicate_badge(client):
    await client.post("/badges/week_1_complete")
    resp = await client.post("/badges/week_1_complete")
    assert resp.status_code == 409


@pytest.mark.asyncio
async def test_award_invalid_badge(client):
    resp = await client.post("/badges/invalid_key")
    assert resp.status_code == 400
