#!/usr/bin/env bash
# Backend OpenAPI 스펙을 backend/openapi.json으로 추출.
#
# 용도:
#   1) Android openapi-generator의 입력 스펙 (Phase 2~)
#   2) CI drift detection — 라우터 변경 후 이 파일 미커밋 시 backend.yml에서 fail
#
# 사용:
#   bash scripts/sync-openapi.sh
#
# 라우터/스키마를 변경했다면 이 스크립트를 돌리고 변경된 backend/openapi.json도 같은 PR에 커밋한다.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="${REPO_ROOT}/backend"

if [ -x "${BACKEND_DIR}/.venv/Scripts/python.exe" ]; then
  PY="${BACKEND_DIR}/.venv/Scripts/python.exe"   # Windows
elif [ -x "${BACKEND_DIR}/.venv/bin/python" ]; then
  PY="${BACKEND_DIR}/.venv/bin/python"            # Linux / macOS
else
  echo "ERROR: backend venv not found. 먼저 다음을 실행:" >&2
  echo "  cd backend && python -m venv .venv && .venv/Scripts/pip install -r requirements-dev.txt" >&2
  exit 1
fi

cd "${BACKEND_DIR}"
# OpenAPI 추출은 schema introspection만 하므로 실제 DB/Supabase 연결 없음.
# 단, Settings 검증 통과를 위해 dummy 값이 필요 (app.main이 모듈 로드 시 get_settings() 호출).
export DATABASE_URL="${DATABASE_URL:-postgresql+asyncpg://dummy:dummy@localhost/dummy}"
export SUPABASE_URL="${SUPABASE_URL:-https://dummy.supabase.co}"
export SUPABASE_SERVICE_ROLE_KEY="${SUPABASE_SERVICE_ROLE_KEY:-dummy}"

"${PY}" - <<'PY'
import json
from pathlib import Path

from app.main import app

out = Path("openapi.json")
spec = app.openapi()
# sort_keys=True: CI diff 안정성 — 라우터 등록 순서가 바뀌어도 동일 spec이면 동일 출력
# ensure_ascii=False: 한국어 description 사람이 읽을 수 있게
out.write_text(
    json.dumps(spec, indent=2, ensure_ascii=False, sort_keys=True) + "\n",
    encoding="utf-8",
)
print(f"OK {out.resolve()} ({out.stat().st_size} bytes, {len(spec.get('paths', {}))} paths)")
PY
