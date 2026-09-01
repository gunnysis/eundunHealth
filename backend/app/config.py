from functools import lru_cache

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """pydantic-settings 기반 앱 설정. 환경변수 / .env 파일에서 자동 로드."""

    database_url: str  # postgresql+asyncpg://...

    # Microsoft Entra External ID (외부 테넌트)
    entra_tenant_id: str  # 테넌트 GUID — 토큰 tid claim 과 일치
    entra_subdomain: str  # {subdomain}.ciamlogin.com — discovery 문서 조회에만 쓴다
    entra_backend_client_id: str  # 백엔드 앱 등록 appId = 액세스 토큰의 audience
    entra_backend_client_secret: str  # Graph client credentials 용 (회원 탈퇴)

    sentry_dsn: str = ""
    environment: str = "production"
    cors_origins: list[str] = []  # 기본 차단 — 네이티브 앱은 CORS 비적용. 웹 표면 필요 시 CORS_ORIGINS 로 명시

    model_config = {"env_file": ".env"}


@lru_cache
def get_settings() -> Settings:
    """Return Settings 인스턴스. lru_cache 싱글턴 — FastAPI Depends 용."""
    return Settings()
