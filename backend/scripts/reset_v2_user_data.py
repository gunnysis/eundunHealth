"""Confirmation-guarded, one-time reset for the v2 weekly-plan cutover."""

import argparse
import asyncio
import json
import re
import subprocess
import sys
from pathlib import Path

from sqlalchemy import inspect, select, text
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.config import get_settings  # noqa: E402
from app.models import Base  # noqa: E402
from app.models.data_reset_audit import DataResetAudit  # noqa: E402

EXPECTED_CONFIRMATION = "RESET_ALL_V2_USER_DATA"
RESET_LOCK_KEY = 7_382_631_005
RESET_TABLES = (
    "meal_plan_items",
    "meal_plans",
    "weekly_plans",
    "user_profile_history",
    "user_profiles",
    "goals",
    "badges",
    "smile_training_sessions",
)


def _parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true", help="Inspect row counts without truncating")
    parser.add_argument("--confirmation", default="")
    parser.add_argument("--reset-id", default="v2-dry-run")
    parser.add_argument("--image-sha", default="0000000")
    parser.add_argument("--execution-id", default="dry-run")
    return parser.parse_args()


def _validate_evidence(args: argparse.Namespace) -> None:
    if getattr(args, "dry_run", False):
        return
    if args.confirmation != EXPECTED_CONFIRMATION:
        raise ValueError("reset confirmation mismatch")
    if re.fullmatch(r"[0-9a-f]{7,40}", args.image_sha) is None:
        raise ValueError("image SHA must be a 7-40 character lowercase hexadecimal git SHA")
    if re.fullmatch(r"v2-[A-Za-z0-9][A-Za-z0-9._-]{2,127}", args.reset_id) is None:
        raise ValueError("reset id must be a v2- prefixed safe identifier")
    if re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,127}", args.execution_id) is None:
        raise ValueError("execution id has an invalid format")


async def reset_user_data(args: argparse.Namespace) -> dict[str, int]:
    """검증된 증적과 exact inventory로 application-owned user data를 한 번만 초기화한다."""
    _validate_evidence(args)
    dry_run = bool(getattr(args, "dry_run", False))

    engine = create_async_engine(get_settings().database_url)
    session_factory = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)
    try:
        async with engine.begin() as connection:
            actual_tables = set(await connection.run_sync(lambda conn: inspect(conn).get_table_names()))
        expected = set(RESET_TABLES)
        # 새 child table이 user_id 없이 추가돼도 reset 누락을 감지한다. audit table만 보존 대상이다.
        app_data_tables = set(Base.metadata.tables) - {DataResetAudit.__tablename__}
        if app_data_tables != expected:
            raise RuntimeError(
                f"app-data table inventory mismatch: expected={sorted(expected)} actual={sorted(app_data_tables)}"
            )
        missing = expected - actual_tables
        if missing:
            raise RuntimeError(f"reset tables missing from database: {sorted(missing)}")

        async with session_factory() as session, session.begin():
            if session.bind and session.bind.dialect.name == "postgresql":
                await session.execute(text("SELECT pg_advisory_xact_lock(:key)"), {"key": RESET_LOCK_KEY})
            existing = await session.scalar(select(DataResetAudit).limit(1))
            if existing is not None:
                raise RuntimeError(f"v2 reset already completed: {existing.reset_id}")

            counts: dict[str, int] = {}
            for table in RESET_TABLES:
                counts[table] = int(await session.scalar(text(f'SELECT count(*) FROM "{table}"')) or 0)

            if dry_run:
                return counts

            quoted = ", ".join(f'"{table}"' for table in RESET_TABLES)
            # RESTRICT(기본)를 유지한다. 미래 FK table이 allow-list 밖에 생기면 실패해야지
            # CASCADE로 조용히 함께 지워져서는 안 된다.
            await session.execute(text(f"TRUNCATE TABLE {quoted} RESTART IDENTITY"))
            remaining: dict[str, int] = {}
            for table in RESET_TABLES:
                remaining[table] = int(await session.scalar(text(f'SELECT count(*) FROM "{table}"')) or 0)
            if any(remaining.values()):
                raise RuntimeError(f"reset verification failed: {remaining}")
            session.add(
                DataResetAudit(
                    reset_id=args.reset_id,
                    image_sha=args.image_sha,
                    execution_id=args.execution_id,
                    row_counts_json=json.dumps(counts, sort_keys=True),
                )
            )
        return counts
    finally:
        await engine.dispose()


def main() -> None:
    """Migration을 head로 올린 뒤 guarded reset을 실행하고 비민감 요약을 출력한다."""
    args = _parse_args()
    _validate_evidence(args)
    # Job의 command override 여부와 무관하게 command 자체가 migration -> reset 순서를 보장한다.
    subprocess.run([sys.executable, "-m", "alembic", "upgrade", "head"], check=True)
    counts = asyncio.run(reset_user_data(args))
    key = "dryRunRows" if getattr(args, "dry_run", False) else "deletedRows"
    print(json.dumps({"resetId": args.reset_id, key: counts}, sort_keys=True))


if __name__ == "__main__":
    main()
