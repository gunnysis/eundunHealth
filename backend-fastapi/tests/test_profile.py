import pytest


@pytest.mark.asyncio
async def test_create_and_get_profile(client):
    resp = await client.put("/profile", json={"heightCm": 175.0, "weightKg": 70.0})
    assert resp.status_code == 200

    resp = await client.get("/profile")
    assert resp.status_code == 200
    data = resp.json()
    assert data["heightCm"] == 175.0
    assert data["weightKg"] == 70.0


@pytest.mark.asyncio
async def test_get_profile_not_found(client):
    resp = await client.get("/profile")
    assert resp.status_code == 404


@pytest.mark.asyncio
async def test_update_profile(client):
    await client.put("/profile", json={"heightCm": 175.0, "weightKg": 70.0})
    resp = await client.put("/profile", json={"heightCm": 180.0, "weightKg": 75.0})
    assert resp.status_code == 200

    resp = await client.get("/profile")
    data = resp.json()
    assert data["heightCm"] == 180.0
    assert data["weightKg"] == 75.0
