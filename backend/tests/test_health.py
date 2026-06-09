import pytest
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from app.main import app
from app.routers.health import get_session_factory


@pytest.mark.asyncio
async def test_health(client):
    resp = await client.get("/health")
    assert resp.status_code == 200
    assert resp.json() == {"status": "ok"}


@pytest.mark.asyncio
async def test_health_ready_ok(client, db_engine):
    factory = async_sessionmaker(db_engine, class_=AsyncSession, expire_on_commit=False)
    app.dependency_overrides[get_session_factory] = lambda: factory
    resp = await client.get("/health/ready")
    assert resp.status_code == 200
    assert resp.json() == {"status": "ready"}


@pytest.mark.asyncio
async def test_health_ready_db_down_returns_503(client):
    class _BoomSession:
        async def __aenter__(self):
            return self

        async def __aexit__(self, *exc):
            return False

        async def execute(self, _stmt):
            raise RuntimeError("db down")

    app.dependency_overrides[get_session_factory] = lambda: (lambda: _BoomSession())
    resp = await client.get("/health/ready")
    assert resp.status_code == 503
    assert resp.json() == {"status": "not ready"}
