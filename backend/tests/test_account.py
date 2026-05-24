import pytest


@pytest.mark.asyncio
async def test_delete_account(client, sample_profile, supabase_delete_mock):
    # 프로필 생성 → 존재 확인
    await client.put("/profile", json=sample_profile)
    assert (await client.get("/profile")).status_code == 200

    # Supabase Admin API 호출을 mock → 계정 삭제
    with supabase_delete_mock(status_code=200):
        resp = await client.delete("/account")
        assert resp.status_code == 200

    # 프로필이 삭제되었는지 확인
    resp = await client.get("/profile")
    assert resp.status_code == 404
