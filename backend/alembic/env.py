import asyncio

from alembic import context
from sqlalchemy import text
from sqlalchemy.ext.asyncio import create_async_engine

from app.config import get_settings
from app.models import Base  # 모든 모델 import

target_metadata = Base.metadata


def do_run_migrations(connection):
    # Alembic 자체는 동시 upgrade를 직렬화하지 않는다. 운영 PostgreSQL에서는 session
    # transaction advisory lock으로 app/reaper/reset 이미지가 동시에 시작할 때 경쟁을 막는다.
    is_postgresql = connection.dialect.name == "postgresql"
    lock_key = 7_382_631_004
    context.configure(connection=connection, target_metadata=target_metadata)
    with context.begin_transaction():
        if is_postgresql:
            connection.execute(text("SELECT pg_advisory_xact_lock(:key)"), {"key": lock_key})
        context.run_migrations()


async def run_async_migrations():
    engine = create_async_engine(get_settings().database_url)
    async with engine.connect() as connection:
        await connection.run_sync(do_run_migrations)
    await engine.dispose()


def run_migrations_online():
    asyncio.run(run_async_migrations())


if context.is_offline_mode():
    raise NotImplementedError("Offline mode not supported with async engine")
else:
    run_migrations_online()
