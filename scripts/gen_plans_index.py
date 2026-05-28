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
from pathlib import Path

import yaml

REQUIRED_FIELDS = ["type", "status", "target_version", "tags"]
ALLOWED_TYPE = {"design", "plan", "rfc", "postmortem"}
ALLOWED_STATUS = {
    "proposed", "approved", "in-progress",
    "shipped", "superseded", "abandoned",
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
