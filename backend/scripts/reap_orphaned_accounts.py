"""고아 앱 데이터 정리 잡 — Supabase Auth 에 없는 user_id 의 DB 데이터를 purge.

배경: account_service.delete_account 는 Supabase Auth 삭제(Step 1) 성공 후 앱 DB purge
(Step 2)를 한다. Step 2 가 실패하면 Auth 엔 없고 DB 엔 남은 고아 데이터가 생긴다(로깅됨,
UoW 롤백으로 부분삭제는 없음). 이 잡이 그 고아를 주기적으로 청소하는 안전망이다.

실행(backend/ 에서) — 둘 다 동작(self-locating: 스크립트가 backend 루트를 sys.path 에 추가):
    python -m scripts.reap_orphaned_accounts      # 권장(모듈 형태)
    python scripts/reap_orphaned_accounts.py      # 직접 실행도 OK
Container Apps Job 의 command 는 `python scripts/reap_orphaned_accounts.py`
(self-locating 이라 동작 + az CLI 의 `--args -m ...` 플래그 오인 회피).

운영: Container Apps Job(cron) 으로 주기 실행 — `scripts/setup-reaper-job.sh` 로 프로비저닝.
fail-safe: Auth 존재 확인이 404(확정 부재)일 때만 purge(account_service.reap_orphaned_data).
"""

import asyncio
import logging
import sys
from pathlib import Path

from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

# backend/ 루트를 sys.path 에 추가 → `python scripts/x.py`(sys.path[0]=scripts/)에서도
# `import app` 가 되게 한다(self-locating). `-m scripts.reap_orphaned_accounts` 도 그대로 동작.
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.config import get_settings  # noqa: E402
from app.services.account_service import AccountService  # noqa: E402

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
logger = logging.getLogger("reap_orphans")


async def main() -> None:
    """엔진/세션을 만들어 reap_orphaned_data 를 실행하고 결과를 로깅한다."""
    settings = get_settings()
    engine = create_async_engine(settings.database_url)
    session_factory = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)
    try:
        async with session_factory() as session:
            service = AccountService(session, settings)
            # reap_orphaned_data 가 orphan 마다 자체 commit(사용자 단위 트랜잭션) → 여기서 추가 commit 불필요.
            reaped = await service.reap_orphaned_data()
        logger.info("Orphan reaper 완료 — purged %d user(s): %s", len(reaped), reaped)
    finally:
        await engine.dispose()


if __name__ == "__main__":
    asyncio.run(main())
