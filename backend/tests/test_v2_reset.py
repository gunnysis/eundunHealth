import argparse

import pytest

from app.models import Base
from app.models.data_reset_audit import DataResetAudit
from scripts.reset_v2_user_data import (
    EXPECTED_CONFIRMATION,
    RESET_TABLES,
    _validate_evidence,
)


def _make_args(**overrides) -> argparse.Namespace:
    defaults = {
        "confirmation": EXPECTED_CONFIRMATION,
        "reset_id": "v2-2026-10-27-prod-cutover",
        "image_sha": "869407a",
        "execution_id": "eundunhealth-reaper-v2reset01",
    }
    defaults.update(overrides)
    return argparse.Namespace(**defaults)


def test_reset_tables_matches_all_app_data_models():
    """새 ORM 테이블이 추가될 때 reset allow-list 누락을 즉시 감지한다."""
    expected = set(RESET_TABLES)
    app_data_tables = set(Base.metadata.tables) - {DataResetAudit.__tablename__}
    assert app_data_tables == expected


def test_validate_evidence_accepts_valid_inputs():
    _validate_evidence(_make_args())


def test_validate_evidence_rejects_wrong_confirmation():
    with pytest.raises(ValueError, match="reset confirmation mismatch"):
        _validate_evidence(_make_args(confirmation="WRONG"))


def test_validate_evidence_rejects_invalid_image_sha():
    with pytest.raises(ValueError, match="image SHA"):
        _validate_evidence(_make_args(image_sha="NOT_A_SHA"))


def test_validate_evidence_rejects_invalid_reset_id():
    with pytest.raises(ValueError, match="reset id"):
        _validate_evidence(_make_args(reset_id="bad-prefix"))


def test_validate_evidence_rejects_invalid_execution_id():
    with pytest.raises(ValueError, match="execution id"):
        _validate_evidence(_make_args(execution_id="bad id with spaces"))
