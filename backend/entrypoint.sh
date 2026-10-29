#!/bin/sh
set -e

# 다중 복제본(min=1, max=3) 동시 기동 시에도 PostgreSQL transaction advisory lock으로
# 직렬화되는 비파괴적 멱등 스키마 부트스트랩(Base.metadata.create_all)을 수행한다.
# 전체 스키마 초기화(Drop & Recreate)는 오직 scripts/hard_reset_db.py 로만 실행한다.
echo "[entrypoint] $(date -u +%FT%TZ) Ensuring database schema exists (python -m app.schema_bootstrap)"
python -m app.schema_bootstrap

# Docker 공식 권장: exec 로 PID 1 교체 → SIGTERM 이 uvicorn 에 직접 전달 → graceful shutdown.
echo "[entrypoint] $(date -u +%FT%TZ) starting: $*"
exec "$@"
