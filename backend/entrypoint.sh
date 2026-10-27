#!/bin/sh
set -e

# Azure 공식 경고: min-replicas=0 + max=1 이어도 fail/update 시 중복 인스턴스 가능.
# Alembic은 자체 동시 실행 lock을 제공하지 않으므로 env.py가 PostgreSQL transaction advisory lock으로
# migration 실행을 직렬화한다.
# 이미 head 면 no-op (~1s). 새 revision 있으면 적용.
echo "[entrypoint] $(date -u +%FT%TZ) alembic upgrade head"
alembic upgrade head

# Docker 공식 권장: exec 로 PID 1 교체 → SIGTERM 이 uvicorn 에 직접 전달 → graceful shutdown.
# (Postgres / Rails 공식 이미지의 표준 패턴)
echo "[entrypoint] $(date -u +%FT%TZ) starting: $*"
exec "$@"
