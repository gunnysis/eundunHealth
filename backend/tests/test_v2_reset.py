from __future__ import annotations

import argparse
import re

import pytest

from app.database import build_engine_kwargs
from app.models import Base
from app.models.data_reset_audit import DataResetAudit
from app.schema_bootstrap import ensure_schema
from scripts.hard_reset_db import (
    CLEANUP_SQL_PATH,
    CREATE_SQL_PATH,
    EXPECTED_CONFIRMATION,
    RESET_TABLES,
    execute_hard_reset_on_engine,
    split_sql_statements,
    validate_reset_args,
)
from scripts.seed_edge_cases import (
    EXPECTED_TABLE_COUNTS,
    EXPECTED_TOTAL_ROWS,
    run_seeder_on_engine,
)


def _make_args(**overrides: object) -> argparse.Namespace:
    defaults: dict[str, object] = {
        "confirmation": EXPECTED_CONFIRMATION,
        "reset_id": "hard-reset-2026-10-29-pg18",
        "image_sha": "8bbc7ca",
        "execution_id": "eundunhealth-hard-reset-test01",
    }
    defaults.update(overrides)
    return argparse.Namespace(**defaults)


def test_reset_tables_matches_all_app_data_models() -> None:
    """새 ORM 테이블이 추가될 때 reset allow-list 누락을 즉시 감지한다."""
    expected = set(RESET_TABLES)
    app_data_tables = set(Base.metadata.tables) - {DataResetAudit.__tablename__}
    assert app_data_tables == expected


def test_canonical_sql_files_exist_and_parse_cleanly() -> None:
    """01_cleanup_drop_all.sql 과 02_schema_create_all.sql 이 존재하고 비어있지 않은 문장으로 분리된다."""
    assert CLEANUP_SQL_PATH.is_file()
    assert CREATE_SQL_PATH.is_file()
    cleanup_stmts = split_sql_statements(CLEANUP_SQL_PATH.read_text(encoding="utf-8"))
    create_stmts = split_sql_statements(CREATE_SQL_PATH.read_text(encoding="utf-8"))
    assert any("DO $$" in s for s in cleanup_stmts)
    assert sum(1 for s in create_stmts if s.upper().startswith("CREATE TABLE")) == 9


def test_validate_reset_args_accepts_valid_inputs() -> None:
    validate_reset_args(_make_args())


def test_validate_reset_args_rejects_wrong_confirmation() -> None:
    with pytest.raises(ValueError, match="reset confirmation mismatch"):
        validate_reset_args(_make_args(confirmation="WRONG"))


def test_validate_reset_args_rejects_invalid_image_sha() -> None:
    with pytest.raises(ValueError, match="image SHA"):
        validate_reset_args(_make_args(image_sha="NOT_A_SHA"))


def test_validate_reset_args_rejects_invalid_reset_id() -> None:
    with pytest.raises(ValueError, match="reset id"):
        validate_reset_args(_make_args(reset_id="bad-prefix"))


def test_validate_reset_args_rejects_invalid_execution_id() -> None:
    with pytest.raises(ValueError, match="execution id"):
        validate_reset_args(_make_args(execution_id="bad id with spaces"))


@pytest.mark.asyncio
async def test_hard_reset_and_100_edge_case_seeder_end_to_end(db_engine) -> None:
    """Task 1(Cleanup) -> Task 2(Create) -> Task 3(100-row Seeder + Constraint/Cascade verification) 전 구간 검증."""
    summary = await execute_hard_reset_on_engine(
        db_engine,
        reset_id="hard-reset-unit-test",
        image_sha="8bbc7ca",
        execution_id="pytest-suite",
    )
    assert summary["tables_created"] == 9
    assert all(cnt == 0 for cnt in summary["post_reset_counts"].values())

    report = await run_seeder_on_engine(db_engine, verify_constraints=True)
    assert report["total_rows"] == EXPECTED_TOTAL_ROWS == 100
    assert report["table_counts"] == EXPECTED_TABLE_COUNTS
    assert report["verified_constraints_count"] >= 10
    assert "on_delete_cascade_profile_and_meal_plan" in report["verified_constraints"]


@pytest.mark.asyncio
async def test_schema_bootstrap_is_idempotent() -> None:
    """schema_bootstrap.ensure_schema 가 인메모리 DB에서도 예외 없이 멱등 동작한다."""
    await ensure_schema("sqlite+aiosqlite:///:memory:")


def test_canonical_sql_matches_orm_metadata_1_to_1() -> None:
    """02_schema_create_all.sql 과 SQLAlchemy Base.metadata 의 테이블·컬럼·인덱스·제약조건 이름이 1:1 일치한다."""
    sql_text = CREATE_SQL_PATH.read_text(encoding="utf-8")

    sql_tables = set(re.findall(r"CREATE\s+TABLE\s+public\.([a-z0-9_]+)\s*\(", sql_text, flags=re.IGNORECASE))
    orm_tables = set(Base.metadata.tables.keys())
    assert sql_tables == orm_tables

    sql_indexes = set(re.findall(r"CREATE\s+INDEX\s+([a-z0-9_]+)\s+ON", sql_text, flags=re.IGNORECASE))
    orm_indexes = {idx.name for table in Base.metadata.tables.values() for idx in table.indexes if idx.name}
    assert sql_indexes == orm_indexes

    sql_constraints = set(re.findall(r"CONSTRAINT\s+([a-z0-9_]+)\s+", sql_text, flags=re.IGNORECASE))
    orm_constraints = {c.name for table in Base.metadata.tables.values() for c in table.constraints if c.name} | {
        "ck_user_profiles_rest_days_valid"
    }
    assert sql_constraints == orm_constraints

    for table_name, table in Base.metadata.tables.items():
        block_match = re.search(
            rf"CREATE\s+TABLE\s+public\.{table_name}\s*\((.*?)\);",
            sql_text,
            flags=re.IGNORECASE | re.DOTALL,
        )
        assert block_match is not None, f"Missing CREATE TABLE block for {table_name}"
        block_body = block_match.group(1)
        for col in table.columns:
            assert re.search(rf"^\s*{col.name}\s+", block_body, flags=re.MULTILINE), (
                f"Column {table_name}.{col.name} missing in 02_schema_create_all.sql"
            )


def test_split_sql_statements_handles_inline_comments() -> None:
    """줄 끝 인라인 `--` 주석이 있어도 세미콜론 기준 문장 분리가 깨지지 않고 문자열 내부 `--` 는 보존된다."""
    sample = "SET lock_timeout = '15s'; -- inline comment\nSELECT '--not-a-comment' AS label; -- trailing comment\n"
    stmts = split_sql_statements(sample)
    assert stmts == [
        "SET lock_timeout = '15s'",
        "SELECT '--not-a-comment' AS label",
    ]


def test_build_engine_kwargs_for_postgresql_and_sqlite() -> None:
    """PostgreSQL URL에는 B1ms 풀/타임아웃 파라미터가 설정되고 SQLite URL에는 pool_pre_ping만 설정된다."""
    pg_kwargs = build_engine_kwargs("postgresql+asyncpg://dev:devpass@localhost:5432/eundunhealth")
    assert pg_kwargs["pool_size"] == 3
    assert pg_kwargs["max_overflow"] == 2
    assert pg_kwargs["pool_pre_ping"] is True
    assert pg_kwargs["pool_recycle"] == 1800
    assert pg_kwargs["connect_args"] == {"command_timeout": 30}

    sqlite_kwargs = build_engine_kwargs("sqlite+aiosqlite:///:memory:")
    assert sqlite_kwargs == {"pool_pre_ping": True}
