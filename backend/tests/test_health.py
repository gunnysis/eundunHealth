from pathlib import Path

import pytest
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from app.main import app
from app.routers.health import get_session_factory


def test_dockerfile_security_hardening_invariants():
    dockerfile = (Path(__file__).resolve().parents[1] / "Dockerfile").read_text(encoding="utf-8")
    assert "FROM python:3.14-slim@sha256:" in dockerfile
    assert "apt-get update" in dockerfile and "apt-get upgrade -y" in dockerfile
    assert "rm -rf /var/lib/apt/lists/*" in dockerfile
    assert "pip uninstall -y pip setuptools wheel" in dockerfile
    assert "USER appuser" in dockerfile


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

    app.dependency_overrides[get_session_factory] = lambda: lambda: _BoomSession()
    resp = await client.get("/health/ready")
    assert resp.status_code == 503
    assert resp.json() == {"status": "not ready"}
