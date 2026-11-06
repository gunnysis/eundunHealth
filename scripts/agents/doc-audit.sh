#!/usr/bin/env bash
# doc_audit.py 로컬 wrapper — 문서 일관성 감사를 실행한다 (결정론적 린터).
#
# 본 스크립트는 외부 AI 종속성 없이 Python 표준 라이브러리(stdlib)만으로 100% 결정론적으로
# 동작합니다.
#
# 사용:
#   bash scripts/agents/doc-audit.sh                 # 사실 수집 + 6대 문서 감사 리포트 (advisory)
#   bash scripts/agents/doc-audit.sh --collect-only  # 결정론적 사실만 JSON 출력
#   bash scripts/agents/doc-audit.sh --strict        # 드리프트 발견 시 exit 2 (게이트용)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# python 선택: 백엔드 venv 우선 → PATH python3
PY="${REPO_ROOT}/backend/.venv/Scripts/python.exe"   # Windows venv
[ -x "$PY" ] || PY="${REPO_ROOT}/backend/.venv/bin/python"   # POSIX venv
[ -x "$PY" ] || PY="python3"

exec "$PY" "${SCRIPT_DIR}/doc_audit.py" "$@"
