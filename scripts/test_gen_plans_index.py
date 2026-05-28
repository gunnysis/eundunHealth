"""Unit tests for scripts/gen_plans_index.py."""
import sys
from pathlib import Path

import pytest  # noqa: F401  (used by pytest discovery)

sys.path.insert(0, str(Path(__file__).parent))
from gen_plans_index import (
    parse_frontmatter,
    validate,
    extract_date_topic,
)


# ---------- parse_frontmatter ----------

def test_parse_frontmatter_basic():
    text = "---\ntype: design\nstatus: proposed\n---\n# title\nbody"
    fm = parse_frontmatter(text)
    assert fm == {"type": "design", "status": "proposed"}


def test_parse_frontmatter_missing_returns_none():
    text = "# title\nno frontmatter here"
    assert parse_frontmatter(text) is None


def test_parse_frontmatter_with_list_field():
    text = "---\ntype: design\nstatus: proposed\ntags:\n  - a\n  - b\n---\nbody"
    fm = parse_frontmatter(text)
    assert fm["tags"] == ["a", "b"]


# ---------- validate ----------

VALID_FM = {
    "type": "design",
    "status": "proposed",
    "target_version": "v0.1.0",
    "tags": ["backend"],
}


def test_validate_passes_valid():
    assert validate(Path("docs/plans/foo.md"), VALID_FM) == []


def test_validate_missing_required_field():
    fm = dict(VALID_FM)
    del fm["status"]
    errs = validate(Path("docs/plans/foo.md"), fm)
    assert any("missing required field 'status'" in e for e in errs)


def test_validate_invalid_type():
    fm = dict(VALID_FM, type="bogus")
    errs = validate(Path("docs/plans/foo.md"), fm)
    assert any("invalid type 'bogus'" in e for e in errs)


def test_validate_invalid_status():
    fm = dict(VALID_FM, status="weird")
    errs = validate(Path("docs/plans/foo.md"), fm)
    assert any("invalid status 'weird'" in e for e in errs)


def test_validate_shipped_requires_pr():
    fm = dict(VALID_FM, status="shipped")  # pr 누락
    errs = validate(Path("docs/plans/foo.md"), fm)
    assert any("status=shipped requires pr" in e for e in errs)


def test_validate_shipped_with_pr_passes():
    fm = dict(VALID_FM, status="shipped", pr=42)
    assert validate(Path("docs/plans/foo.md"), fm) == []


def test_validate_superseded_requires_superseded_by():
    fm = dict(VALID_FM, status="superseded")
    errs = validate(Path("docs/plans/foo.md"), fm)
    assert any("status=superseded requires superseded_by" in e for e in errs)


def test_validate_tags_must_be_nonempty_list():
    fm = dict(VALID_FM, tags=[])
    errs = validate(Path("docs/plans/foo.md"), fm)
    assert any("tags must be a non-empty list" in e for e in errs)

    fm2 = dict(VALID_FM, tags="not-a-list")
    errs2 = validate(Path("docs/plans/foo.md"), fm2)
    assert any("tags must be a non-empty list" in e for e in errs2)


# ---------- extract_date_topic ----------

def test_extract_date_topic_design():
    assert extract_date_topic("2026-05-27-schema-drift-recovery-design.md") == (
        "2026-05-27", "schema-drift-recovery"
    )


def test_extract_date_topic_plan():
    assert extract_date_topic("2026-05-28-mcp-integration-setup-plan.md") == (
        "2026-05-28", "mcp-integration-setup"
    )


def test_extract_date_topic_rfc():
    assert extract_date_topic("2026-05-27-signup-failed-ux-visibility-rfc.md") == (
        "2026-05-27", "signup-failed-ux-visibility"
    )


def test_extract_date_topic_no_suffix():
    assert extract_date_topic("2026-05-29-misc.md") == ("2026-05-29", "misc")
