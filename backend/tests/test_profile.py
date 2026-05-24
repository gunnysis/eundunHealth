import pytest


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
