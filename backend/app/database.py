from collections.abc import AsyncIterator
from typing import Any

from fastapi import Request
from sqlalchemy import MetaData, event, text
from sqlalchemy.engine import Connection, Engine
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import DeclarativeBase

POSTGRES_NAMING_CONVENTION: dict[str, str] = {
    "ix": "ix_%(column_0_label)s",
    "uq": "uq_%(table_name)s_%(column_0_name)s",
    "ck": "ck_%(table_name)s_%(constraint_name)s",
    "fk": "fk_%(table_name)s_%(column_0_name)s_%(referred_table_name)s",
    "pk": "pk_%(table_name)s",
}

_PG_UUIDV7_TABLES: tuple[str, ...] = (
    "user_profiles",
    "user_profile_history",
    "weekly_plans",
    "goals",
    "badges",
    "meal_plans",
    "meal_plan_items",
    "smile_training_sessions",
)


class Base(DeclarativeBase):
    """SQLAlchemy 2.0 DeclarativeBase — 모든 ORM 모델의 단일 메타데이터 루트."""

    metadata = MetaData(naming_convention=POSTGRES_NAMING_CONVENTION)


@event.listens_for(Engine, "connect")
def _set_sqlite_pragma(dbapi_connection: Any, _connection_record: Any) -> None:
    """SQLite(단위 테스트) 연결에서도 PostgreSQL과 동일하게 FK ON DELETE CASCADE를 강제한다."""
    module_name = type(dbapi_connection).__module__.lower()
    if "sqlite" in module_name or "aiosqlite" in module_name:
        cursor = dbapi_connection.cursor()
        cursor.execute("PRAGMA foreign_keys=ON")
        cursor.close()


@event.listens_for(Base.metadata, "after_create")
def _set_pg_uuidv7_server_defaults(_target: Any, connection: Connection, **_kw: Any) -> None:
    """PostgreSQL 18에서 Base.metadata.create_all 호출 시 8개 도메인 테이블 id에 DEFAULT uuidv7()을 보장한다."""
    if connection.dialect.name != "postgresql":
        return
    for table_name in _PG_UUIDV7_TABLES:
        connection.execute(text(f"ALTER TABLE {table_name} ALTER COLUMN id SET DEFAULT uuidv7()"))


def build_engine_kwargs(
    database_url: str,
    *,
    pool_size: int = 3,
    max_overflow: int = 2,
) -> dict[str, Any]:
    """Azure PostgreSQL Standard_B1ms (max_connections=50) 맞춤형 SQLAlchemy 엔진 옵션을 생성한다."""
    if database_url.startswith("sqlite"):
        return {"pool_pre_ping": True}
    return {
        "pool_size": pool_size,
        "max_overflow": max_overflow,
        "pool_pre_ping": True,
        "pool_recycle": 1800,
        "pool_timeout": 30,
        "connect_args": {"command_timeout": 30},
    }


# UoW 패턴 — 세션 단위 트랜잭션
# 엔진/세션팩토리는 lifespan에서 1회 생성 → app.state에 저장
# get_db는 Request를 통해 app.state.session_factory 참조
async def get_db(request: Request) -> AsyncIterator[AsyncSession]:
    """FastAPI dependency — request-scoped DB session 제공. UoW 패턴."""
    async with request.app.state.session_factory() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise
