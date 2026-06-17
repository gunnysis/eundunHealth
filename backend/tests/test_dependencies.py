import threading

import pytest
from fastapi import HTTPException
from fastapi.security import HTTPAuthorizationCredentials
from jwt import InvalidTokenError, PyJWKClientError

from app import dependencies
from app.config import Settings


def _settings() -> Settings:
    return Settings(
        database_url="sqlite+aiosqlite:///:memory:",
        supabase_url="https://test.supabase.co",
        supabase_service_role_key="test-key",
    )


def _creds() -> HTTPAuthorizationCredentials:
    return HTTPAuthorizationCredentials(scheme="Bearer", credentials="dummy.jwt.token")


def _raise(exc: Exception):
    raise exc


class _FakeJwk:
    def __init__(self, exc: Exception | None = None):
        self._exc = exc

    def get_signing_key_from_jwt(self, token):
        if self._exc:
            raise self._exc
        return type("K", (), {"key": "fake-key"})()


@pytest.mark.asyncio
async def test_invalid_token_returns_401(monkeypatch):
    monkeypatch.setattr(dependencies, "_get_jwk_client", lambda url: _FakeJwk())
    monkeypatch.setattr(dependencies.jwt, "decode", lambda *a, **k: _raise(InvalidTokenError()))
    with pytest.raises(HTTPException) as ei:
        await dependencies.get_current_user_id(_creds(), _settings())
    assert ei.value.status_code == 401


@pytest.mark.asyncio
async def test_jwks_client_error_returns_503_not_401(monkeypatch):
    monkeypatch.setattr(dependencies, "_get_jwk_client", lambda url: _FakeJwk(PyJWKClientError("jwks down")))
    with pytest.raises(HTTPException) as ei:
        await dependencies.get_current_user_id(_creds(), _settings())
    assert ei.value.status_code == 503


@pytest.mark.asyncio
async def test_missing_sub_returns_401(monkeypatch):
    monkeypatch.setattr(dependencies, "_get_jwk_client", lambda url: _FakeJwk())
    monkeypatch.setattr(dependencies.jwt, "decode", lambda *a, **k: {"aud": "authenticated"})
    with pytest.raises(HTTPException) as ei:
        await dependencies.get_current_user_id(_creds(), _settings())
    assert ei.value.status_code == 401


@pytest.mark.asyncio
async def test_unexpected_error_propagates_not_401(monkeypatch):
    monkeypatch.setattr(dependencies, "_get_jwk_client", lambda url: _FakeJwk(RuntimeError("bug")))
    with pytest.raises(RuntimeError):
        await dependencies.get_current_user_id(_creds(), _settings())


@pytest.mark.asyncio
async def test_signing_key_lookup_runs_off_event_loop(monkeypatch):
    """JWKS 동기 조회가 워커 스레드로 오프로드되어 이벤트 루프를 막지 않는다."""
    main_thread = threading.get_ident()
    captured: dict[str, int] = {}

    class _ThreadCapturingJwk:
        def get_signing_key_from_jwt(self, token):
            captured["thread"] = threading.get_ident()
            return type("K", (), {"key": "fake-key"})()

    monkeypatch.setattr(dependencies, "_get_jwk_client", lambda url: _ThreadCapturingJwk())
    monkeypatch.setattr(dependencies.jwt, "decode", lambda *a, **k: {"sub": "user-1"})

    result = await dependencies.get_current_user_id(_creds(), _settings())

    assert result == "user-1"
    assert captured["thread"] != main_thread  # 워커 스레드 = 루프 블로킹 안 함


def test_jwk_client_uses_short_timeout(monkeypatch):
    """느린 JWKS 가 워커를 30초간 점유하지 못하게 기본 30s → 5s."""
    dependencies._jwk_client = None  # 모듈 전역 캐시 리셋
    client = dependencies._get_jwk_client("https://test.supabase.co")
    assert client.timeout == 5
    dependencies._jwk_client = None  # 다른 테스트 격리
