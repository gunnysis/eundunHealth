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
