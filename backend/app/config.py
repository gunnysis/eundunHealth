from functools import lru_cache

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """pydantic-settings 기반 앱 설정. 환경변수 / .env 파일에서 자동 로드."""

    database_url: str  # postgresql+asyncpg://...
    supabase_url: str  # https://xxx.supabase.co
    supabase_service_role_key: str  # Supabase Admin API용 (회원 탈퇴)
    sentry_dsn: str = ""
    environment: str = "production"
    cors_origins: list[str] = ["*"]  # 프로덕션에서는 앱 도메인만 허용

    model_config = {"env_file": ".env"}


@lru_cache
def get_settings() -> Settings:
    """Return Settings 인스턴스. lru_cache 싱글턴 — FastAPI Depends 용."""
    return Settings()
