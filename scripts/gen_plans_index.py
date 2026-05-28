"""Generate docs/plans/README.md from frontmatter of docs/plans/*.md.

Used by:
  - scripts/gen-plans-index.sh (shell wrapper for local + CI)
  - .githooks/pre-commit (auto-regenerate when docs/plans/*.md staged)
  - .github/workflows/docs-plans-index.yml (--check mode for drift detection)

Convention: see docs/plans/2026-05-28-plans-folder-maintenance-design.md §5.1.

D5: missing frontmatter = silent skip + stderr warn (multi-PR coordination 안전).
    malformed frontmatter = fail.
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

_FRONTMATTER_RE = re.compile(r"^---\r?\n(.*?)\r?\n---\r?\n", re.DOTALL)
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
        text = path.read_text(encoding="utf-8", newline="")
        fm = parse_frontmatter(text)
        try:
            display_path = path.relative_to(REPO_ROOT)
        except ValueError:
            display_path = path
        if fm is None:
            print(
                f"::warning file={display_path}::skipping (no frontmatter)",
                file=sys.stderr,
            )
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
    for _key, group in by_key.items():
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
    return "\n".join(lines) + "\n"


def main(argv: list[str]) -> int:
    check_only = "--check" in argv
    records, errs = collect_plans(PLANS_DIR)
    if errs:
        for e in errs:
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
    README_PATH.write_text(rendered, encoding="utf-8", newline="")
    print(f"OK {README_PATH} ({len(grouped)} entries)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
