from contextlib import contextmanager
from unittest.mock import AsyncMock, patch

import httpx
import pytest
import pytest_asyncio
from httpx import ASGITransport, AsyncClient
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

from app.config import Settings, get_settings
from app.database import Base, get_db
from app.dependencies import get_current_user_id
from app.main import app

TEST_DB_URL = "sqlite+aiosqlite:///:memory:"


@pytest_asyncio.fixture(scope="function")
async def db_engine():
    engine = create_async_engine(TEST_DB_URL)
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    yield engine
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)
    await engine.dispose()


@pytest_asyncio.fixture
async def client(db_engine):
    session_factory = async_sessionmaker(db_engine, class_=AsyncSession, expire_on_commit=False)

    async def override_get_db():
        async with session_factory() as session:
            yield session
            await session.commit()

    async def override_get_current_user_id() -> str:
        return "test-user-id"

    def override_get_settings() -> Settings:
        return Settings(
            database_url="sqlite+aiosqlite:///:memory:",
            supabase_url="https://test.supabase.co",
            supabase_service_role_key="test-service-role-key",
        )

    app.dependency_overrides[get_db] = override_get_db
    app.dependency_overrides[get_current_user_id] = override_get_current_user_id
    app.dependency_overrides[get_settings] = override_get_settings

    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
        yield ac
    app.dependency_overrides.clear()


# === Shared payload fixtures ===

@pytest.fixture
def sample_profile() -> dict:
    """대표 프로필 페이로드 — heightCm/weightKg 외 선택 필드는 미포함."""
    return {"heightCm": 175.0, "weightKg": 70.0}


@pytest.fixture
def sample_plan() -> dict:
    """주간 운동 계획 페이로드 — week_start와 day_plans만 가진 최소형."""
    return {"weekStart": "2026-05-25", "dayPlans": '[{"day":1,"exercises":[]}]'}


# === Supabase Admin API mock ===

@pytest.fixture
def supabase_delete_mock():
    """app.services.account_service.httpx.AsyncClient를 패치해서
    DELETE /auth/v1/admin/users/{id} 응답을 흉내내는 컨텍스트 매니저를 반환한다.

    사용법:
        with supabase_delete_mock(status_code=200):
            await client.delete("/account")
    """

    @contextmanager
    def _mock(status_code: int = 200):
        mock_response = httpx.Response(
            status_code, request=httpx.Request("DELETE", "http://mock")
        )
        with patch("app.services.account_service.httpx.AsyncClient") as mock_cls:
            instance = AsyncMock()
            instance.delete = AsyncMock(return_value=mock_response)
            mock_cls.return_value = instance
            instance.__aenter__ = AsyncMock(return_value=instance)
            instance.__aexit__ = AsyncMock(return_value=False)
            yield instance

    return _mock
