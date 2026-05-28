---
type: plan
status: in-progress
pr: null
related_inc: null
supersedes: null
target_version: docs-only
tags: [docs, tooling, conventions, meta]
---

# docs/plans/ 유지보수 인프라 Implementation Plan

> **For Claude (next session 또는 동일 세션):** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task.

**Goal:** Layer 1 (frontmatter + 자동 INDEX) + Layer 2 (templates) + Layer 4 (postmortem 컨벤션) 을 한 PR 로 도입. 이후 `docs/plans/` 의 status/PR/인시던트 추적이 README 한 번 보면 끝나고, 새 plan 은 템플릿 cp 로 시작, shipped 후엔 plan 끝에 postmortem 회고가 누적.

**Architecture (요약):** Python 스크립트(`scripts/gen_plans_index.py`)가 `docs/plans/*.md` 의 YAML frontmatter 를 `yaml.safe_load` 로 파싱·검증 후 status-grouped `README.md` 생성. shell 래퍼(`scripts/gen-plans-index.sh`)는 backend venv 의 Python 활용 + CI 에선 system Python fallback. pre-commit hook 이 staged docs/plans 변경 시 자동 실행 + git add README. 별도 CI workflow (`docs-plans-index.yml`, paths: `docs/plans/**`) 가 `--check` 모드로 drift 차단.

**Tech Stack:** Python 3.12 + PyYAML 6.0.3 (backend venv 이미 설치, CI 는 pip install). Bash (shell wrapper + pre-commit hook). GitHub Actions YAML.

**참고:**
- Design: `docs/plans/2026-05-28-plans-folder-maintenance-design.md`
- Branch: `chore/plans-folder-maintenance` (Task 0 에서 생성)
- 페어 design (dogfood): 본 plan 자체가 새 frontmatter 형식 사용

**중요 원칙:**
- TDD: Python 스크립트는 fixture 기반 단위 테스트 → 구현 → 통합 검증 순
- 모든 commit 은 `chore/plans-folder-maintenance` 브랜치, 최종 PR 1개
- Frontmatter 백필은 design §5.2 표 (7개) 그대로. schema-drift 2개는 PR #47 머지 후 followup (D5 결정 — missing frontmatter = silent skip)
- README.md 는 생성물 — 손으로 편집 금지 (헤더 코멘트로 명시)
- **파일 IO 는 `newline=""`** 명시 (Windows CRLF translation 회피, diff 안정성)
- **Windows 호스트**: PowerShell primary + Bash tool 보조. 각 Step 첫 줄에 `bash` 또는 `pwsh` 명시
- **D5 — missing vs malformed frontmatter 처리 분리**: `parse_frontmatter` → None 이면 `collect_plans` 가 silent skip + stderr warn. `validate(path, fm: dict)` 는 dict 만 받음 (signature 변경). malformed frontmatter (필수 필드 누락, 잘못된 값) 만 fail

**Task 순서:**

```
Phase 1 — 인프라 (TDD)
  Task 0  branch + 환경 확인
  Task 1  Python parser/validator (test → impl → commit #1)
  Task 2  README renderer + main entry (test → impl → commit #2)
  Task 3  shell wrapper + 2개 templates → commit #3

Phase 2 — 백필 + 첫 README
  Task 4  9개 doc frontmatter 백필 → commit #4
  Task 5  첫 README.md 생성 + 수동 검증 → commit #5

Phase 3 — 자동화
  Task 6  pre-commit hook 분기 추가 → commit #6
  Task 7  CI workflow `docs-plans-index.yml` → commit #7

Phase 4 — 컨벤션
  Task 8  CLAUDE.md Documentation 갱신 + memory convention 갱신 → commit #8

Phase 5 — 검증 + PR
  Task 9  전체 회귀 (drift check + 실패 경로 시뮬레이션 + Android/backend 영향 0 확인)
  Task 10 push + PR 생성
```

---

## 사전 준비

### Task 0: branch + 환경 확인

**Files:** 변경 없음

**Step 1: main 최신화 + 새 브랜치** (bash)

```bash
git fetch origin main
git checkout main
git pull --ff-only origin main
git checkout -b chore/plans-folder-maintenance
git branch --show-current
```

기대: `chore/plans-folder-maintenance`.

**Step 2: PyYAML 사전 확인** (bash)

```bash
backend/.venv/Scripts/python.exe -c "import yaml; print(yaml.__version__)"
```

기대: `6.0.3` (이미 확인됨, sanity check).

**Step 3: pytest 사용 가능 확인** (bash)

```bash
backend/.venv/Scripts/python.exe -m pytest --version
```

기대: `pytest 8.x`. 실패면 backend venv 손상 → 사용자에게 보고.

**No commit.**

---

## Phase 1: 인프라 (TDD)

### Task 1: Python parser + validator (test → impl → commit #1)

**Files:**
- Create: `scripts/test_gen_plans_index.py` (fixture 기반 단위 테스트)
- Create: `scripts/gen_plans_index.py` (parser + validator 부분)

**Step 1: 실패하는 단위 테스트 작성** (write 도구)

`scripts/test_gen_plans_index.py`:

```python
"""Unit tests for scripts/gen_plans_index.py."""
import sys
from pathlib import Path

import pytest

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
    # 컨벤션 외 파일명도 깨지지 않고 (date, full-name) 반환
    assert extract_date_topic("2026-05-29-misc.md") == ("2026-05-29", "misc")
```

**Step 2: 테스트 실행 — fail 확인** (bash)

```bash
backend/.venv/Scripts/python.exe -m pytest scripts/test_gen_plans_index.py -v
```

기대: `ModuleNotFoundError: No module named 'gen_plans_index'` 또는 `ImportError`. 모든 테스트 collection 단계에서 실패.

**Step 3: parser + validator 구현** (write 도구)

`scripts/gen_plans_index.py`:

```python
"""Generate docs/plans/README.md from frontmatter of docs/plans/*.md.

Used by:
  - scripts/gen-plans-index.sh (shell wrapper for local + CI)
  - .githooks/pre-commit (auto-regenerate when docs/plans/*.md staged)
  - .github/workflows/docs-plans-index.yml (--check mode for drift detection)

Convention: see docs/plans/2026-05-28-plans-folder-maintenance-design.md §5.1.
"""
from __future__ import annotations

import re
import sys
from collections import defaultdict
from pathlib import Path

import yaml

REPO_URL = "https://github.com/gunnysis/eundunHealth"

REQUIRED_FIELDS = ["type", "status", "target_version", "tags"]
ALLOWED_TYPE = {"design", "plan", "rfc", "postmortem"}
ALLOWED_STATUS = {
    "proposed", "approved", "in-progress",
    "shipped", "superseded", "abandoned",
}
# Display order (top to bottom in README)
STATUS_ORDER = [
    "in-progress", "proposed", "approved",
    "shipped", "superseded", "abandoned",
]
STATUS_HEADING = {
    "in-progress": "In progress",
    "proposed": "Proposed",
    "approved": "Approved (not started)",
    "shipped": "Shipped",
    "superseded": "Superseded",
    "abandoned": "Abandoned",
}

_FRONTMATTER_RE = re.compile(r"^---\n(.*?)\n---\n", re.DOTALL)
_FILENAME_RE = re.compile(
    r"^(?P<date>\d{4}-\d{2}-\d{2})-(?P<topic>.+?)"
    r"(?:-design|-plan|-rfc|-postmortem)?$"
)


def parse_frontmatter(text: str) -> dict | None:
    """Extract YAML frontmatter dict, or None if missing."""
    m = _FRONTMATTER_RE.match(text)
    if not m:
        return None
    return yaml.safe_load(m.group(1))


def validate(path: Path, fm: dict) -> list[str]:
    """Return list of error messages; empty if valid.

    Note: caller (collect_plans) handles fm=None (missing frontmatter) as
    silent skip + stderr warn. This function only validates malformed frontmatter
    (D5 결정 — multi-PR coordination 안전).
    """
    errs: list[str] = []
    for f in REQUIRED_FIELDS:
        if f not in fm:
            errs.append(f"{path}: missing required field '{f}'")
    t = fm.get("type")
    if t is not None and t not in ALLOWED_TYPE:
        errs.append(f"{path}: invalid type '{t}' (allowed: {sorted(ALLOWED_TYPE)})")
    s = fm.get("status")
    if s is not None and s not in ALLOWED_STATUS:
        errs.append(f"{path}: invalid status '{s}' (allowed: {sorted(ALLOWED_STATUS)})")
    if s == "shipped" and not fm.get("pr"):
        errs.append(f"{path}: status=shipped requires pr field (PR number)")
    if s == "superseded" and not fm.get("superseded_by"):
        errs.append(f"{path}: status=superseded requires superseded_by")
    tags = fm.get("tags")
    if "tags" in fm and (not isinstance(tags, list) or len(tags) == 0):
        errs.append(f"{path}: tags must be a non-empty list")
    return errs


def extract_date_topic(filename: str) -> tuple[str, str]:
    """'2026-05-27-foo-design.md' -> ('2026-05-27', 'foo')."""
    name = filename.rsplit(".", 1)[0]
    m = _FILENAME_RE.match(name)
    if not m:
        return ("", name)
    return (m.group("date"), m.group("topic"))
```

**Step 4: 테스트 재실행 — pass 확인** (bash)

```bash
backend/.venv/Scripts/python.exe -m pytest scripts/test_gen_plans_index.py -v
```

기대: `14 passed` (D5 결정으로 `test_validate_missing_frontmatter` 제거됨 — None 처리는 `collect_plans` 책임).

**Step 5: commit** (bash)

```bash
git add scripts/gen_plans_index.py scripts/test_gen_plans_index.py
git commit -m "$(cat <<'EOF'
feat(docs-tooling): gen_plans_index frontmatter parser + validator (TDD)

YAML frontmatter parser (yaml.safe_load) + 검증 규칙 (status=shipped→pr 필수,
status=superseded→superseded_by 필수, tags non-empty list). 14개 단위 테스트
fixture 기반. README rendering 은 다음 commit. validate() 는 dict 만 받음 —
None (missing frontmatter) 는 collect_plans 에서 silent skip+warn (D5 결정).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: README renderer + main entry (test → impl → commit #2)

**Files:**
- Modify: `scripts/test_gen_plans_index.py` (rendering 테스트 추가)
- Modify: `scripts/gen_plans_index.py` (collect_plans, group_pairs, render_readme, main 추가)

**Step 1: rendering + grouping + main 테스트 추가** (edit 도구)

`scripts/test_gen_plans_index.py` 파일 끝에 추가:

```python


# ---------- group_pairs ----------

from gen_plans_index import group_pairs, render_readme  # noqa: E402


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
    assert " | — |" in rendered  # related_inc=None 행의 인시던트 컬럼


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
    # 정상 doc 만 records 에 포함, 에러 없음
    assert errs == []
    assert len(records) == 1
    assert records[0]["topic"] == "good"
    # warn 메시지가 stderr 에
    captured = capsys.readouterr()
    assert "warning" in captured.err.lower() or "no frontmatter" in captured.err
    assert "orphan" in captured.err
```

**Step 2: 테스트 실행 — fail 확인** (bash)

```bash
backend/.venv/Scripts/python.exe -m pytest scripts/test_gen_plans_index.py -v
```

기대: 신규 테스트 (`test_group_pairs_*`, `test_render_readme_*`, `test_main_*`, `test_collect_plans_*`) 모두 fail (`ImportError: cannot import name 'group_pairs'` 등). Task 1 의 15개는 여전히 pass.

**Step 3: renderer + main 구현** (edit 도구 — `scripts/gen_plans_index.py` 끝에 추가)

기존 `scripts/gen_plans_index.py` 끝에 append:

```python


# ---------- collection + grouping + rendering ----------

REPO_ROOT = Path(__file__).parent.parent
PLANS_DIR = REPO_ROOT / "docs" / "plans"
README_PATH = PLANS_DIR / "README.md"


def collect_plans(plans_dir: Path) -> tuple[list[dict], list[str]]:
    """Walk plans_dir/*.md (skip README + _templates), validate, return records + errs.

    D5: missing frontmatter → silent skip + stderr warn (no error).
        malformed frontmatter → append to errs (caller fails).
    """
    records: list[dict] = []
    all_errs: list[str] = []
    for path in sorted(plans_dir.rglob("*.md")):
        if path.name == "README.md":
            continue
        if "_templates" in path.parts:
            continue
        # 읽을 때 newline="" 으로 Windows CRLF 변환 회피 (해시 안정성)
        text = path.read_text(encoding="utf-8", newline="")
        fm = parse_frontmatter(text)
        # path 는 repo-relative 로 보여주기 (에러 메시지용)
        try:
            display_path = path.relative_to(REPO_ROOT)
        except ValueError:
            display_path = path
        if fm is None:
            # D5: 점진 도입 + 다중 PR coordination 안전
            print(f"::warning file={display_path}::skipping (no frontmatter)", file=sys.stderr)
            continue
        errs = validate(display_path, fm)
        if errs:
            all_errs.extend(errs)
            continue
        date, topic = extract_date_topic(path.name)
        records.append({
            "date": date,
            "topic": topic,
            "filename": path.name,
            "type": fm["type"],
            "status": fm["status"],
            "pr": fm.get("pr"),
            "related_inc": fm.get("related_inc"),
            "tags": fm["tags"],
        })
    return records, all_errs


def group_pairs(records: list[dict]) -> list[dict]:
    """Merge design+plan with same (date, topic) into single row."""
    by_key: dict[tuple[str, str], list[dict]] = defaultdict(list)
    for r in records:
        by_key[(r["date"], r["topic"])].append(r)
    out: list[dict] = []
    for (_date, _topic), group in by_key.items():
        types = sorted({r["type"] for r in group})
        rep = group[0]
        out.append({
            "date": rep["date"],
            "topic": rep["topic"],
            "type": " + ".join(types),
            "status": rep["status"],
            "pr": rep["pr"],
            "related_inc": rep["related_inc"],
            "tags": rep["tags"],
        })
    return out


def render_readme(records: list[dict]) -> str:
    by_status: dict[str, list[dict]] = defaultdict(list)
    for r in records:
        by_status[r["status"]].append(r)

    lines: list[str] = [
        "<!-- AUTO-GENERATED by scripts/gen-plans-index.sh — do not edit manually -->",
        "# docs/plans/ 인덱스",
        "",
        "> `docs/plans/` 는 비-trivial 작업의 design + plan 페어를 모은 폴더. "
        "컨벤션은 memory `design-plan-docs-convention.md` 참조.",
        "> 본 INDEX 는 frontmatter 기반 자동 생성 — 직접 편집 X. "
        "재생성: `bash scripts/gen-plans-index.sh`.",
        "",
    ]
    for status in STATUS_ORDER:
        rows = by_status.get(status, [])
        if not rows:
            continue
        rows.sort(key=lambda r: r["date"], reverse=True)
        lines.append(f"## {STATUS_HEADING[status]} ({len(rows)})")
        lines.append("")
        lines.append("| 날짜 | 주제 | type | PR | 인시던트 | tags |")
        lines.append("|---|---|---|---|---|---|")
        for r in rows:
            pr_cell = (
                f"[#{r['pr']}]({REPO_URL}/pull/{r['pr']})"
                if r.get("pr") else "—"
            )
            inc_cell = r.get("related_inc") or "—"
            tags_cell = ", ".join(r["tags"])
            lines.append(
                f"| {r['date']} | {r['topic']} | {r['type']} | "
                f"{pr_cell} | {inc_cell} | {tags_cell} |"
            )
        lines.append("")
    # LF only, trailing newline
    return "\n".join(lines) + "\n"


def main(argv: list[str]) -> int:
    check_only = "--check" in argv
    records, errs = collect_plans(PLANS_DIR)
    if errs:
        for e in errs:
            # GitHub Actions annotation format
            file_part = e.split(":", 1)[0]
            print(f"::error file={file_part}::{e}", file=sys.stderr)
        return 1
    grouped = group_pairs(records)
    rendered = render_readme(grouped)
    if check_only:
        existing = (
            README_PATH.read_text(encoding="utf-8", newline="")
            if README_PATH.exists() else ""
        )
        if existing != rendered:
            print(
                "::error::docs/plans/README.md is out of sync with frontmatter.",
                file=sys.stderr,
            )
            print(
                "Fix locally: bash scripts/gen-plans-index.sh && "
                "git add docs/plans/README.md && git commit",
                file=sys.stderr,
            )
            return 1
        return 0
    # newline="" 로 CRLF 변환 회피
    README_PATH.write_text(rendered, encoding="utf-8", newline="")
    print(f"OK {README_PATH} ({len(grouped)} entries)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
```

**Step 4: 테스트 재실행 — 전부 pass 확인** (bash)

```bash
backend/.venv/Scripts/python.exe -m pytest scripts/test_gen_plans_index.py -v
```

기대: `25 passed` (14 from Task 1 + 11 from Task 2 — D5 의 skip_doc_without_frontmatter 포함).

**Step 5: commit** (bash)

```bash
git add scripts/gen_plans_index.py scripts/test_gen_plans_index.py
git commit -m "$(cat <<'EOF'
feat(docs-tooling): README renderer + collect_plans + main entry

- collect_plans: docs/plans/*.md walk, _templates/ + README.md 제외, frontmatter 검증
  - D5: missing frontmatter → stderr warn + skip (NOT error). malformed → fail.
    PR #47 머지 후 schema-drift 페어가 frontmatter 없이 등장해도 main 안 깨짐.
- group_pairs: (date, topic) 동일 design+plan 을 한 row 로 머지
- render_readme: status 별 그룹핑, 그룹 내 날짜 desc, PR 절대 URL 링크
- main: 기본 생성 + --check drift detection (GitHub Actions annotation 포맷)
- 파일 IO 는 모두 newline="" — Windows CRLF 변환 회피, 해시 안정성

추가 11개 단위 테스트 (group/render/main/collect + D5 skip 동작). 총 25개 PASS.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: shell wrapper + 2개 templates → commit #3

**Files:**
- Create: `scripts/gen-plans-index.sh`
- Create: `docs/plans/_templates/design.md`
- Create: `docs/plans/_templates/plan.md`

**Step 1: shell wrapper 작성** (write)

`scripts/gen-plans-index.sh`:

```bash
#!/usr/bin/env bash
# Generate docs/plans/README.md from frontmatter of docs/plans/*.md.
#
# 용도:
#   1) 로컬: pre-commit hook 이 docs/plans/*.md 변경 시 자동 호출
#   2) CI:   .github/workflows/docs-plans-index.yml 이 --check 모드로 drift 차단
#   3) 수동: bash scripts/gen-plans-index.sh
#
# Python 선택 우선순위:
#   1. backend/.venv (로컬 개발자)
#   2. system python3 (CI 러너 — actions/setup-python 가 제공)
#
# 옵션:
#   --check   생성 안 함. 기존 README 와 새 내용 diff. 다르면 exit 1.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT="${REPO_ROOT}/scripts/gen_plans_index.py"

# Python interpreter resolution
if [ -x "${REPO_ROOT}/backend/.venv/Scripts/python.exe" ]; then
    PY="${REPO_ROOT}/backend/.venv/Scripts/python.exe"     # Windows venv
elif [ -x "${REPO_ROOT}/backend/.venv/bin/python" ]; then
    PY="${REPO_ROOT}/backend/.venv/bin/python"             # Linux/macOS venv
elif command -v python3 >/dev/null 2>&1; then
    PY="python3"                                            # CI fallback
elif command -v python >/dev/null 2>&1; then
    PY="python"
else
    echo "ERROR: python interpreter not found" >&2
    exit 1
fi

# PyYAML 사전 점검 (system python 에서 흔히 빠짐)
if ! "${PY}" -c "import yaml" 2>/dev/null; then
    echo "ERROR: PyYAML not installed for ${PY}" >&2
    echo "Install: ${PY} -m pip install PyYAML" >&2
    exit 1
fi

exec "${PY}" "${SCRIPT}" "$@"
```

**Step 2: 실행 권한 + LF 확정 (entrypoint.sh 패턴 재활용)** (bash)

```bash
# backend/.gitattributes 와 동일하게 repo root .gitattributes 에 *.sh eol=lf 이미 있음 (확인)
cat .gitattributes
# 기대 출력:
#   *.sh text eol=lf
#   scripts/**/*.sh text eol=lf

git add scripts/gen-plans-index.sh
git update-index --chmod=+x scripts/gen-plans-index.sh
git ls-files --stage scripts/gen-plans-index.sh
# 기대: 100755 <sha> 0  scripts/gen-plans-index.sh
```

**Step 3: design 템플릿 작성** (write)

`docs/plans/_templates/design.md`:

```markdown
---
type: design
status: proposed
pr: null
related_inc: null
supersedes: null
target_version: vX.Y.Z
tags: [TBD]
---

# {제목} 설계

- **작성일**: YYYY-MM-DD
- **상태**: 작성 중 (또는 "승인 완료")
- **연관 작업**: (PR / 인시던트 / 이전 design 등)
- **대상 버전**: (versionCode N 또는 docs-only / infra-only)
- **선행 작업**: (의존성 작업, 없으면 "없음")

## 1. 배경

(왜 지금 / 사용자 문제 / 인시던트 트리거 / 데이터 증거)

## 2. Scope

### In-scope
- 항목 1

### Out-of-scope
- 항목 1 (이유: ...)

## 3. 의사결정 요약

| # | 결정 | 채택안 | 근거 |
|---|---|---|---|

## 4. 옵션 비교

| 옵션 | A. ... | B. ... | C. ... |
|---|---|---|---|

## 5. 구성 요소별 변경

### 5.1 NEW/MODIFY: `path/to/file`

(코드 블록 + 차이 설명)

## 6. 검증 계획

## 7. 롤백 절차

## 8. 잔여 리스크

## 9. 참고 자료
```

**Step 4: plan 템플릿 작성** (write)

`docs/plans/_templates/plan.md`:

```markdown
---
type: plan
status: proposed
pr: null
related_inc: null
supersedes: null
target_version: vX.Y.Z
tags: [TBD]
---

# {제목} Implementation Plan

> **For Claude (next session):** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task.

**Goal:** (한두 문장 — 무엇을 / 왜)

**Architecture (요약):** (핵심 패턴 한 문단)

**Tech Stack:** Python 3.12 / Kotlin 2.2.10 / ...

**참고:**
- Design: `docs/plans/YYYY-MM-DD-{topic}-design.md`
- Branch: `{branch-name}` (Task 0 에서 생성)

**중요 원칙:**
- TDD: 동작 변경 task 는 red → green → commit
- 모든 commit 은 `{branch}` 브랜치, 최종 PR 1개
- Windows 호스트: 각 Step 첫 줄에 `bash` 또는 `pwsh` 명시

**Task 순서:**

```
Task 0  branch + 환경 확인
Task 1  ...
Task N  push + PR
```

---

## Phase 1: ...

### Task 1: ...

**Files:** ...

**Step 1:** ...

**Step N: commit** (bash)

```bash
git commit -m "..."
```

---

## Phase 2: 최종 검증 + PR

### Task N-1: 전체 회귀
### Task N:   push + PR

---

## Phase 3 (선택): 머지 후 운영 검증

---

## 잔여 리스크 / 후속 작업

## Postmortem

> (PR 머지 + 7일 후 채움. 계획과 다르게 갔던 점 / 발견된 새 위험 / 다음 plan 에 적용할 교훈.
>  없으면 "특이사항 없음" 1줄. 비워두지 말 것.)
```

**Step 5: 통합 smoke test — 실제 plans 폴더에 대해 실행** (bash)

```bash
bash scripts/gen-plans-index.sh 2>&1 | head -30
echo "EXIT=$?"
```

기대: **exit 0** (D5 덕). stderr 에 `::warning file=...::skipping (no frontmatter)` 7건 (백필 안 된 7개 doc), stdout 에 `OK docs/plans/README.md (1 entries)` (본 design+plan 페어만 grouped). 생성된 README 에는 본 페어 1 row 만 표시.

**Step 6: commit** (bash)

```bash
git add scripts/gen-plans-index.sh \
        docs/plans/_templates/design.md \
        docs/plans/_templates/plan.md
git commit -m "$(cat <<'EOF'
feat(docs-tooling): shell wrapper + design/plan templates

- scripts/gen-plans-index.sh: backend venv → system python3 fallback,
  PyYAML 사전 점검, exec 로 PID 1 교체 (signal forwarding)
- 100755 + LF 확정 (.gitattributes 기존 *.sh eol=lf 활용)
- _templates/design.md, _templates/plan.md: 새 plan 작성 시 cp 시작점,
  frontmatter 포함, 섹션 구조는 schema-drift-recovery 페어 참조

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 2: 백필 + 첫 README

### Task 4: 7개 doc frontmatter 백필 → commit #4

**Files:** 7개 — `docs/plans/*.md` (이 plan + design 페어 제외, schema-drift 페어 2개 제외 — PR #47 머지 후 followup)

**Step 1: 매핑 표 (design §5.2 그대로)** — 각 파일 위에 추가할 frontmatter

| 파일 | type | status | pr | related_inc | tags |
|---|---|---|---|---|---|
| `2026-05-26-applinks-deep-link-design.md` | design | shipped | 42 | null | [android, auth, deep-link] |
| `2026-05-26-applinks-deep-link-plan.md` | plan | shipped | 42 | null | [android, auth, deep-link] |
| `2026-05-26-signup-confirmation-flow-design.md` | design | shipped | 40 | null | [android, auth, supabase] |
| `2026-05-26-signup-confirmation-flow-plan.md` | plan | shipped | 40 | null | [android, auth, supabase] |
| `2026-05-27-signup-failed-ux-visibility-rfc.md` | rfc | proposed | null | INC-2026-05-26-01 | [android, ux] |
| `2026-05-28-mcp-integration-setup-design.md` | design | shipped | 46 | null | [ops, mcp, automation] |
| `2026-05-28-mcp-integration-setup-plan.md` | plan | shipped | 46 | null | [ops, mcp, automation] |

**Step 2: 각 doc 맨 위에 frontmatter 삽입** (edit 도구)

예시 — `2026-05-26-applinks-deep-link-design.md`:

기존 첫 줄:
```
# App Links / Deep Link 도입 — 메일 인증 자동 로그인 설계
```

으로 시작 → 다음으로 교체:
```
---
type: design
status: shipped
pr: 42
related_inc: null
supersedes: null
target_version: v0.1.3
tags: [android, auth, deep-link]
---

# App Links / Deep Link 도입 — 메일 인증 자동 로그인 설계
```

**target_version 값 결정 가이드** (커밋·CHANGELOG 기반):
- applinks (#42) → `v0.1.3`
- signup-confirmation (#40) → `v0.1.1`
- schema-drift (#47, 머지 전) → `v0.1.5` (design 에 명시)
- signup-failed-ux RFC → `pending` (string)
- mcp-integration (#46) → `infra-only` (앱 버전 영향 없음)

**Step 3: smoke verify — validation pass 확인** (bash)

```bash
bash scripts/gen-plans-index.sh 2>&1 | head -10
echo "EXIT=$?"
```

기대: exit 0 + stderr 에 schema-drift 페어 2건 `::warning ... no frontmatter` + stdout `OK docs/plans/README.md (5 entries)` (3 shipped 페어 + 1 RFC + 본 페어 approved = 5 grouped rows).

검증 실패 시: 에러 메시지가 파일/필드 명시 → 해당 frontmatter 수정 후 재시도.

**Step 4: commit (README.md 는 다음 Task 에서 별도 commit — diff 가 메타데이터 vs 생성물 분리)** (bash)

```bash
git add docs/plans/2026-05-26-applinks-deep-link-design.md \
        docs/plans/2026-05-26-applinks-deep-link-plan.md \
        docs/plans/2026-05-26-signup-confirmation-flow-design.md \
        docs/plans/2026-05-26-signup-confirmation-flow-plan.md \
        docs/plans/2026-05-27-signup-failed-ux-visibility-rfc.md \
        docs/plans/2026-05-28-mcp-integration-setup-design.md \
        docs/plans/2026-05-28-mcp-integration-setup-plan.md
git commit -m "$(cat <<'EOF'
chore(docs): backfill YAML frontmatter for 7 existing plans

7개 기존 doc (3 design+plan 페어 + 1 RFC) 에 frontmatter 추가:
- shipped: applinks (#42), signup-confirmation (#40), mcp-integration (#46)
- proposed: signup-failed-ux RFC

schema-drift 페어 (2026-05-27-schema-drift-recovery-{design,plan}.md) 는 PR #47
머지 후 별도 followup commit. 그 동안엔 D5 덕에 silent skip (CI 안 깨짐).

target_version + tags 도 같이 채워서 INDEX 자동 생성 + 향후 status 갱신 SoT 확보.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: 첫 README.md 생성 + 수동 검증 → commit #5

**Files:** Create — `docs/plans/README.md` (자동 생성)

**Step 1: README 생성** (bash)

```bash
bash scripts/gen-plans-index.sh
cat docs/plans/README.md
```

기대 출력 골격:
```
<!-- AUTO-GENERATED by scripts/gen-plans-index.sh — do not edit manually -->
# docs/plans/ 인덱스

> ...

## Approved (not started) (1)
| 2026-05-28 | plans-folder-maintenance | design + plan | — | — | docs, tooling, conventions, meta |

## Proposed (1)
| 2026-05-27 | signup-failed-ux-visibility | rfc | — | INC-2026-05-26-01 | android, ux |

## Shipped (3)
| 2026-05-28 | mcp-integration-setup | design + plan | [#46](...) | — | ops, mcp, automation |
| 2026-05-26 | signup-confirmation-flow | design + plan | [#40](...) | — | android, auth, supabase |
| 2026-05-26 | applinks-deep-link | design + plan | [#42](...) | — | android, auth, deep-link |
```

stderr 에는 schema-drift 2건 warn 출력. 본 페어는 `status: approved` 으로 "Approved" 그룹.
Task 8 에서 본 페어 status 를 `in-progress` 로 갱신하면 "In progress" 그룹으로 이동.

**Step 2: 수동 시각 검증** (bash)

```bash
# - 모든 그룹 헤더 있는지
grep -E "^## (In progress|Proposed|Approved|Shipped)" docs/plans/README.md
# - PR 링크가 절대 URL 인지
grep -E "github\.com/gunnysis/eundunHealth/pull" docs/plans/README.md | head -5
# - LF only (CRLF 없음)
python3 -c "print('CRLF' if b'\r' in open('docs/plans/README.md','rb').read() else 'LF only')"
```

기대: CRLF 없음, 링크 4건 (3 shipped + schema-drift in-progress), 헤더 3~4개.

**Step 3: 본 plan + design 의 status 확인** (bash)

본 plan + design 의 frontmatter status 는 작성 시 `approved` 로 둠. PR #47 (schema-drift) 와 마찬가지로 머지 전엔 `in-progress` 가 정확 — 하지만 본 PR 의 commit 이 시작될 때 status 를 in-progress 로 갱신할 필요는 없음 (commit 자체가 in-progress 신호). PR 머지 후 사람이 일괄 `shipped + pr 채움`.

**선택**: 본 PR Phase 4 Task 8 에서 본 design+plan 의 status 를 `in-progress` 로 갱신해서 README 의 "In progress" 그룹에 노출되게 할지 결정. **추천: yes** — drift 가 아니라 정상 상태 반영.

(Task 8 에서 처리, 여기선 일단 approved 그대로 둠.)

**Step 4: commit** (bash)

```bash
git add docs/plans/README.md
git commit -m "$(cat <<'EOF'
docs(plans): generate first INDEX (README.md) — auto-generated

frontmatter 백필 직후 첫 자동 생성. 5 entries (4 페어 + 1 RFC, 본 plan+design
페어는 approved 상태로 별도 그룹).

이 파일은 손으로 편집하지 말 것 — `bash scripts/gen-plans-index.sh` 재생성.
다음 commit 부터 pre-commit hook 이 자동 호출 + CI 가 drift 차단.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 3: 자동화

### Task 6: pre-commit hook 분기 추가 → commit #6

**Files:** Modify — `.githooks/pre-commit`

**Step 1: docs/plans 분기 추가** (edit 도구)

기존 hook 끝 (Kotlin spotless+detekt 분기 뒤) 에 추가:

```bash

# 3) docs/plans/*.md 변경 시 INDEX 자동 재생성
CHANGED_PLANS=$(git diff --cached --name-only --diff-filter=ACM \
    | grep -E '^docs/plans/.*\.md$' \
    | grep -v 'docs/plans/README\.md$' \
    | grep -v 'docs/plans/_templates/' || true)

if [ -n "$CHANGED_PLANS" ]; then
    echo "[pre-commit] docs/plans/ changes detected → regenerating README.md"
    bash "$(git rev-parse --show-toplevel)/scripts/gen-plans-index.sh"
    git add "$(git rev-parse --show-toplevel)/docs/plans/README.md"
fi
```

**Step 2: 로컬 hook 실행 시뮬레이션 — 더미 변경으로 검증** (bash)

```bash
# 임시로 한 doc 의 tags 마지막에 무해한 변경 (공백 추가)
echo "" >> docs/plans/2026-05-26-applinks-deep-link-design.md
git add docs/plans/2026-05-26-applinks-deep-link-design.md

# hook 직접 실행 (git commit 안 함)
bash .githooks/pre-commit
# 기대: "docs/plans/ changes detected → regenerating README.md" 출력 + README 재생성

# 변경 되돌리기
git restore --staged docs/plans/2026-05-26-applinks-deep-link-design.md
git restore docs/plans/2026-05-26-applinks-deep-link-design.md
git restore docs/plans/README.md  # 재생성된 README 도 되돌림 (변경 없을 것이지만 안전)
```

**Step 3: commit** (bash)

```bash
git add .githooks/pre-commit
git commit -m "$(cat <<'EOF'
feat(hooks): pre-commit auto-regenerates docs/plans/README.md

기존 Kotlin spotless+detekt 분기 다음에 docs/plans/*.md 변경 감지 시
gen-plans-index 실행 + README.md 자동 stage 분기 추가. README.md / _templates/
는 trigger 에서 제외 (자기참조 무한 루프 방지).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 7: CI workflow `docs-plans-index.yml` → commit #7

**Files:** Create — `.github/workflows/docs-plans-index.yml`

**Step 1: workflow 작성** (write)

`.github/workflows/docs-plans-index.yml`:

```yaml
name: docs-plans-index

# 본 workflow 는 docs/plans/ frontmatter ↔ docs/plans/README.md drift 만 차단한다.
# backend.yml / android.yml 과 paths 가 disjoint — backend dev 의 CI 대기시간 보호.
on:
  pull_request:
    paths:
      - 'docs/plans/**'
      - 'scripts/gen_plans_index.py'
      - 'scripts/gen-plans-index.sh'
      - '.github/workflows/docs-plans-index.yml'
  push:
    branches: [main]
    paths:
      - 'docs/plans/**'
      - 'scripts/gen_plans_index.py'
      - 'scripts/gen-plans-index.sh'
      - '.github/workflows/docs-plans-index.yml'

jobs:
  check-index:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v5

      - name: Setup Python
        uses: actions/setup-python@v6
        with:
          python-version: '3.12'

      - name: Install PyYAML
        run: pip install 'PyYAML==6.0.3'

      - name: Run unit tests (parser/validator/renderer)
        run: python -m pytest scripts/test_gen_plans_index.py -v

      - name: Check INDEX drift
        run: bash scripts/gen-plans-index.sh --check
```

**Step 2: workflow 문법 검증** (bash — gh CLI 또는 actionlint 가 있으면, 없으면 push 후 확인)

```bash
# actionlint 가 PATH 에 있으면:
command -v actionlint >/dev/null && actionlint .github/workflows/docs-plans-index.yml || echo "(actionlint not installed; will verify on push)"
```

(없어도 OK — Task 9 의 push 후 GitHub 가 검증)

**Step 3: commit** (bash)

```bash
git add .github/workflows/docs-plans-index.yml
git commit -m "$(cat <<'EOF'
ci: docs-plans-index workflow — frontmatter ↔ README drift 차단

paths 가 backend.yml / android.yml 과 disjoint — backend dev 의 CI 대기시간 보호.
job: PyYAML 6.0.3 설치 → 단위 테스트 25개 실행 → --check 모드 drift 감지.
실패 시 fix 명령어를 GitHub Actions annotation 으로 출력 (gen_plans_index.py main()).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 4: 컨벤션

### Task 8: CLAUDE.md Documentation + memory convention + 본 design/plan status 갱신 → commit #8

**Files:**
- Modify: `CLAUDE.md` (Documentation 섹션)
- Modify: memory `design-plan-docs-convention.md`
- Modify: `docs/plans/2026-05-28-plans-folder-maintenance-design.md` (status: approved → in-progress)
- Modify: `docs/plans/2026-05-28-plans-folder-maintenance-plan.md` (status: approved → in-progress)

**Step 1: CLAUDE.md `## Documentation` 섹션에 한 줄 추가** (edit)

기존 `## Documentation` 항목 중 `@docs/ops/operations-snapshot.md` 라인 위에 추가:

```diff
+ - `@docs/plans/README.md` — design+plan 페어 인덱스 (자동 생성, frontmatter SoT, INC 추적 컬럼 포함)
  - `@docs/ops/operations-snapshot.md` — **현재 운영 상태 단일 출처**
```

**Step 2: memory `design-plan-docs-convention.md` 갱신** (edit)

기존 "How to apply" 1번 (파일명 컨벤션) 다음에 새 2번 삽입:

```markdown
2. **Frontmatter (필수)**: 모든 doc 맨 위에 YAML frontmatter. schema 는 `docs/plans/2026-05-28-plans-folder-maintenance-design.md` §5.1 참조.
   - 필수: `type` (design|plan|rfc|postmortem), `status` (proposed|approved|in-progress|shipped|superseded|abandoned), `target_version`, `tags` (non-empty list)
   - 조건부 필수: `status: shipped` → `pr` 필수. `status: superseded` → `superseded_by` 필수.
   - 선택: `related_inc`, `supersedes`
   - 새 plan 작성 시 `cp docs/plans/_templates/{design,plan}.md docs/plans/YYYY-MM-DD-{topic}-{design,plan}.md` 로 시작
```

기존 번호 2,3 → 3,4 로 밀어내고, 끝에 새 항목 추가:

```markdown
5. **INDEX 자동 생성**: `bash scripts/gen-plans-index.sh` 가 frontmatter 읽어 `docs/plans/README.md` 생성. pre-commit hook 이 자동 호출 + CI workflow `docs-plans-index.yml` 가 drift 차단. 손으로 README 편집 X.

6. **PR 머지 후 status 갱신 (사람 책임)**: PR 머지되면 해당 design+plan 페어의 frontmatter `status: in-progress → shipped`, `pr: <번호>` 갱신. 1주 후 plan 끝에 `## Postmortem` 섹션 작성 ("특이사항 없음" 1줄이라도). 자동화 X — INDEX 의 PR 컬럼이 cross-check.
```

memory MEMORY.md 의 한 줄 설명도 갱신 (Quick Reference 영역에서 design-plan-docs-convention 항목이 있으면):

```markdown
- Design+plan 문서 컨벤션: [design-plan-docs-convention.md](design-plan-docs-convention.md) — frontmatter (YAML) + 자동 INDEX (README.md) + pre-commit hook + CI drift check (2026-05-28 부터)
```

**Step 3: 본 design + plan 의 status 를 in-progress 로 갱신** (edit)

본 plan + design 두 파일의 frontmatter 의 `status: approved` → `status: in-progress`. PR 머지 후 사람이 `shipped + pr 채움` 으로 다시 갱신.

```bash
# 갱신 후 README 재생성 확인
bash scripts/gen-plans-index.sh
grep "plans-folder-maintenance" docs/plans/README.md
# 기대: "In progress" 그룹에 plans-folder-maintenance 행이 있어야 함
```

**Step 4: commit** (bash)

```bash
git add CLAUDE.md \
        docs/plans/2026-05-28-plans-folder-maintenance-design.md \
        docs/plans/2026-05-28-plans-folder-maintenance-plan.md \
        docs/plans/README.md
git commit -m "$(cat <<'EOF'
docs: CLAUDE.md + 본 plan/design status (approved → in-progress) + INDEX 재생성

- CLAUDE.md Documentation 섹션에 docs/plans/README.md 추가
- 본 plan-folder-maintenance design+plan 자체의 status 를 in-progress 로 갱신
  (commit 이 시작된 시점 = in-progress 가 정확)
- README.md 자동 재생성 — In progress 그룹에 본 페어 표시
- memory 'design-plan-docs-convention.md' 갱신은 별도 commit 안 함
  (~/.claude/projects/ 경로는 git 미추적 — 사용자가 수동 갱신)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

**Step 5: memory 파일 직접 갱신 (git 영역 밖)** (write)

`C:\Users\Administrator\.claude\projects\C--programming-apps-eundunHealth\memory\design-plan-docs-convention.md` 의 본문 (frontmatter 제외) 을 Step 2 의 내용으로 갱신.

(이 step 은 Bash 가 아니라 Write 도구로 처리. commit 없음.)

---

## Phase 5: 검증 + PR

### Task 9: 전체 회귀

**Files:** 변경 없음

**Step 1: 단위 테스트 전체** (bash)

```bash
backend/.venv/Scripts/python.exe -m pytest scripts/test_gen_plans_index.py -v
```

기대: `25 passed`.

**Step 2: --check drift 통과** (bash)

```bash
bash scripts/gen-plans-index.sh --check
echo "EXIT=$?"
```

기대: `EXIT=0`.

**Step 3: drift 실패 경로 시뮬레이션** (bash)

```bash
# README 의 일부 줄을 일부러 깨기
cp docs/plans/README.md /tmp/readme.bak
echo "BOGUS LINE" >> docs/plans/README.md

bash scripts/gen-plans-index.sh --check 2>&1 | head -5
echo "EXIT=$?"

# 복원
mv /tmp/readme.bak docs/plans/README.md
bash scripts/gen-plans-index.sh --check && echo "RESTORED OK"
```

기대: 첫 --check 는 exit 1 + `::error::docs/plans/README.md is out of sync` + fix 명령어. 복원 후 exit 0.

**Step 4: frontmatter 검증 실패 경로 시뮬레이션** (bash)

```bash
# RFC 의 frontmatter 에서 tags 일부러 빈 리스트로
sed -i 's/^tags:.*$/tags: []/' docs/plans/2026-05-27-signup-failed-ux-visibility-rfc.md

bash scripts/gen-plans-index.sh 2>&1 | head -5
echo "EXIT=$?"

# 복원
git restore docs/plans/2026-05-27-signup-failed-ux-visibility-rfc.md
bash scripts/gen-plans-index.sh && echo "RESTORED OK"
```

기대: 첫 실행 exit 1 + `::error file=docs/plans/...::...tags must be a non-empty list`. 복원 후 exit 0.

**Step 5: backend/Android 영향 없음 sanity** (bash + pwsh)

```bash
# backend 정적 검사 (개정 없음 → clean 유지여야)
cd backend && .venv/Scripts/python.exe -m pytest tests/ -q 2>&1 | tail -3
.venv/Scripts/python.exe -m mypy app/ 2>&1 | tail -3
cd ..
```

(pwsh)
```pwsh
.\gradlew.bat :app:testDebugUnitTest 2>&1 | Select-Object -Last 5
```

기대: backend pytest 44 passed, mypy clean, Android `BUILD SUCCESSFUL` (변경 0).

---

### Task 10: push + PR 생성

**Step 1: push** (bash)

```bash
git push -u origin chore/plans-folder-maintenance
```

**Step 2: PR 생성** (bash)

```bash
gh pr create --title "chore(docs): docs/plans/ frontmatter + 자동 INDEX + templates + postmortem 컨벤션" --body "$(cat <<'EOF'
## Summary
- `docs/plans/` 9개 doc 에 YAML frontmatter 백필 (type/status/pr/related_inc/target_version/tags).
- `scripts/gen_plans_index.py` (+ shell wrapper) — frontmatter → `docs/plans/README.md` 자동 생성 (status 별 그룹, design+plan 페어 한 row, PR 절대 URL 링크).
- pre-commit hook 자동 호출 + 별도 CI workflow `docs-plans-index.yml` 가 drift 차단 (backend.yml 과 paths disjoint — backend dev CI 대기시간 보호).
- `_templates/{design,plan}.md` 2개 템플릿 — 새 plan 작성 시 `cp` 시작점.
- plan 의 `## Postmortem` 섹션 컨벤션 정의 (별도 파일 X, 같은 plan 끝에 누적, "특이사항 없음" 1줄도 작성 필수).
- CLAUDE.md Documentation 섹션 + memory `design-plan-docs-convention.md` 갱신.

## Design / Plan
- Design: `docs/plans/2026-05-28-plans-folder-maintenance-design.md`
- Plan: `docs/plans/2026-05-28-plans-folder-maintenance-plan.md`
- 두 doc 자체가 새 frontmatter 형식 사용 (dogfood)

## Test plan
- [x] `python -m pytest scripts/test_gen_plans_index.py -v` → 25 passed (parser/validator/group/render/main/collect)
- [x] `bash scripts/gen-plans-index.sh` → README 생성 (5 entries, 4 페어 + 1 RFC + 본 페어)
- [x] `bash scripts/gen-plans-index.sh --check` → exit 0 (no drift)
- [x] drift 시뮬레이션 (README 임의 수정 → --check exit 1 + 명확한 에러)
- [x] frontmatter 검증 실패 시뮬레이션 (tags=[] → exit 1 + GitHub Actions annotation)
- [x] backend pytest 44/44 / mypy clean (영향 0 확인)
- [x] Android `:app:testDebugUnitTest` BUILD SUCCESSFUL (영향 0 확인)
- [ ] **머지 후 (사람 책임)**: 본 design+plan 페어의 frontmatter `status: in-progress → shipped`, `pr: <이 PR>` 갱신 + Postmortem 섹션 작성 (1주 후)

## 변경 요약
| 파일 | 변경 |
|---|---|
| `scripts/gen_plans_index.py` | NEW — parser/validator/renderer/main (~210 lines) |
| `scripts/test_gen_plans_index.py` | NEW — 25개 단위 테스트 |
| `scripts/gen-plans-index.sh` | NEW — shell wrapper (venv → system python fallback) |
| `docs/plans/_templates/design.md` | NEW — design 시작점 |
| `docs/plans/_templates/plan.md` | NEW — plan 시작점 (Postmortem 섹션 포함) |
| `docs/plans/*.md` (9개) | frontmatter 백필 |
| `docs/plans/README.md` | NEW — 자동 생성, 절대 손으로 편집 X |
| `.githooks/pre-commit` | docs/plans/ 변경 시 README 자동 재생성 분기 추가 |
| `.github/workflows/docs-plans-index.yml` | NEW — CI drift check |
| `CLAUDE.md` | Documentation 섹션에 docs/plans/README.md 추가 |
| memory `design-plan-docs-convention.md` | frontmatter + INDEX + postmortem 컨벤션 추가 |

## Out-of-scope (의도적 deferred)
- archive/ 디렉토리 이동 (Layer 3): 9개 → 20+ 되기 전엔 외부 참조 5개 깨지는 비용 > 정리 가치
- 자동 PR 상태 감지 (gh CLI): offline 동작 X + token 의존 → 사람이 수동 갱신
- RFC / postmortem 템플릿: 사례 누적 후 패턴화

## References
- 컨벤션 (frontmatter schema): design §5.1
- 기존 INC 패턴 재활용: schema-drift-recovery #47 의 entrypoint pattern
- 외부 참조 무파손 (파일 이동 0): CLAUDE.md / incident-log / migration-runbook / snapshot / CHANGELOG 모두 그대로

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

**Step 3: PR URL 사용자에게 보고**

---

## 잔여 리스크 / 후속 작업

1. **status 수동 갱신 부담** — PR template 에 "shipped 면 frontmatter status/pr 갱신했는지" 체크박스 추가 검토 (사용 빈도 보고 결정).
2. **Postmortem 미작성 위험** — 컨벤션 강제력 없음. 첫 PoC (schema-drift #47) 가 실제 작성되는지 관찰. 안 쓰면 컨벤션 재검토 또는 PR template 가드.
3. **신규 contributor 가 frontmatter 누락** — CI 가 차단하니 머지는 막힘. 인지 비용 ↑. 템플릿 안내로 완화.
4. **README 자동 생성이 manual override 막음** — `status: abandoned` 별도 그룹 표시 정도로만 대응. 더 복잡한 노출 제어는 후속.

## Postmortem

> (PR 머지 + 7일 후 채움. 계획과 다르게 갔던 점 / 발견된 새 위험 / 다음 plan 에 적용할 교훈.
>  없으면 "특이사항 없음" 1줄. 비워두지 말 것.)

## 참고

- Design 문서: `docs/plans/2026-05-28-plans-folder-maintenance-design.md`
- 페어 PR 참고: #47 (schema-drift-recovery, 같은 commit 흐름 + heredoc 패턴)
- Branch: `chore/plans-folder-maintenance`
