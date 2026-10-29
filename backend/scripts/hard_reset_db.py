"""PostgreSQL / SQLite 스키마 전면 하드 리셋(Drop & Recreate) 파이프라인 실행기.

점진적 마이그레이션(ALTER TABLE / Up-Down 스크립트)을 일절 사용하지 않고,
1) `backend/sql/01_cleanup_drop_all.sql` (전체 객체 DROP CASCADE)
2) `backend/sql/02_schema_create_all.sql` (신규 정규화 DDL 재생성)
3) `data_reset_audits` 감사 원장 기록
순서로 깨끗한 스키마를 재구축한다.
"""

from __future__ import annotations

import argparse
import asyncio
import datetime
import json
import os
import re
import sys
from pathlib import Path
from typing import Any

BACKEND_ROOT = Path(__file__).resolve().parents[1]
if str(BACKEND_ROOT) not in sys.path:
    sys.path.insert(0, str(BACKEND_ROOT))

from sqlalchemy import text  # noqa: E402
from sqlalchemy.ext.asyncio import AsyncEngine, create_async_engine  # noqa: E402

from app.models import Base  # noqa: E402
from app.models.data_reset_audit import DataResetAudit  # noqa: E402

EXPECTED_CONFIRMATION = "DROP_AND_RECREATE_ALL_SCHEMAS"
ADVISORY_LOCK_KEY = 918273645

SQL_DIR = BACKEND_ROOT / "sql"
CLEANUP_SQL_PATH = SQL_DIR / "01_cleanup_drop_all.sql"
CREATE_SQL_PATH = SQL_DIR / "02_schema_create_all.sql"

RESET_TABLES: tuple[str, ...] = (
    "meal_plan_items",
    "meal_plans",
    "smile_training_sessions",
    "badges",
    "goals",
    "weekly_plans",
    "user_profile_history",
    "user_profiles",
)

_RESET_ID_RE = re.compile(r"^hard-reset-[A-Za-z0-9._-]{3,100}$")
_IMAGE_SHA_RE = re.compile(r"^[0-9a-f]{7,64}$")
_EXECUTION_ID_RE = re.compile(r"^[A-Za-z0-9._:-]{3,128}$")


def _strip_sql_line_comment(line: str) -> str:
    """단일 따옴표 문자열 리터럴 바깥의 `--` 인라인 주석을 제거한다."""
    in_single_quote = False
    i = 0
    length = len(line)
    while i < length:
        ch = line[i]
        if ch == "'":
            in_single_quote = not in_single_quote
        elif ch == "-" and not in_single_quote and i + 1 < length and line[i + 1] == "-":
            return line[:i].rstrip()
        i += 1
    return line


def split_sql_statements(sql_text: str) -> list[str]:
    """주석을 제거하고 `DO $$ ... $$;` 블록을 보존하면서 개별 SQL 문장으로 분리한다."""
    lines: list[str] = []
    for raw_line in sql_text.splitlines():
        line = _strip_sql_line_comment(raw_line)
        if not line.strip():
            continue
        lines.append(line)
    cleaned = "\n".join(lines)

    statements: list[str] = []
    current: list[str] = []
    in_dollar_block = False

    for line in cleaned.splitlines():
        dollar_count = line.count("$$")
        if dollar_count % 2 == 1:
            in_dollar_block = not in_dollar_block
        current.append(line)
        if not in_dollar_block and line.rstrip().endswith(";"):
            stmt = "\n".join(current).strip()
            if stmt.endswith(";"):
                stmt = stmt[:-1].strip()
            if stmt:
                statements.append(stmt)
            current = []

    tail = "\n".join(current).strip()
    if tail:
        statements.append(tail)
    return statements


def validate_reset_args(args: argparse.Namespace) -> None:
    """하드 리셋 CLI 인자(확인 토큰·reset_id·image_sha·execution_id)를 검증한다."""
    if args.confirmation != EXPECTED_CONFIRMATION:
        raise ValueError(f"reset confirmation mismatch: expected '{EXPECTED_CONFIRMATION}', got '{args.confirmation}'")
    if not _RESET_ID_RE.match(args.reset_id):
        raise ValueError(f"invalid reset id '{args.reset_id}' (must match {_RESET_ID_RE.pattern})")
    if not _IMAGE_SHA_RE.match(args.image_sha):
        raise ValueError(f"invalid image SHA '{args.image_sha}' (must be 7..64 lowercase hex chars)")
    if not _EXECUTION_ID_RE.match(args.execution_id):
        raise ValueError(f"invalid execution id '{args.execution_id}'")


async def execute_hard_reset_on_engine(
    engine: AsyncEngine,
    *,
    reset_id: str = "hard-reset-2026-10-29-pg18",
    image_sha: str = "0000000",
    execution_id: str = "local-cli",
) -> dict[str, Any]:
    """주어진 AsyncEngine 상에서 Task 1(Cleanup) -> Task 2(Create DDL) 하드 리셋을 수행한다."""
    dialect = engine.dialect.name
    cleanup_sql = CLEANUP_SQL_PATH.read_text(encoding="utf-8")
    create_sql = CREATE_SQL_PATH.read_text(encoding="utf-8")

    async with engine.begin() as conn:
        if dialect == "postgresql":
            await conn.execute(text(f"SELECT pg_advisory_xact_lock({ADVISORY_LOCK_KEY})"))
            for stmt in split_sql_statements(cleanup_sql):
                await conn.execute(text(stmt))
            for stmt in split_sql_statements(create_sql):
                await conn.execute(text(stmt))
        else:
            await conn.run_sync(Base.metadata.drop_all)
            await conn.run_sync(Base.metadata.create_all)

        table_counts: dict[str, int] = {}
        for table_name in RESET_TABLES:
            cnt = await conn.scalar(text(f"SELECT count(*) FROM {table_name}"))  # nosec B608
            table_counts[table_name] = int(cnt or 0)

        now_utc = datetime.datetime.now(datetime.UTC)
        await conn.execute(
            DataResetAudit.__table__.insert().values(
                reset_id=reset_id,
                image_sha=image_sha,
                execution_id=execution_id,
                row_counts_json=json.dumps(table_counts, sort_keys=True),
                completed_at=now_utc,
            )
        )

    return {
        "dialect": dialect,
        "reset_id": reset_id,
        "tables_created": len(RESET_TABLES) + 1,
        "post_reset_counts": table_counts,
    }


async def _async_main(args: argparse.Namespace) -> int:
    validate_reset_args(args)
    db_url = args.database_url or os.environ.get("DATABASE_URL")
    if not db_url:
        raise RuntimeError("DATABASE_URL environment variable or --database-url is required.")

    engine = create_async_engine(db_url, pool_pre_ping=True)
    try:
        summary = await execute_hard_reset_on_engine(
            engine,
            reset_id=args.reset_id,
            image_sha=args.image_sha,
            execution_id=args.execution_id,
        )
        print(json.dumps(summary, ensure_ascii=False, indent=2))
        if args.seed_edge_cases:
            from scripts.seed_edge_cases import run_seeder_on_engine

            seed_report = await run_seeder_on_engine(
                engine,
                verify_constraints=args.verify_constraints,
            )
            print(json.dumps(seed_report, ensure_ascii=False, indent=2))
    finally:
        await engine.dispose()
    return 0


def build_parser() -> argparse.ArgumentParser:
    """하드 리셋 CLI 인자 파서를 생성한다."""
    parser = argparse.ArgumentParser(description="Hard Reset (Drop & Recreate) database schema.")
    parser.add_argument(
        "--confirmation",
        required=True,
        help=f"Must be exactly '{EXPECTED_CONFIRMATION}'",
    )
    parser.add_argument(
        "--reset-id",
        default="hard-reset-2026-10-29-pg18",
        help="Audit reset identifier (prefix 'hard-reset-')",
    )
    parser.add_argument(
        "--image-sha",
        default="8bbc7ca",
        help="Git commit SHA (7..64 lowercase hex)",
    )
    parser.add_argument(
        "--execution-id",
        default="local-hard-reset-cli",
        help="Execution context identifier",
    )
    parser.add_argument(
        "--database-url",
        default=None,
        help="Optional override for DATABASE_URL",
    )
    parser.add_argument(
        "--seed-edge-cases",
        action="store_true",
        help="Run the 100-record edge-case seeder immediately after recreating the schema",
    )
    parser.add_argument(
        "--verify-constraints",
        dest="verify_constraints",
        action="store_true",
        default=True,
        help="Verify negative CHECK/FK/UNIQUE constraints and ON DELETE CASCADE after seeding (default: True)",
    )
    parser.add_argument(
        "--no-verify-constraints",
        dest="verify_constraints",
        action="store_false",
        help="Skip negative constraint verification after seeding",
    )
    return parser


def main() -> int:
    """하드 리셋 스크립트 메인 진입점."""
    parser = build_parser()
    args = parser.parse_args()
    return asyncio.run(_async_main(args))


if __name__ == "__main__":
    raise SystemExit(main())
