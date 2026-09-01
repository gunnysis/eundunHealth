import threading

import pytest
from fastapi import HTTPException
from fastapi.security import HTTPAuthorizationCredentials
from jwt import (
    InvalidAudienceError,
    InvalidIssuerError,
    InvalidTokenError,
    PyJWKClientError,
)

from app import dependencies
from app.config import Settings

TENANT_ID = "c7ebcc7f-fc6b-4674-a3d5-8fbc419561a8"
SUBDOMAIN = "eundunhealthciam"
BACKEND_CLIENT_ID = "903bf44d-d73a-40b5-9601-e9c362699c38"

# 실측값(설계 F4-a): issuer 의 서브도메인은 친숙한 이름이 아니라 tenantId 다.
ISSUER = f"https://{TENANT_ID}.ciamlogin.com/{TENANT_ID}/v2.0"
JWKS_URI = f"https://{SUBDOMAIN}.ciamlogin.com/{TENANT_ID}/discovery/v2.0/keys"


def _settings() -> Settings:
    return Settings(
        database_url="sqlite+aiosqlite:///:memory:",
        entra_tenant_id=TENANT_ID,
        entra_subdomain=SUBDOMAIN,
        entra_backend_client_id=BACKEND_CLIENT_ID,
        entra_backend_client_secret="test-secret",
    )


def _creds() -> HTTPAuthorizationCredentials:
    return HTTPAuthorizationCredentials(scheme="Bearer", credentials="dummy.jwt.token")


def _raise(exc: Exception):
    raise exc


def _valid_payload(**overrides):
    payload = {
        "iss": ISSUER,
        "aud": BACKEND_CLIENT_ID,
        "oid": "d2540ae9-916a-465d-b0ed-f364a767ed23",
        "sub": "jGuRLV4x0jrHaV8h7jf4S-L71Tw5YrxJB-ASjeQH21Q",  # oid 와 다르다(pairwise)
        "scp": "access_as_user",
    }
    payload.update(overrides)
    return payload


class _FakeJwk:
    def __init__(self, exc: Exception | None = None):
        self._exc = exc

    def get_signing_key_from_jwt(self, token):
        if self._exc:
            raise self._exc
        return type("K", (), {"key": "fake-key"})()


@pytest.fixture(autouse=True)
def _reset_module_caches(monkeypatch):
    """모듈 전역 캐시(discovery/JWKS)를 테스트마다 격리."""
    monkeypatch.setattr(dependencies, "_oidc_config", None)
    monkeypatch.setattr(dependencies, "_jwk_client", None)
    monkeypatch.setattr(
        dependencies,
        "_get_oidc_config",
        lambda subdomain: {"issuer": ISSUER, "jwks_uri": JWKS_URI},
    )


@pytest.mark.asyncio
async def test_valid_token_returns_oid_not_sub(monkeypatch):
    """DB 키는 oid 다. sub 를 반환하면 Graph 계정 삭제가 매칭되지 않는다(설계 F1)."""
    monkeypatch.setattr(dependencies, "_get_jwk_client", lambda uri: _FakeJwk())
    monkeypatch.setattr(dependencies.jwt, "decode", lambda *a, **k: _valid_payload())

    result = await dependencies.get_current_user_id(_creds(), _settings())

    assert result == "d2540ae9-916a-465d-b0ed-f364a767ed23"


@pytest.mark.asyncio
async def test_decode_is_called_with_discovery_issuer_and_rs256(monkeypatch):
    """검증 인자 계약 고정.

    issuer 를 문자열로 조합하면(초안 오류, 설계 F4-a) 서명·audience 는 통과하고
    issuer 에서만 어긋나 전 API 401 이 된다. 추적이 매우 어려우므로 여기서 박제한다.
    """
    captured: dict = {}

    def _capture_decode(token, key, **kwargs):
        captured.update(kwargs)
        return _valid_payload()

    monkeypatch.setattr(dependencies, "_get_jwk_client", lambda uri: _FakeJwk())
    monkeypatch.setattr(dependencies.jwt, "decode", _capture_decode)

    await dependencies.get_current_user_id(_creds(), _settings())

    assert captured["algorithms"] == ["RS256"]
    assert captured["audience"] == BACKEND_CLIENT_ID
    assert captured["issuer"] == ISSUER
    # 조합식이었다면 친숙한 서브도메인이 들어갔을 것이다.
    assert not captured["issuer"].startswith(f"https://{SUBDOMAIN}.")


@pytest.mark.asyncio
async def test_jwks_uri_comes_from_discovery(monkeypatch):
    """JWKS URL 도 조합하지 않고 discovery 의 jwks_uri 를 쓴다."""
    seen: dict = {}

    def _capture_client(uri):
        seen["uri"] = uri
        return _FakeJwk()

    monkeypatch.setattr(dependencies, "_get_jwk_client", _capture_client)
    monkeypatch.setattr(dependencies.jwt, "decode", lambda *a, **k: _valid_payload())

    await dependencies.get_current_user_id(_creds(), _settings())

    assert seen["uri"] == JWKS_URI


@pytest.mark.asyncio
async def test_missing_oid_returns_401(monkeypatch):
    """oid 부재의 가장 흔한 원인은 공격이 아니라 클라이언트의 profile scope 누락이다."""
    monkeypatch.setattr(dependencies, "_get_jwk_client", lambda uri: _FakeJwk())
    payload = _valid_payload()
    del payload["oid"]
    monkeypatch.setattr(dependencies.jwt, "decode", lambda *a, **k: payload)

    with pytest.raises(HTTPException) as ei:
        await dependencies.get_current_user_id(_creds(), _settings())
    assert ei.value.status_code == 401


@pytest.mark.asyncio
async def test_missing_required_scope_returns_401(monkeypatch):
    """설계 F1-b — 공식 권장. app-only 토큰(scp 대신 roles) 차단 효과도 있다."""
    monkeypatch.setattr(dependencies, "_get_jwk_client", lambda uri: _FakeJwk())
    payload = _valid_payload()
    del payload["scp"]
    payload["roles"] = ["SomeAppRole"]
    monkeypatch.setattr(dependencies.jwt, "decode", lambda *a, **k: payload)

    with pytest.raises(HTTPException) as ei:
        await dependencies.get_current_user_id(_creds(), _settings())
    assert ei.value.status_code == 401


@pytest.mark.asyncio
async def test_unrelated_scope_returns_401(monkeypatch):
    monkeypatch.setattr(dependencies, "_get_jwk_client", lambda uri: _FakeJwk())
    monkeypatch.setattr(
        dependencies.jwt, "decode", lambda *a, **k: _valid_payload(scp="User.Read other.scope")
    )

    with pytest.raises(HTTPException) as ei:
        await dependencies.get_current_user_id(_creds(), _settings())
    assert ei.value.status_code == 401


@pytest.mark.asyncio
async def test_scope_matched_among_multiple(monkeypatch):
    """scp 는 공백 구분 목록이다. 부분 문자열 매칭이 아니라 토큰 단위여야 한다."""
    monkeypatch.setattr(dependencies, "_get_jwk_client", lambda uri: _FakeJwk())
    monkeypatch.setattr(
        dependencies.jwt, "decode", lambda *a, **k: _valid_payload(scp="openid access_as_user")
    )

    assert await dependencies.get_current_user_id(_creds(), _settings())


@pytest.mark.asyncio
async def test_issuer_mismatch_returns_401(monkeypatch):
    """Entra 는 발급자 URL 패턴을 테넌트 간 공유한다 — 미검증 시 타 테넌트 토큰이 통과한다."""
    monkeypatch.setattr(dependencies, "_get_jwk_client", lambda uri: _FakeJwk())
    monkeypatch.setattr(
        dependencies.jwt, "decode", lambda *a, **k: _raise(InvalidIssuerError("wrong issuer"))
    )

    with pytest.raises(HTTPException) as ei:
        await dependencies.get_current_user_id(_creds(), _settings())
    assert ei.value.status_code == 401


@pytest.mark.asyncio
async def test_audience_mismatch_returns_401(monkeypatch):
    monkeypatch.setattr(dependencies, "_get_jwk_client", lambda uri: _FakeJwk())
    monkeypatch.setattr(
        dependencies.jwt, "decode", lambda *a, **k: _raise(InvalidAudienceError("wrong aud"))
    )

    with pytest.raises(HTTPException) as ei:
        await dependencies.get_current_user_id(_creds(), _settings())
    assert ei.value.status_code == 401


@pytest.mark.asyncio
async def test_invalid_token_returns_401(monkeypatch):
    monkeypatch.setattr(dependencies, "_get_jwk_client", lambda uri: _FakeJwk())
    monkeypatch.setattr(dependencies.jwt, "decode", lambda *a, **k: _raise(InvalidTokenError()))

    with pytest.raises(HTTPException) as ei:
        await dependencies.get_current_user_id(_creds(), _settings())
    assert ei.value.status_code == 401


@pytest.mark.asyncio
async def test_jwks_client_error_returns_503_not_401(monkeypatch):
    monkeypatch.setattr(
        dependencies, "_get_jwk_client", lambda uri: _FakeJwk(PyJWKClientError("jwks down"))
    )

    with pytest.raises(HTTPException) as ei:
        await dependencies.get_current_user_id(_creds(), _settings())
    assert ei.value.status_code == 503


@pytest.mark.asyncio
async def test_discovery_failure_returns_503_not_401(monkeypatch):
    """discovery 조회 실패는 인증 실패가 아니라 인증 서버 장애다."""
    monkeypatch.setattr(
        dependencies,
        "_get_oidc_config",
        lambda subdomain: _raise(PyJWKClientError("OIDC discovery failed")),
    )

    with pytest.raises(HTTPException) as ei:
        await dependencies.get_current_user_id(_creds(), _settings())
    assert ei.value.status_code == 503


@pytest.mark.asyncio
async def test_unexpected_error_propagates_not_401(monkeypatch):
    monkeypatch.setattr(dependencies, "_get_jwk_client", lambda uri: _FakeJwk(RuntimeError("bug")))

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

    monkeypatch.setattr(dependencies, "_get_jwk_client", lambda uri: _ThreadCapturingJwk())
    monkeypatch.setattr(dependencies.jwt, "decode", lambda *a, **k: _valid_payload())

    result = await dependencies.get_current_user_id(_creds(), _settings())

    assert result == "d2540ae9-916a-465d-b0ed-f364a767ed23"
    assert captured["thread"] != main_thread  # 워커 스레드 = 루프 블로킹 안 함


def test_jwk_client_uses_short_timeout():
    """느린 JWKS 가 워커를 30초간 점유하지 못하게 기본 30s → 5s."""
    client = dependencies._get_jwk_client(JWKS_URI)
    assert client.timeout == 5
