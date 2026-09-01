import asyncio

import httpx
import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from jwt import InvalidTokenError, PyJWKClient, PyJWKClientError

from app.config import Settings, get_settings

security = HTTPBearer()

# 이 API 가 노출하는 유일한 delegated scope. 토큰의 scp 에 반드시 포함돼야 한다.
REQUIRED_SCOPE = "access_as_user"

# PyJWKClient 내장 TTL 캐시 사용 (24시간)
_jwk_client: PyJWKClient | None = None

# OIDC discovery 문서 캐시 (issuer · jwks_uri)
_oidc_config: dict[str, str] | None = None


def _get_oidc_config(subdomain: str) -> dict[str, str]:
    """OIDC discovery 문서에서 issuer 와 jwks_uri 를 읽어 캐시한다.

    **문자열로 조합하지 않는다.** Entra External ID 는 jwks_uri 에는 친숙한 서브도메인을
    쓰면서 issuer 에는 tenantId 를 서브도메인으로 쓴다. 조합식으로 만들면 서명과 audience 는
    통과하고 issuer 에서만 어긋나 전 API 가 401 이 되는데, 그 원인을 찾기가 매우 어렵다.
    실제로 초안이 이 오류를 담고 있었다(설계 F4-a).
    """
    global _oidc_config
    if _oidc_config is None:
        url = (
            f"https://{subdomain}.ciamlogin.com/"
            f"{subdomain}.onmicrosoft.com/v2.0/.well-known/openid-configuration"
        )
        try:
            resp = httpx.get(url, timeout=5)
            resp.raise_for_status()
            doc = resp.json()
            _oidc_config = {"issuer": doc["issuer"], "jwks_uri": doc["jwks_uri"]}
        except (httpx.HTTPError, ValueError, KeyError) as e:
            # 인증 실패(401)가 아니라 인증 서버 장애(503)로 다뤄야 한다.
            raise PyJWKClientError(f"OIDC discovery 실패: {e}") from e
    return _oidc_config


def _get_jwk_client(jwks_uri: str) -> PyJWKClient:
    global _jwk_client
    if _jwk_client is None:
        _jwk_client = PyJWKClient(
            jwks_uri,
            cache_keys=True,
            lifespan=86400,  # 24시간 TTL
            timeout=5,  # 기본 30s → 5s: 느린 JWKS 가 워커 스레드를 오래 점유하지 못하게
        )
    return _jwk_client


async def get_current_user_id(
    credentials: HTTPAuthorizationCredentials = Depends(security),
    settings: Settings = Depends(get_settings),
) -> str:
    """Bearer JWT 를 JWKS(RS256)로 검증하고 Entra 사용자 ID(oid) 를 반환한다.

    `sub` 가 아니라 `oid` 다. `sub` 는 앱마다 달라지는 pairwise 식별자라 이것을 저장하면
    Microsoft Graph 의 사용자 삭제가 매칭되지 않아 **로그인은 되는데 계정 삭제만 조용히
    실패**한다(설계 F1).
    """
    try:
        # 캐시 적중 시엔 스레드 홉 없이 즉시 반환. 최초 1회만 워커로 오프로드한다.
        cfg = _oidc_config
        if cfg is None:
            cfg = await asyncio.to_thread(_get_oidc_config, settings.entra_subdomain)
        jwk_client = _get_jwk_client(cfg["jwks_uri"])
        # 동기 urllib 호출을 워커 스레드로 오프로드해 이벤트 루프 블로킹 방지(콜드스타트·키 로테이션
        # 시에만 실제 fetch — 24h 캐시 적중 시엔 네트워크 없음). 워커 예외는 그대로 전파된다.
        signing_key = await asyncio.to_thread(jwk_client.get_signing_key_from_jwt, credentials.credentials)
        payload = jwt.decode(
            credentials.credentials,
            signing_key.key,
            algorithms=["RS256"],
            audience=settings.entra_backend_client_id,
            issuer=cfg["issuer"],
        )
        # scp 검증은 공식 권장 사항이다(설계 F1-b). 부수 효과로 app-only 토큰도 막힌다 —
        # client credentials 토큰은 scp 대신 roles 를 갖는다.
        if REQUIRED_SCOPE not in (payload.get("scp") or "").split():
            raise InvalidTokenError("missing required scope")
        # oid 부재의 가장 흔한 원인은 공격이 아니라 클라이언트의 profile scope 누락이다.
        user_id = payload.get("oid")
        if user_id is None:
            raise InvalidTokenError("missing oid claim")
        return str(user_id)
    except InvalidTokenError:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="인증 실패")
    except PyJWKClientError as e:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="인증 서버 일시 오류") from e
    # 그 외(코드버그 등)는 전역 핸들러로 전파 → 500 + Sentry
