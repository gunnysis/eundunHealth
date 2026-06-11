import pytest
import pytest_asyncio
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

from app.database import Base
from app.repositories.goal_repo import GoalRepository

TEST_DB_URL = "sqlite+aiosqlite:///:memory:"


@pytest_asyncio.fixture
async def session():
    engine = create_async_engine(TEST_DB_URL)
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    factory = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)
    async with factory() as s:
        yield s
    await engine.dispose()


@pytest.mark.asyncio
async def test_upsert_new_goal_has_server_created_at(session):
    repo = GoalRepository(session)
    goal = await repo.upsert("u1", "weight", 70.0)
    # flush+refresh 로 server_default(created_at)가 commit 전에 채워져야 한다
    assert goal.created_at is not None
