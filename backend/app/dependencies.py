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
        )
    return _jwk_client


async def get_current_user_id(
    credentials: HTTPAuthorizationCredentials = Depends(security),
    settings: Settings = Depends(get_settings),
) -> str:
    """Bearer JWT 를 JWKS(ES256)로 검증하고 Supabase user_id(sub) 반환."""
    try:
        jwk_client = _get_jwk_client(settings.supabase_url)
        signing_key = jwk_client.get_signing_key_from_jwt(credentials.credentials)
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
