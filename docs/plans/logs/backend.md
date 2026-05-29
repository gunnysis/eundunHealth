# Backend 작업 로그

> 이 ledger 는 docs/plans/ 의 hybrid 구조 — Working 은 페어 파일, Completed 는 본 ledger 의 entry. 컨벤션: `docs/plans/README.md`.

## Recent (last 90 days)

### 2026-05-28 — Schema drift 복구 + Alembic 마이그레이션 자동화 (INC-2026-05-27-01)

- **PR**: [#47](https://github.com/gunnysis/eundunHealth/pull/47) (shipped, v0.1.4 → v0.1.5 사이 main hotfix)
- **Why**: INC-2026-05-27-01 — PUT /profile 500 (`column user_profiles.rest_day does not exist`) 으로 "운동 계획 받기" 서버 오류 발생. Root cause 2가지: (1) v0.3 에서 ORM `User.rest_day` 추가됐지만 실제 운영 DB 에 컬럼 누락 (Alembic 마이그레이션 파일 부재). (2) `backend.yml` 의 deploy job 이 Container App 이미지 push 만 하고 `alembic upgrade head` 자동 실행 안 함 → main 머지 + revision 추가만으로 운영 DB 반영 안 됨.
- **What**: alembic revision `fa3915deab2f` 신규 (`add_rest_day_to_user_profiles`). Docker 공식 entrypoint 패턴 (`backend/entrypoint.sh`) — Container Apps startup probe 진입 전 `alembic upgrade head` 동기 실행 → 멱등 + 멀티 인스턴스 race-safe (alembic_version row lock). CI `runtime-smoke` job 의 startup 로그에 entrypoint 메시지 + `/health` 200 검증으로 회귀 차단.
- **Outcome**: PUT /profile 500 해소. 이후 모든 스키마 변경 = main 머지만으로 운영 DB 자동 반영. CLAUDE.md 룰 7 (스키마 변경 PR = entrypoint 검증 포함) + PR template 가드 + `docs/ops/migration-runbook.md §3.4` (entrypoint 패턴 + 5분+ 백필은 Container Apps Jobs 로 분리) 등재.
- **Lessons**: ORM 모델 변경과 alembic 마이그레이션이 별도 단계라 동기 실패 가능 — pytest 가 SQLite 사용하면 UUID↔NUMERIC false positive 만들기도 함 (룰 3 — `scripts/alembic-autogen.sh` 의 PG 컨테이너 사용 필수). 5분 이상 데이터 백필은 startup probe timeout 위험 → Container Apps Jobs 패턴 검토 필요.
- **Files touched**: `backend/alembic/versions/fa3915deab2f_add_rest_day_to_user_profiles.py`, `backend/entrypoint.sh`, `backend/Dockerfile` (entrypoint 등록), `.github/workflows/backend.yml` (runtime-smoke 가드), `CLAUDE.md` (룰 7), `docs/ops/incident-log.md` (INC-2026-05-27-01), `docs/ops/migration-runbook.md` (§3.4), `.github/PULL_REQUEST_TEMPLATE.md`

## Older

(없음 — 모든 entry 가 last 90 days 이내)
