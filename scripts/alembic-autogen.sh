#!/usr/bin/env bash
# Alembic autogenerate against a real PostgreSQL container (NOT sqlite).
#
# 왜 필요한가:
#   alembic revision --autogenerate가 dialect mismatch 때문에 SQLite에서는
#   PostgreSQL native 타입(UUID, JSONB 등)을 잘못 비교해 거짓 alter_column 라인을
#   끼워넣는다. 그대로 프로덕션에 적용하면 `cannot cast type numeric to uuid` 등
#   런타임 에러 위험. 참조 인시던트: docs/ops/incident-log.md INC-2026-05-24-07.
#
# 동작:
#   1) backend/docker-compose.yml의 db 서비스(postgres:16-alpine) 기동.
#   2) DATABASE_URL을 해당 PG로 가리킨 채로 alembic upgrade head + revision --autogenerate.
#   3) (옵션) 종료 시 db 서비스를 내림. -k|--keep 플래그로 유지 가능.
#
# 사용법:
#   bash scripts/alembic-autogen.sh "add user_settings table"
#   bash scripts/alembic-autogen.sh -k "tweak indexes"   # db 컨테이너 유지
#
# 종속: docker, docker compose, backend/.venv (Python alembic).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BACKEND_DIR="${REPO_ROOT}/backend"
KEEP_DB=0
MESSAGE=""

while [ $# -gt 0 ]; do
    case "$1" in
        -k|--keep) KEEP_DB=1; shift ;;
        -h|--help)
            sed -n '2,20p' "$0"
            exit 0
            ;;
        *) MESSAGE="$1"; shift ;;
    esac
done

if [ -z "$MESSAGE" ]; then
    echo "ERROR: 마이그레이션 메시지가 필요합니다."
    echo "예: bash scripts/alembic-autogen.sh \"add user_settings table\""
    exit 1
fi

cd "$BACKEND_DIR"

# venv 활성화 헬퍼 (Windows/Unix 양쪽 지원)
if [ -f ".venv/Scripts/alembic" ]; then
    ALEMBIC=".venv/Scripts/alembic"
elif [ -f ".venv/bin/alembic" ]; then
    ALEMBIC=".venv/bin/alembic"
else
    echo "ERROR: backend/.venv가 없습니다. python -m venv .venv && pip install -r requirements-dev.txt"
    exit 1
fi

cleanup() {
    if [ "$KEEP_DB" -eq 0 ]; then
        echo ""
        echo "Tearing down db container (use -k to keep)..."
        docker compose down -v >/dev/null 2>&1 || true
    else
        echo ""
        echo "DB 컨테이너 유지됨 (docker compose down -v로 수동 정리)."
    fi
}
trap cleanup EXIT

echo "Starting postgres:16-alpine (docker compose up -d db)..."
docker compose up -d db >/dev/null

echo "Waiting for PG to accept connections..."
for i in 1 2 3 4 5 6 7 8 9 10; do
    if docker compose exec -T db pg_isready -U dev -d eundunhealth >/dev/null 2>&1; then
        echo "  ready (attempt $i)"
        break
    fi
    sleep 2
    if [ "$i" -eq 10 ]; then
        echo "ERROR: PG가 20초 안에 ready 되지 않음. docker compose logs db 확인."
        exit 1
    fi
done

# Alembic에 PG URL 주입 (async driver 사용)
export DATABASE_URL="postgresql+asyncpg://dev:devpass@localhost:5432/eundunhealth"
# 모듈 import 시 settings가 둘 다 요구 — placeholder로 안전 통과.
export SUPABASE_URL="${SUPABASE_URL:-https://placeholder.supabase.co}"
export SUPABASE_SERVICE_ROLE_KEY="${SUPABASE_SERVICE_ROLE_KEY:-placeholder}"

echo ""
echo "Step 1/2: alembic upgrade head (기존 마이그레이션 적용)..."
"$ALEMBIC" upgrade head

echo ""
echo "Step 2/2: alembic revision --autogenerate -m \"${MESSAGE}\"..."
"$ALEMBIC" revision --autogenerate -m "$MESSAGE"

echo ""
echo "완료. backend/alembic/versions/ 아래 새 파일 생성됨."
echo "  반드시 diff를 검토하고 false positive(alter_column 등)가 없는지 확인할 것."
echo "  검토 후: ./gradlew shadowJar 또는 redeploy.sh 흐름에서 자동 적용 안 되므로"
echo "  운영 적용은 docs/ops/monitoring-and-cost.md §6.3 패턴을 따른다."
