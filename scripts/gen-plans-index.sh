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
