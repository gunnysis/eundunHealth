import asyncio

import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from jwt import InvalidTokenError, PyJWKClient, PyJWKClientError

from app.config import Settings, get_settings

security = HTTPBearer()

# PyJWKClient 내장 TTL 캐시 사용 (24시간)
_jwk_client: PyJWKClient | None = None


def _get_jwk_client(supabase_url: str) -> PyJWKClient:
    global _jwk_client
    if _jwk_client is None:
        _jwk_client = PyJWKClient(
            f"{supabase_url}/auth/v1/.well-known/jwks.json",
            cache_keys=True,
            lifespan=86400,  # 24시간 TTL
            timeout=5,  # 기본 30s → 5s: 느린 JWKS 가 워커 스레드를 오래 점유하지 못하게
        )
    return _jwk_client


async def get_current_user_id(
    credentials: HTTPAuthorizationCredentials = Depends(security),
    settings: Settings = Depends(get_settings),
) -> str:
    """Bearer JWT 를 JWKS(ES256)로 검증하고 Supabase user_id(sub) 반환."""
    try:
        jwk_client = _get_jwk_client(settings.supabase_url)
        # 동기 urllib 호출을 워커 스레드로 오프로드해 이벤트 루프 블로킹 방지(콜드스타트·키 로테이션
        # 시에만 실제 fetch — 24h 캐시 적중 시엔 네트워크 없음). 워커 예외는 그대로 전파된다.
        signing_key = await asyncio.to_thread(jwk_client.get_signing_key_from_jwt, credentials.credentials)
        payload = jwt.decode(
            credentials.credentials,
            signing_key.key,
            algorithms=["ES256"],
            audience="authenticated",
        )
        user_id = payload.get("sub")
        if user_id is None:
            raise InvalidTokenError("missing sub claim")
        return str(user_id)
    except InvalidTokenError:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="인증 실패")
    except PyJWKClientError as e:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="인증 서버 일시 오류") from e
    # 그 외(코드버그 등)는 전역 핸들러로 전파 → 500 + Sentry
