# Backend 작업 로그

> 이 ledger 는 docs/plans/ 의 hybrid 구조 — Working 은 페어 파일, Completed 는 본 ledger 의 entry. 컨벤션: `docs/plans/README.md`.

## Recent (last 90 days)

### 2026-06-16 — 감사 LOW 후속(백엔드): CORS 와일드카드 차단 + alembic rest_day server_default (v0.1.15)

- **PR**: [#123](https://github.com/gunnysis/eundunHealth/pull/123) (merged, squash `078a24fb`)
- **Why**: 감사 LOW — CORS 가 `["*"]` 잔존(네이티브 앱이라 웹 origin 불필요) + alembic fresh DB 분기에서 `rest_day` server_default 불일치.
- **What**: ③ alembic forward 마이그레이션 `c849579de6c4`(`user_profiles.rest_day` `server_default="7"` 일관화) + 모델 `server_default` 추가. ④ CORS 기본 `[]`(`config.py`) + `containerapp.yaml` `[]` → 와일드카드 제거. 회귀가드 `test_cors_does_not_allow_arbitrary_origin` + runtime-smoke(③ PG 검증).
- **Outcome**: 백엔드 자동배포 완료, CORS live 검증(임의 origin 에 `Access-Control-Allow-Origin` 미반환). **alembic head = `c849579de6c4`** (직전 `fa3915deab2f`). pytest 62 PASS.
- **Files touched**: alembic/versions/c849579de6c4_rest_day_server_default.py(신규), app/models/user_profile.py, app/config.py, containerapp.yaml, tests/(CORS)

---

### 2026-06-15 — 출시 준비 종합(백엔드): 입력검증 400·완료 정합성·manual 플래그 (v0.1.14)

- **PR**: [#122](https://github.com/gunnysis/eundunHealth/pull/122) (merged, squash `e2d7460`)
- **Why**: Android 토글 해제 보존(INC-2026-06-15-26) 의 서버측 + 전수감사 입력검증/정합성.
- **What**: `CompletionRequest.manual: bool` 추가 → `manual` 일 때만 day `manuallySet` 기록. `weekly_plan_service` 입력검증 `_parse_date`/`_validate_day_plans`/day-offset → `BadRequestException`(500→400). 통계 `_completion_rate` 를 `isCompleted` 기준 통일 + `!isRestDay` 카운트. `weekly_plan_repo.get_by_user_and_week(for_update)` 행잠금.
- **Outcome**: pytest green. `manual` live 배포 후 Flip3 e2e 검증(토글 해제 보존).
- **Files touched**: app/schemas/weekly_plan.py, app/services/{weekly_plan,statistics}_service.py, app/repositories/weekly_plan_repo.py

---

### 2026-06-11 — 코드베이스 리팩토링 Bundle B (백엔드 실버그/정리)

- **PR**: [#108](https://github.com/gunnysis/eundunHealth/pull/108) (merged, squash `3b1d2e5`)
- **Design/Plan**: `docs/plans/2026-06-11-codebase-refactoring-{design,plan}.md` (5-번들 이니셔티브 공통 페어 — process-infra/android ledger 와 함께 아카이브)
- **Why**: 백엔드 감사에서 실버그 2건 + stale 문서 식별 — (1) `dependencies.py` JWT 검증의 `except (InvalidTokenError, Exception)` 가 JWKS 장애·코드버그까지 401로 은폐 → 운영 장애 디버깅 불가. (2) `goal_repo.upsert` 가 신규 row flush/refresh 안 해 `created_at`(server_default) None → service 가 deprecated `datetime.utcnow()` 로 조작된 클라이언트 시각 반환. (3) `complete` 라우터 docstring 의 "배지 자동 부여"(코드에 없음).
- **What**: ① except 분리 — `InvalidTokenError`→401 / `PyJWKClientError`→503 / 나머지 전역핸들러(500+Sentry), `payload.get("sub")` None 가드(PyJWT 2.13.0 예외계층 기반). ② `goal_repo.upsert` flush+refresh(badge_repo 패턴), service fabrication 제거. ③ docstring 정정 + 라우터 docstring→OpenAPI description 이므로 `openapi.json` 재싱크 동봉.
- **Decisions**: 매핑 통일(model_validate)은 **비적용** — GoalResponse/ProfileHistoryEntry 의 date 필드가 `str` 타입이라 `model_validate(orm)` 시 datetime→str 검증 실패(Pydantic v2 미강제) → 수동 `str()` 유지가 정답.
- **Outcome**: ruff/mypy/bandit clean, pytest **54 passed**(49+신규 5: dependencies 4·goal_repo 1). CI(Lint/Type/Test·compose smoke·security) green. main 독립 머지(병렬).
- **Lessons**: 점검에서 **CI 차단 결함 사전 포착** — 라우터 docstring 변경이 OpenAPI description 을 바꾸므로 `sync-openapi.sh` + openapi.json 커밋 없으면 `backend.yml` drift 가드가 fail(plan 초안 누락 → 수정). date wire 포맷 위험은 연구로 해소(필드 `str`·Android `Instant.parse`→null·미표시).
- **Files touched**: backend/app/dependencies.py, backend/app/repositories/goal_repo.py, backend/app/services/goal_service.py, backend/app/routers/weekly_plan.py, backend/openapi.json, backend/tests/test_dependencies.py(신규), backend/tests/test_goal_repo.py(신규)
- **Postmortem**: (머지 + 7일 후.)

### 2026-05-28 — Schema drift 복구 + Alembic 마이그레이션 자동화 (INC-2026-05-27-01)

- **PR**: [#47](https://github.com/gunnysis/eundunHealth/pull/47) (shipped, v0.1.4 → v0.1.5 사이 main hotfix)
- **Why**: INC-2026-05-27-01 — PUT /profile 500 (`column user_profiles.rest_day does not exist`) 으로 "운동 계획 받기" 서버 오류 발생. Root cause 2가지: (1) v0.3 에서 ORM `User.rest_day` 추가됐지만 실제 운영 DB 에 컬럼 누락 (Alembic 마이그레이션 파일 부재). (2) `backend.yml` 의 deploy job 이 Container App 이미지 push 만 하고 `alembic upgrade head` 자동 실행 안 함 → main 머지 + revision 추가만으로 운영 DB 반영 안 됨.
- **What**: alembic revision `fa3915deab2f` 신규 (`add_rest_day_to_user_profiles`). Docker 공식 entrypoint 패턴 (`backend/entrypoint.sh`) — Container Apps startup probe 진입 전 `alembic upgrade head` 동기 실행 → 멱등 + 멀티 인스턴스 race-safe (alembic_version row lock). CI `runtime-smoke` job 의 startup 로그에 entrypoint 메시지 + `/health` 200 검증으로 회귀 차단.
- **Outcome**: PUT /profile 500 해소. 이후 모든 스키마 변경 = main 머지만으로 운영 DB 자동 반영. CLAUDE.md 룰 7 (스키마 변경 PR = entrypoint 검증 포함) + PR template 가드 + `docs/ops/migration-runbook.md §3.4` (entrypoint 패턴 + 5분+ 백필은 Container Apps Jobs 로 분리) 등재.
- **Lessons**: ORM 모델 변경과 alembic 마이그레이션이 별도 단계라 동기 실패 가능 — pytest 가 SQLite 사용하면 UUID↔NUMERIC false positive 만들기도 함 (룰 3 — `scripts/alembic-autogen.sh` 의 PG 컨테이너 사용 필수). 5분 이상 데이터 백필은 startup probe timeout 위험 → Container Apps Jobs 패턴 검토 필요.
- **Files touched**: `backend/alembic/versions/fa3915deab2f_add_rest_day_to_user_profiles.py`, `backend/entrypoint.sh`, `backend/Dockerfile` (entrypoint 등록), `.github/workflows/backend.yml` (runtime-smoke 가드), `CLAUDE.md` (룰 7), `docs/ops/incident-log.md` (INC-2026-05-27-01), `docs/ops/migration-runbook.md` (§3.4), `.github/PULL_REQUEST_TEMPLATE.md`

## Older

(없음 — 모든 entry 가 last 90 days 이내)
