#!/usr/bin/env bash
# doc_audit.py 로컬 wrapper — 문서 드리프트 감사를 실행한다.
#
# 인증: SDK auditor 경로는 이미 로그인된 Claude Code 세션의 구독 인증을 그대로 사용한다.
#       (CI 는 별도로 CLAUDE_CODE_OAUTH_TOKEN secret 을 쓴다 — .github/workflows/doc-audit.yml.)
# collector(--collect-only)는 SDK·인증이 필요 없다.
#
# 사용:
#   bash scripts/agents/doc-audit.sh                 # collector → SDK auditor → 사람용 리포트
#   bash scripts/agents/doc-audit.sh --collect-only  # 결정론적 사실만(SDK 불필요)
#   bash scripts/agents/doc-audit.sh --strict        # 드리프트 발견 시 exit 2
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# python 선택: 백엔드 venv(3.12) 우선 → PATH python3. (auditor 경로는 venv 에 claude-agent-sdk
# 설치 전제: pip install -r scripts/agents/requirements.txt)
PY="${REPO_ROOT}/backend/.venv/Scripts/python.exe"   # Windows venv
[ -x "$PY" ] || PY="${REPO_ROOT}/backend/.venv/bin/python"   # POSIX venv
[ -x "$PY" ] || PY="python3"

exec "$PY" "${SCRIPT_DIR}/doc_audit.py" "$@"
