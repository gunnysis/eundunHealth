"""Generate docs/plans/README.md from frontmatter of docs/plans/*.md + topic ledgers.

Used by:
  - scripts/gen-plans-index.sh (shell wrapper for local + CI)
  - .githooks/pre-commit (auto-regenerate when docs/plans/*.md staged)
  - .github/workflows/docs-plans-index.yml (--check mode for drift detection)

Hybrid 구조 (2026-05-29 plans-ledger-restructure):
  - Working = pair files (docs/plans/*-design.md + *-plan.md) — 활성 작업, frontmatter status: proposed/in-progress
  - Completed = topic ledgers (docs/plans/logs/{android,backend,dependencies,process-infra}.md) — entry append
  - 본 script 는 매 호출 시 ledger 의 entry (### YYYY-MM-DD) 을 90일 기준 Recent/Older 자동 재정렬

D5: missing frontmatter (in pair files) = silent skip + stderr warn (multi-PR coordination 안전).
    malformed frontmatter = fail.
"""
from __future__ import annotations

import re
import sys
from collections import defaultdict
from datetime import date, timedelta
from pathlib import Path

import yaml

REPO_URL = "https://github.com/gunnysis/eundunHealth"


def _read_text_preserve_newlines(path: Path) -> str:
    # Path.read_text gained `newline` kwarg in Python 3.13; CI pins 3.12.
    with path.open(encoding="utf-8", newline="") as f:
        return f.read()


def _write_text_preserve_newlines(path: Path, text: str) -> None:
    with path.open("w", encoding="utf-8", newline="") as f:
        f.write(text)

REQUIRED_FIELDS = ["type", "status", "target_version", "tags"]
ALLOWED_TYPE = {"design", "plan", "rfc", "postmortem"}
ALLOWED_STATUS = {
    "proposed", "approved", "in-progress",
    "holding", "deferred",
    "shipped", "superseded", "abandoned",
}
# Display order (top to bottom in README)
STATUS_ORDER = [
    "in-progress", "proposed", "approved",
    "holding", "deferred",
    "shipped", "superseded", "abandoned",
]
STATUS_HEADING = {
    "in-progress": "In progress",
    "proposed": "Proposed",
    "approved": "Approved (not started)",
    "holding": "Holding",
    "deferred": "Deferred",
    "shipped": "Shipped",
    "superseded": "Superseded",
    "abandoned": "Abandoned",
}

# Topic ledgers (hybrid 구조 — 2026-05-29 plans-ledger-restructure)
LEDGER_DIR = "logs"
LEDGER_FILE_NAMES = ["android.md", "backend.md", "dependencies.md", "process-infra.md"]
LEDGER_TITLES = {
    "android.md": "Android",
    "backend.md": "Backend",
    "dependencies.md": "Dependencies",
    "process-infra.md": "Process & Infra",
}
RECENT_DAYS = 90

_FRONTMATTER_RE = re.compile(r"^---\r?\n(.*?)\r?\n---\r?\n", re.DOTALL)
_FILENAME_RE = re.compile(
    r"^(?P<date>\d{4}-\d{2}-\d{2})-(?P<topic>.+?)"
    r"(?:-design|-plan|-rfc|-postmortem)?$"
)
_ENTRY_HEADING_RE = re.compile(r"^### (\d{4}-\d{2}-\d{2}) — (.+?)$", re.MULTILINE)
_PR_LINK_RE = re.compile(r"\*\*PR\*\*: \[#(\d+)\]\(([^)]+)\)")


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
    """Walk plans_dir/*.md (skip README + _templates + logs/), validate, return records + errs.

    D5: missing frontmatter → silent skip + stderr warn (no error).
        malformed frontmatter → append to errs (caller fails).

    Note: logs/ 안의 topic ledger 파일은 별도 처리 (regenerate_ledger).
    """
    records: list[dict] = []
    all_errs: list[str] = []
    for path in sorted(plans_dir.glob("*.md")):
        if path.name == "README.md":
            continue
        if "_templates" in path.parts:
            continue
        if LEDGER_DIR in path.parts:
            continue  # topic ledger 는 별도 처리
        text = _read_text_preserve_newlines(path)
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
        date_str, topic = extract_date_topic(path.name)
        records.append({
            "date": date_str,
            "topic": topic,
            "filename": path.name,
            "type": fm["type"],
            "status": fm["status"],
            "pr": fm.get("pr"),
            "related_inc": fm.get("related_inc"),
            "tags": fm["tags"],
            "superseded_by": fm.get("superseded_by"),
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
            "superseded_by": rep.get("superseded_by"),
        })
    return out


def render_readme(records: list[dict]) -> str:
    """Legacy renderer (kept for test_gen_plans_index.py compatibility).

    Production main() uses render_readme_v2 (hybrid 구조).
    """
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


# ---------- Topic ledger handling (2026-05-29 hybrid restructure) ----------


def parse_ledger_entries(text: str) -> list[dict]:
    """Recent 섹션 안의 entry 들을 (date, title, full body) 로 추출.

    Older 섹션의 한 줄 요약은 별도 처리 (regenerate_ledger 의 split 단계).
    Returns: list of {date, title, body} dicts. Empty list if no Recent section.
    """
    recent_start = text.find("## Recent")
    older_start = text.find("## Older")
    if recent_start < 0:
        return []
    recent_end = older_start if older_start > 0 else len(text)
    recent_body = text[recent_start:recent_end]
    entries: list[dict] = []
    matches = list(_ENTRY_HEADING_RE.finditer(recent_body))
    for i, m in enumerate(matches):
        end = matches[i + 1].start() if i + 1 < len(matches) else len(recent_body)
        entries.append({
            "date": m.group(1),
            "title": m.group(2).strip(),
            "body": recent_body[m.start():end].rstrip() + "\n",
        })
    return entries


def split_recent_older(entries: list[dict], today: date) -> tuple[list[dict], list[dict]]:
    """90일 기준 Recent / Older 로 분류.

    Args:
        entries: list of {date, title, body}
        today: reference date (production = date.today(), testable)
    Returns:
        (recent, older) — 각각 sorted by date desc (caller's pre-sort 가 유지됨)
    """
    cutoff = today - timedelta(days=RECENT_DAYS)
    recent: list[dict] = []
    older: list[dict] = []
    for e in entries:
        try:
            entry_date = date.fromisoformat(e["date"])
        except ValueError:
            recent.append(e)  # malformed date → 안전하게 Recent 로
            continue
        if entry_date >= cutoff:
            recent.append(e)
        else:
            older.append(e)
    return recent, older


def compress_to_oneline(entry: dict) -> str:
    """Older entry 의 한 줄 요약.

    형식: '- YYYY-MM-DD title ([#N](url)) — Why 의 첫 문장'
    PR link 없거나 Why 없으면 가능한 부분만.
    """
    pr_match = _PR_LINK_RE.search(entry["body"])
    pr_part = f" ([#{pr_match.group(1)}]({pr_match.group(2)}))" if pr_match else ""
    why_match = re.search(r"\*\*Why\*\*:\s*([^\n.]+\.)", entry["body"])
    summary = f" — {why_match.group(1).strip()}" if why_match else ""
    return f"- {entry['date']} {entry['title']}{pr_part}{summary}\n"


def regenerate_ledger(path: Path, today: date) -> bool:
    """Recent 섹션의 entry 들을 90일 기준 재분류 + 정렬.

    1) Recent entry parse + 날짜 내림차순 (같은 날짜는 PR # 내림차순) 정렬
    2) cutoff 기준 Recent / Older split
    3) 새 Older 항목 = compress_to_oneline. 기존 Older 한 줄들 유지 + dedup
    4) 본문 재조립 (header / Recent 본문 / Older oneline)

    Returns: True if file content changed, False if no change (idempotent check).
    """
    text = _read_text_preserve_newlines(path)
    entries = parse_ledger_entries(text)
    if not entries:
        return False

    def sort_key(e: dict) -> tuple[str, int]:
        pr_match = _PR_LINK_RE.search(e["body"])
        pr_num = int(pr_match.group(1)) if pr_match else 0
        return (e["date"], pr_num)
    entries.sort(key=sort_key, reverse=True)
    recent, older = split_recent_older(entries, today)

    # 기존 Older 섹션의 oneline 들 보존
    existing_older_lines: list[str] = []
    older_start = text.find("## Older")
    if older_start > 0:
        for line in text[older_start:].splitlines():
            if line.startswith("- "):
                existing_older_lines.append(line.rstrip())

    new_older_oneline = [compress_to_oneline(e).rstrip() for e in older]
    # dedup + sort desc (date 첫 4단어 가 YYYY-MM-DD 라 string sort 가 date sort 와 일치)
    all_older = sorted(set(existing_older_lines + new_older_oneline), reverse=True)

    header_end = text.find("## Recent")
    if header_end < 0:
        return False  # 형식 미준수 — caller 가 별도 warning
    new_text = text[:header_end]
    new_text += "## Recent (last 90 days)\n\n"
    for e in recent:
        body = e["body"].rstrip() + "\n\n"
        new_text += body
    new_text += "## Older\n\n"
    if all_older:
        new_text += "\n".join(all_older) + "\n"
    else:
        new_text += "(없음 — 모든 entry 가 last 90 days 이내)\n"

    if new_text != text:
        _write_text_preserve_newlines(path, new_text)
        return True
    return False


def count_ledger_stats(plans_dir: Path) -> dict[str, tuple[int, int]]:
    """Return {filename: (recent_count, older_count)} for each ledger."""
    stats: dict[str, tuple[int, int]] = {}
    for fname in LEDGER_FILE_NAMES:
        ledger_path = plans_dir / LEDGER_DIR / fname
        if not ledger_path.exists():
            continue
        text = _read_text_preserve_newlines(ledger_path)
        recent = len(parse_ledger_entries(text))
        older = 0
        older_start = text.find("## Older")
        if older_start > 0:
            for line in text[older_start:].splitlines():
                if line.startswith("- "):
                    older += 1
        stats[fname] = (recent, older)
    return stats


ACTIVE_GROUPS = [
    ("진행 중", {"in-progress"}),
    ("대기 (proposed / approved)", {"proposed", "approved"}),
    ("보류 (holding / deferred)", {"holding", "deferred"}),
]


def render_readme_v2(
    active: list[dict],
    superseded: list[dict],
    plans_dir: Path,
) -> str:
    """v2 INDEX (hybrid 구조): 활성 페어 표 + topic ledger 요약 + superseded 섹션 + 워크플로 안내."""
    lines: list[str] = [
        "<!-- AUTO-GENERATED by scripts/gen-plans-index.sh — do not edit manually -->",
        "# docs/plans/ 인덱스",
        "",
        "> **hybrid 구조**: 활성 작업 = 페어 파일 (이 폴더 루트), 완료 작업 = topic ledger entry "
        f"(`{LEDGER_DIR}/android.md` / `backend.md` / `dependencies.md` / `process-infra.md`).",
        "> 컨벤션: 본 README 의 \"워크플로\" 섹션 + memory `plans-folder-archive-preference.md` + "
        "`design-plan-docs-convention.md`.",
        "> 본 INDEX 는 frontmatter 기반 자동 생성 — 직접 편집 X. 재생성: `bash scripts/gen-plans-index.sh`.",
        "",
    ]
    # 활성 작업 섹션 — status 그룹별 하위 섹션
    lines.append(f"## 활성 작업 (페어 파일, {len(active)})")
    lines.append("")
    if active:
        active_sorted = sorted(active, key=lambda r: r["date"], reverse=True)
        for group_label, group_statuses in ACTIVE_GROUPS:
            group_rows = [r for r in active_sorted if r["status"] in group_statuses]
            if not group_rows:
                continue
            lines.append(f"### {group_label} ({len(group_rows)})")
            lines.append("")
            lines.append("| 날짜 | 주제 | type | status | tags |")
            lines.append("|---|---|---|---|---|")
            for r in group_rows:
                tags_cell = ", ".join(r["tags"])
                lines.append(
                    f"| {r['date']} | {r['topic']} | {r['type']} | {r['status']} | {tags_cell} |"
                )
            lines.append("")
    else:
        lines.append("(없음 — 모든 작업이 ledger 에 흡수됨 또는 신규 작업 없음)")
        lines.append("")

    # Topic ledger 요약
    lines.append("## Topic Ledgers (완료 작업 history)")
    lines.append("")
    stats = count_ledger_stats(plans_dir)
    for fname in LEDGER_FILE_NAMES:
        if fname not in stats:
            continue
        recent_n, older_n = stats[fname]
        title = LEDGER_TITLES.get(fname, fname)
        lines.append(f"- [{title}]({LEDGER_DIR}/{fname}) — recent {recent_n}, older {older_n}")
    lines.append("")

    # Superseded 섹션
    if superseded:
        sup_sorted = sorted(superseded, key=lambda r: r["date"], reverse=True)
        lines.append(f"## Superseded ({len(sup_sorted)})")
        lines.append("")
        lines.append("| 날짜 | 주제 | superseded_by |")
        lines.append("|---|---|---|")
        for r in sup_sorted:
            sb = r.get("superseded_by") or "—"
            lines.append(f"| {r['date']} | {r['topic']} | {sb} |")
        lines.append("")

    # 워크플로 안내 (정적)
    lines.append("## 워크플로")
    lines.append("")
    lines.append("1. **새 작업 시작**: `_templates/{design,plan}.md` 복사 → `docs/plans/YYYY-MM-DD-<topic>-{design,plan}.md` 페어 작성. frontmatter 의 `ledger_topic` 필드에 `android` / `backend` / `dependencies` / `process-infra` 중 하나.")
    lines.append("2. **작업 진행**: 페어 파일 update. PR 작성 시 페어 link.")
    lines.append(f"3. **PR 머지 후**: 해당 topic ledger (`{LEDGER_DIR}/<ledger_topic>.md`) 의 `## Recent (last 90 days)` 섹션 맨 위에 압축 entry 추가 (~15-30줄). 페어 파일 `git rm`. 같은 commit 또는 후속 mechanical commit.")
    lines.append("4. **(자동)** `gen-plans-index.sh` 가 매 commit 시 ledger 의 Recent/Older 자동 재정렬 (90일 기준) + 본 INDEX 갱신.")
    lines.append("5. **(가드)** shipped frontmatter 인 페어가 루트에 남아있으면 CI fail (`docs-plans-index.yml`).")
    lines.append("")

    return "\n".join(lines) + "\n"


def main(argv: list[str]) -> int:
    check_only = "--check" in argv
    today = date.today()

    # 1) Ledger regeneration (Recent/Older 90일 기준 자동 분류)
    ledger_changed = False
    for fname in LEDGER_FILE_NAMES:
        ledger_path = PLANS_DIR / LEDGER_DIR / fname
        if not ledger_path.exists():
            continue
        if regenerate_ledger(ledger_path, today):
            ledger_changed = True

    # 2) Pair file scan + validation
    records, errs = collect_plans(PLANS_DIR)
    if errs:
        for e in errs:
            file_part = e.split(":", 1)[0]
            print(f"::error file={file_part}::{e}", file=sys.stderr)
        return 1

    # 3) Status 별 분류 — active / shipped_stragglers / superseded
    active_statuses = {"proposed", "in-progress", "approved", "holding", "deferred"}
    active = [r for r in records if r["status"] in active_statuses]
    shipped_stragglers = [r for r in records if r["status"] == "shipped"]
    superseded_rows = [r for r in records if r["status"] == "superseded"]

    # shipped 인데 ledger 로 흡수 안 된 페어 → stderr warning (CI 가드 separate)
    for r in shipped_stragglers:
        print(
            f"::warning file=docs/plans/{r['filename']}::shipped frontmatter 인 페어가 루트에 남아있음. "
            f"해당 topic ledger 에 entry 추가 후 git rm 필요. 컨벤션: docs/plans/README.md",
            file=sys.stderr,
        )

    grouped_active = group_pairs(active)
    grouped_superseded = group_pairs(superseded_rows)

    rendered = render_readme_v2(grouped_active, grouped_superseded, PLANS_DIR)

    if check_only:
        existing = (
            _read_text_preserve_newlines(README_PATH)
            if README_PATH.exists() else ""
        )
        if existing != rendered or ledger_changed:
            print(
                "::error::docs/plans/README.md or topic ledger out of sync.",
                file=sys.stderr,
            )
            print(
                "Fix locally: bash scripts/gen-plans-index.sh && "
                "git add docs/plans/ && git commit",
                file=sys.stderr,
            )
            return 1
        return 0
    _write_text_preserve_newlines(README_PATH, rendered)
    print(
        f"OK {README_PATH} (active: {len(grouped_active)}, "
        f"superseded: {len(grouped_superseded)}, ledger_changed: {ledger_changed})"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
