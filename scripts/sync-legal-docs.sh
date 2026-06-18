#!/usr/bin/env bash
# 법적 고지(개인정보 처리방침 · 계정 삭제) 의 SSoT 는 docs/store/*.md 다.
# 백엔드 Docker 빌드 컨텍스트는 backend/ 라 repo 루트 docs/store/ 에 접근할 수 없으므로,
# 그 두 파일을 backend/app/legal/ 로 동기화한다(빌드 컨텍스트 안으로). 백엔드는 이 사본을
# 런타임에 HTML 로 렌더해 GET /privacy · GET /account-deletion 으로 서빙(Play Store 등록 URL).
#
# 정책 변경 시: docs/store/*.md 만 편집 → 본 스크립트 실행 → 같은 커밋에 backend/app/legal/ 포함.
# drift 가드: backend/tests/test_legal.py 가 두 사본의 동일성을 검증(미동기 시 pytest 실패).
# openapi.json 동기 패턴과 동일(sync-openapi.sh).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/docs/store"
DST="$ROOT/backend/app/legal"

mkdir -p "$DST"
for f in privacy-policy.md account-deletion.md; do
    cp "$SRC/$f" "$DST/$f"
    echo "synced: docs/store/$f -> backend/app/legal/$f"
done
