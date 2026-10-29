"""컨테이너 기동 시 멱등 스키마 부트스트랩(비파괴적 Base.metadata.create_all).

일반 컨테이너 재시작·스케일아웃 시에는 데이터를 절대 삭제하지 않고 테이블 존재만 보장한다.
다중 복제본 동시 기동 경합을 막기 위해 PostgreSQL에서는 transaction advisory lock으로 직렬화한다.
데이터 초기화(Drop & Recreate 하드 리셋)는 오직 `scripts/hard_reset_db.py` 로만 수행한다.
"""

from __future__ import annotations

import asyncio
import logging
import os

from sqlalchemy import text
from sqlalchemy.ext.asyncio import create_async_engine

from app.config import get_settings
from app.models import Base

logger = logging.getLogger(__name__)
BOOTSTRAP_ADVISORY_LOCK_KEY = 918273645


async def ensure_schema(database_url: str | None = None) -> None:
    """현재 연결된 데이터베이스에 9개 정규 테이블이 존재하도록 멱등 생성한다."""
    url = database_url or os.environ.get("DATABASE_URL") or get_settings().database_url
    engine = create_async_engine(url, pool_pre_ping=True)
    try:
        async with engine.begin() as conn:
            if engine.dialect.name == "postgresql":
                await conn.execute(text("SET LOCAL lock_timeout = '15s'"))
                await conn.execute(text(f"SELECT pg_advisory_xact_lock({BOOTSTRAP_ADVISORY_LOCK_KEY})"))
            await conn.run_sync(Base.metadata.create_all)
        logger.info("Schema bootstrap complete (tables=%d).", len(Base.metadata.tables))
    finally:
        await engine.dispose()


def main() -> int:
    """CLI 진입점 — 현재 DATABASE_URL 대상 멱등 스키마 부트스트랩 실행."""
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
    asyncio.run(ensure_schema())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
