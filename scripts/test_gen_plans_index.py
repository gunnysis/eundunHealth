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


def test_validate_holding_status_passes():
    fm = dict(VALID_FM, status="holding")
    assert validate(Path("docs/plans/foo.md"), fm) == []


def test_validate_deferred_status_passes():
    fm = dict(VALID_FM, status="deferred")
    assert validate(Path("docs/plans/foo.md"), fm) == []


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


# ---------- group_pairs ----------

from gen_plans_index import group_pairs, render_readme, render_readme_v2  # noqa: E402


def test_group_pairs_merges_design_and_plan():
    records = [
        {"date": "2026-05-27", "topic": "schema-drift", "type": "design",
         "status": "in-progress", "pr": 47, "related_inc": "INC-X", "tags": ["a"]},
        {"date": "2026-05-27", "topic": "schema-drift", "type": "plan",
         "status": "in-progress", "pr": 47, "related_inc": "INC-X", "tags": ["a"]},
    ]
    grouped = group_pairs(records)
    assert len(grouped) == 1
    assert grouped[0]["type"] == "design + plan"


def test_group_pairs_keeps_singletons():
    records = [
        {"date": "2026-05-27", "topic": "lonely-rfc", "type": "rfc",
         "status": "proposed", "pr": None, "related_inc": None, "tags": ["x"]},
    ]
    grouped = group_pairs(records)
    assert len(grouped) == 1
    assert grouped[0]["type"] == "rfc"


def test_group_pairs_different_topics_not_merged():
    records = [
        {"date": "2026-05-27", "topic": "a", "type": "design",
         "status": "shipped", "pr": 1, "related_inc": None, "tags": ["x"]},
        {"date": "2026-05-27", "topic": "b", "type": "design",
         "status": "shipped", "pr": 2, "related_inc": None, "tags": ["x"]},
    ]
    grouped = group_pairs(records)
    assert len(grouped) == 2


# ---------- render_readme ----------

def test_render_readme_has_auto_generated_comment():
    rendered = render_readme([])
    assert rendered.startswith("<!-- AUTO-GENERATED")


def test_render_readme_status_grouping():
    records = [
        {"date": "2026-05-28", "topic": "shipped-thing", "type": "design + plan",
         "status": "shipped", "pr": 46, "related_inc": None, "tags": ["x"]},
        {"date": "2026-05-27", "topic": "wip-thing", "type": "design",
         "status": "in-progress", "pr": 47, "related_inc": "INC-Y", "tags": ["y"]},
    ]
    rendered = render_readme(records)
    # in-progress 그룹이 shipped 보다 먼저
    assert rendered.index("## In progress") < rendered.index("## Shipped")
    # PR 링크 포함
    assert "[#46](https://github.com/gunnysis/eundunHealth/pull/46)" in rendered
    assert "[#47](https://github.com/gunnysis/eundunHealth/pull/47)" in rendered
    # 인시던트 컬럼
    assert "INC-Y" in rendered
    # 비어 있으면 — (em dash)
    assert " | — |" in rendered


def test_render_readme_date_desc_within_group():
    records = [
        {"date": "2026-05-26", "topic": "older", "type": "design",
         "status": "shipped", "pr": 40, "related_inc": None, "tags": ["x"]},
        {"date": "2026-05-28", "topic": "newer", "type": "design",
         "status": "shipped", "pr": 46, "related_inc": None, "tags": ["x"]},
    ]
    rendered = render_readme(records)
    assert rendered.index("newer") < rendered.index("older")


def test_render_readme_lf_only_no_crlf():
    """Windows host 에서도 LF 만. CI diff 안정성."""
    rendered = render_readme([{
        "date": "2026-05-28", "topic": "t", "type": "design",
        "status": "shipped", "pr": 1, "related_inc": None, "tags": ["x"],
    }])
    assert "\r" not in rendered


# ---------- main (integration) ----------

from gen_plans_index import main, collect_plans  # noqa: E402


def test_main_check_mode_passes_on_synced(tmp_path, monkeypatch):
    plans_dir = tmp_path / "docs" / "plans"
    plans_dir.mkdir(parents=True)
    (plans_dir / "2026-05-28-foo-design.md").write_text(
        "---\ntype: design\nstatus: shipped\npr: 99\n"
        "target_version: v1.0\ntags: [a]\n---\n# foo\n",
        encoding="utf-8",
    )
    monkeypatch.setattr("gen_plans_index.PLANS_DIR", plans_dir)
    monkeypatch.setattr("gen_plans_index.README_PATH", plans_dir / "README.md")
    # generate first
    assert main([]) == 0
    # then --check should pass
    assert main(["--check"]) == 0


def test_main_check_mode_fails_on_drift(tmp_path, monkeypatch, capsys):
    plans_dir = tmp_path / "docs" / "plans"
    plans_dir.mkdir(parents=True)
    (plans_dir / "2026-05-28-foo-design.md").write_text(
        "---\ntype: design\nstatus: shipped\npr: 99\n"
        "target_version: v1.0\ntags: [a]\n---\n# foo\n",
        encoding="utf-8",
    )
    (plans_dir / "README.md").write_text("stale content\n", encoding="utf-8")
    monkeypatch.setattr("gen_plans_index.PLANS_DIR", plans_dir)
    monkeypatch.setattr("gen_plans_index.README_PATH", plans_dir / "README.md")
    assert main(["--check"]) == 1
    captured = capsys.readouterr()
    assert "out of sync" in captured.err


def test_main_fails_on_invalid_frontmatter(tmp_path, monkeypatch, capsys):
    plans_dir = tmp_path / "docs" / "plans"
    plans_dir.mkdir(parents=True)
    (plans_dir / "2026-05-28-bad.md").write_text(
        "---\ntype: design\nstatus: shipped\n"  # pr 누락 → 검증 실패
        "target_version: v1.0\ntags: [a]\n---\n",
        encoding="utf-8",
    )
    monkeypatch.setattr("gen_plans_index.PLANS_DIR", plans_dir)
    monkeypatch.setattr("gen_plans_index.README_PATH", plans_dir / "README.md")
    assert main([]) == 1
    captured = capsys.readouterr()
    assert "status=shipped requires pr" in captured.err


def test_collect_plans_skips_readme_and_templates(tmp_path):
    plans_dir = tmp_path / "docs" / "plans"
    (plans_dir / "_templates").mkdir(parents=True)
    (plans_dir / "README.md").write_text("ignored\n", encoding="utf-8")
    (plans_dir / "_templates" / "design.md").write_text(
        "---\ntype: design\nstatus: proposed\n"
        "target_version: vX\ntags: [t]\n---\n",
        encoding="utf-8",
    )
    (plans_dir / "2026-05-28-x-design.md").write_text(
        "---\ntype: design\nstatus: shipped\npr: 1\n"
        "target_version: v1\ntags: [a]\n---\n",
        encoding="utf-8",
    )
    records, errs = collect_plans(plans_dir)
    assert errs == []
    assert len(records) == 1
    assert records[0]["topic"] == "x"


def test_collect_plans_skips_doc_without_frontmatter(tmp_path, capsys):
    """D5: missing frontmatter → silent skip + stderr warn, NOT error."""
    plans_dir = tmp_path / "docs" / "plans"
    plans_dir.mkdir(parents=True)
    # 정상 doc
    (plans_dir / "2026-05-28-good.md").write_text(
        "---\ntype: design\nstatus: shipped\npr: 1\n"
        "target_version: v1\ntags: [a]\n---\n# good\n",
        encoding="utf-8",
    )
    # frontmatter 없는 doc (PR #47 머지 직후의 schema-drift 시나리오)
    (plans_dir / "2026-05-28-orphan.md").write_text(
        "# orphan doc with no frontmatter\nbody\n",
        encoding="utf-8",
    )
    records, errs = collect_plans(plans_dir)
    assert errs == []
    assert len(records) == 1
    assert records[0]["topic"] == "good"
    captured = capsys.readouterr()
    assert "warning" in captured.err.lower() or "no frontmatter" in captured.err
    assert "orphan" in captured.err


def test_collect_plans_root_only_ignores_subdirs(tmp_path):
    """glob (root-only) scan: 하위 디렉토리의 .md 파일은 무시."""
    plans_dir = tmp_path / "docs" / "plans"
    plans_dir.mkdir(parents=True)
    # root-level pair file (should be collected)
    (plans_dir / "2026-06-05-foo-design.md").write_text(
        "---\ntype: design\nstatus: proposed\n"
        "target_version: v1\ntags: [a]\n---\n# foo\n",
        encoding="utf-8",
    )
    # subdirectory files (should be ignored)
    subdir = plans_dir / "expected"
    subdir.mkdir()
    (subdir / "2026-06-05-bar-design.md").write_text(
        "---\ntype: design\nstatus: proposed\n"
        "target_version: v1\ntags: [b]\n---\n# bar\n",
        encoding="utf-8",
    )
    staging = plans_dir / "_staging"
    staging.mkdir()
    (staging / "2026-06-05-baz-plan.md").write_text(
        "---\ntype: plan\nstatus: proposed\n"
        "target_version: v1\ntags: [c]\n---\n# baz\n",
        encoding="utf-8",
    )
    records, errs = collect_plans(plans_dir)
    assert errs == []
    assert len(records) == 1
    assert records[0]["topic"] == "foo"


def test_render_readme_v2_groups_by_status(tmp_path):
    """비어있지 않은 status 그룹만 하위 섹션으로 렌더링."""
    # ledger 디렉토리 (count_ledger_stats 용)
    plans_dir = tmp_path / "docs" / "plans"
    (plans_dir / "logs").mkdir(parents=True)
    active = [
        {"date": "2026-06-05", "topic": "feat-a", "type": "design + plan",
         "status": "in-progress", "pr": None, "related_inc": None,
         "tags": ["android"], "superseded_by": None},
        {"date": "2026-06-04", "topic": "feat-b", "type": "design",
         "status": "proposed", "pr": None, "related_inc": None,
         "tags": ["backend"], "superseded_by": None},
        {"date": "2026-06-03", "topic": "feat-c", "type": "plan",
         "status": "holding", "pr": None, "related_inc": None,
         "tags": ["infra"], "superseded_by": None},
    ]
    rendered = render_readme_v2(active, [], plans_dir)
    # 3개 그룹 모두 렌더링
    assert "### 진행 중 (1)" in rendered
    assert "### 대기 (proposed / approved) (1)" in rendered
    assert "### 보류 (holding / deferred) (1)" in rendered
    # 총 active 수 표시
    assert "## 활성 작업 (페어 파일, 3)" in rendered
    # deferred 그룹에 항목 없으므로 holding/deferred 그룹에 feat-c만
    assert "feat-c" in rendered
    # 순서: 진행 중 → 대기 → 보류
    assert rendered.index("진행 중") < rendered.index("대기")
    assert rendered.index("대기") < rendered.index("보류")
