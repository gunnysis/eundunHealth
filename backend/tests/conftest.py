import os

# app.main 모듈이 import 시점에 get_settings()를 호출(CORS 미들웨어 등록)하므로
# pydantic-settings의 필수 필드를 환경변수로 미리 채워둔다. dependency_overrides는
# 라우터 레벨 의존성만 갈아끼우므로 import-time 호출은 영향받지 않는다.
os.environ.setdefault("DATABASE_URL", "sqlite+aiosqlite:///:memory:")
os.environ.setdefault("ENTRA_TENANT_ID", "c7ebcc7f-fc6b-4674-a3d5-8fbc419561a8")
os.environ.setdefault("ENTRA_SUBDOMAIN", "eundunhealthciam")
os.environ.setdefault("ENTRA_BACKEND_CLIENT_ID", "903bf44d-d73a-40b5-9601-e9c362699c38")
os.environ.setdefault("ENTRA_BACKEND_CLIENT_SECRET", "test-client-secret")

from contextlib import contextmanager  # noqa: E402
from unittest.mock import AsyncMock, patch  # noqa: E402

import httpx  # noqa: E402
import pytest  # noqa: E402
import pytest_asyncio  # noqa: E402
from httpx import ASGITransport, AsyncClient  # noqa: E402
from sqlalchemy.ext.asyncio import (  # noqa: E402
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)

from app.config import Settings, get_settings  # noqa: E402
from app.database import Base, get_db  # noqa: E402
from app.dependencies import get_current_user_id  # noqa: E402
from app.main import app  # noqa: E402

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


# === Auth helpers ===

def set_test_user(user_id: str) -> None:
    """런타임에 get_current_user_id override를 교체한다.

    user isolation 테스트처럼 한 테스트 안에서 사용자 컨텍스트를 바꿔야 할 때 사용.
    `client` fixture가 이미 활성화된 상태여야 한다.
    """
    async def _override() -> str:
        return user_id

    app.dependency_overrides[get_current_user_id] = _override


@pytest_asyncio.fixture
async def client_no_auth(db_engine):
    """`client`와 동일하지만 get_current_user_id를 override하지 않는다.

    HTTPBearer가 Authorization 헤더 부재 시 403을 반환하는 경로를 검증할 때 사용.
    """
    session_factory = async_sessionmaker(db_engine, class_=AsyncSession, expire_on_commit=False)

    async def override_get_db():
        async with session_factory() as session:
            yield session
            await session.commit()

    def override_get_settings() -> Settings:
        return Settings(
            database_url="sqlite+aiosqlite:///:memory:",
        )

    app.dependency_overrides[get_db] = override_get_db
    app.dependency_overrides[get_settings] = override_get_settings
    # NOTE: get_current_user_id는 intentionally override하지 않음

    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
        yield ac
    app.dependency_overrides.clear()


# === Microsoft Graph mock ===


def graph_token_response() -> httpx.Response:
    """client credentials 토큰 응답 스텁. Graph 를 부르는 모든 경로가 먼저 이걸 통과한다."""
    return httpx.Response(
        200,
        json={"access_token": "fake-graph-token", "expires_in": 3599},
        request=httpx.Request("POST", "http://mock/token"),
    )


@pytest.fixture
def entra_delete_mock():
    """app.services.account_service.httpx.AsyncClient 를 패치해 Graph 응답을 흉내낸다.

    토큰 발급(POST) + 사용자 삭제(DELETE /users/{id}) + 영구 파기
    (DELETE /directory/deletedItems/{id}) 를 모두 덮는다.

    사용법:
        with entra_delete_mock(status_code=204):
            await client.delete("/account")
    """

    @contextmanager
    def _mock(status_code: int = 204):
        # Graph 의 삭제 성공은 200 이 아니라 204 다(Supabase 와 다름).
        mock_response = httpx.Response(
            status_code, request=httpx.Request("DELETE", "http://mock")
        )
        with patch("app.services.account_service.httpx.AsyncClient") as mock_cls:
            instance = AsyncMock()
            instance.post = AsyncMock(return_value=graph_token_response())
            instance.delete = AsyncMock(return_value=mock_response)
            mock_cls.return_value = instance
            instance.__aenter__ = AsyncMock(return_value=instance)
            instance.__aexit__ = AsyncMock(return_value=False)
            yield instance

    return _mock
