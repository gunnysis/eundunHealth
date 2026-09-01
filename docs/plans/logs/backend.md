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

## Older

- 2026-05-28 Schema drift 복구 + Alembic 마이그레이션 자동화 (INC-2026-05-27-01) ([#47](https://github.com/gunnysis/eundunHealth/pull/47)) — INC-2026-05-27-01 — PUT /profile 500 (`column user_profiles.
