from unittest.mock import AsyncMock, patch

import httpx
import pytest


@pytest.mark.asyncio
async def test_delete_account(client):
    # 프로필 생성
    await client.put("/profile", json={"heightCm": 175.0, "weightKg": 70.0})
    # 생성 확인
    resp = await client.get("/profile")
    assert resp.status_code == 200

    # Supabase API mock
    mock_response = httpx.Response(200, request=httpx.Request("DELETE", "http://mock"))

    with patch("app.services.account_service.httpx.AsyncClient") as mock_cls:
        instance = AsyncMock()
        instance.delete = AsyncMock(return_value=mock_response)
        mock_cls.return_value = instance
        instance.__aenter__ = AsyncMock(return_value=instance)
        instance.__aexit__ = AsyncMock(return_value=False)

        resp = await client.delete("/account")
        assert resp.status_code == 200

    # 프로필 삭제 확인
    resp = await client.get("/profile")
    assert resp.status_code == 404
