# Backend 작업 로그

> 이 ledger 는 docs/plans/ 의 hybrid 구조 — Working 은 페어 파일, Completed 는 본 ledger 의 entry. 컨벤션: `docs/plans/README.md`.

## Recent (last 90 days)

### 2026-10-29 — Azure AI Foundry 식단 생성 모델 `gpt-5.4-mini` (100K TPM) 전환 & OpenAI Strict Structured Outputs 적용 (v0.7.2, commit `db8b4f8`)

- **Commit**: `db8b4f8` (PR #218 머지; 백엔드 배포 run `40838433732` / 활성 리비전 `eundunhealth-api--0000068` / 이미지 `db8b4f8`)
- **Why**: 기존 `ep-eundunhealth-deepseekv3` (`DeepSeek-V3.2`, `capacity: 1` = `1 RPM / 1K TPM`) 배포가 주간 식단 1회 생성(~2,200 토큰) 직후 재생성 또는 동시 요청 시 즉시 `429 RateLimitError`를 발생시키고 응답에 15~25초가 소요되던 병목을 근본 해결해야 했다.
- **What**:
  - **Azure AI Foundry `gpt-5.4-mini` 프로비저닝**: Korea Central `hub-eundunhealth-prod`에 `ep-eundunhealth-meal-ai` (`OpenAI` `gpt-5.4-mini` `2026-03-17`, `GlobalStandard`, `capacity: 100` = `100,000 TPM / 100 RPM`) 배포 및 레거시 `ep-eundunhealth-deepseekv3` 삭제. 인프라 IaC를 `scripts/infra/meal-ai-endpoint.bicep` 및 `deploy-meal-ai-endpoint.ps1`로 정본화.
  - **OpenAI Native Strict JSON Schema 적용**: `backend/app/services/meal_ai_client.py`에 `_WEEKLY_MEAL_JSON_SCHEMA`(`response_format={"type": "json_schema", "json_schema": {"name": "weekly_meal_plan", "strict": True, ...}}`) 및 `max_completion_tokens=4000`을 적용해 마크다운 코드펜스 오염과 스키마 불일치를 원천 차단.
- **Outcome**: 식단 생성 응답 시간 ~6.0초 단축 및 `100K TPM / 100 RPM` 처리량 확보, 백엔드 pytest 146/146 PASS, `eundunhealth-api--0000068` 및 `eundunhealth-reaper` 동기 배포 완료.
- **Files touched**: `backend/app/config.py`, `backend/app/services/meal_ai_client.py`, `backend/containerapp.yaml`, `backend/tests/test_meals.py`, `scripts/infra/{meal-ai-endpoint.bicep,deploy-meal-ai-endpoint.ps1,provision-ai-foundry.ps1,verify-meal-ai-e2e.ps1}`, `docs/store/privacy-policy.md`

### 2026-10-29 — Azure PostgreSQL 18 업그레이드 · 스키마 전면 리팩토링 및 하드 리셋(Drop & Recreate) 파이프라인 구축 (v0.7.0, commit 40595a1)

- **Commit**: `40595a1` (PR #213 release/v0.7.0 머지; 백엔드 배포 run `40816129146` / 활성 리비전 `eundunhealth-api--0000067` / 이미지 `40595a1`, 원장 PR #215)
- **Why**: 10단계 Alembic 점진적 마이그레이션 누적으로 인한 자식 테이블 FK 누락(고아 데이터 위험), UNIQUE-인덱스 중복 7건, DB 레벨 도메인 CHECK 제약 부재를 해소하고, 운영 Flexible Server(`healthapp`, PostgreSQL 18.4)와 로컬/CI Docker(`postgres:16-alpine`) 간의 2개 메이저 버전 드리프트를 해소하며, Alembic 점진 마이그레이션을 영구 폐기하고 완전한 Drop & Recreate(Hard Reset) 및 멱등 스키마 부트스트랩 체계로 전환해야 했다.
- **What**:
  - **PostgreSQL 18 엔진 동기화 & 튜닝**: 로컬/CI Docker Compose를 `postgres:18-alpine` 최신 다이제스트로 업그레이드하고 공식 PG 18 볼륨 경로(`/var/lib/postgresql`) 반영. Azure Flexible Server(`healthapp`)에 동적 파라미터 5종(`idle_in_transaction_session_timeout=60s`, `azure.extensions=PG_STAT_STATEMENTS`, `track_io_timing=on`, `log_min_duration_statement=1s`, `pg_stat_statements.track=top`) 적용.
  - **클린업 & 3NF 정규화 DDL**: `backend/sql/01_cleanup_drop_all.sql`(`public` 스키마 권한을 100% 보존하며 사용자 객체 전수 동적 DROP CASCADE) 및 `backend/sql/02_schema_create_all.sql`(9개 테이블 3NF 정규화, PostgreSQL 18 네이티브 `DEFAULT uuidv7()` PK, `ON DELETE CASCADE` 외래키, 도메인 CHECK 제약 11종 강제, 중복 인덱스 전량 제거).
  - **SQLAlchemy 2.0 ORM & 부트스트랩/하드 리셋 CLI**: `backend/app/models/*.py` 8개 파일 전면 재작성(`Uuid(as_uuid=True)`, `default=uuid.uuid7`, `POSTGRES_NAMING_CONVENTION`), 컨테이너 기동용 멱등 부트스트랩(`python -m app.schema_bootstrap`) 및 운영/개발 전용 파괴적 하드 리셋 CLI(`backend/scripts/hard_reset_db.py --confirmation DROP_AND_RECREATE_ALL_SCHEMAS`). Azure B1ms 맞춤 커넥션 풀(`pool_size=3, max_overflow=2, pool_recycle=1800`).
  - **100건 엣지케이스 Seeder & 음수 제약 검증**: `backend/scripts/seed_edge_cases.py`로 정확히 100건 정상 데이터 삽입 및 SAVEPOINT 기반 11개 제약조건 위반/CASCADE 차단 실증(`backend/tests/test_v2_reset.py`, 146 passed).
  - **레거시 Alembic 전면 폐기**: `backend/alembic/**`, `backend/alembic.ini`, `scripts/alembic-autogen.sh`, `backend/scripts/reset_v2_user_data.py` 완전 삭제 및 `alembic` 패키지 의존성 전면 제거 후 해시 락 갱신.
- **Outcome**: 로컬 및 운영 Azure Flexible Server(`healthapp`) 라이브 하드 리셋 완수(클린 9개 테이블 및 100건 엣지케이스/11개 음수 제약 실증 완료), 백엔드 테스트 146/146 PASS, Zero-Legacy Grep Gate 통과, uvicorn 컨테이너 리비전 `0000067` 정상 가동.
- **설계 원문**: `2026-10-29-postgresql-schema-hard-reset-plan.md` (본 entry 로 흡수, git rm)

### 2026-10-27 — 적응형 주간 운동 계획 v2(`/v2/weekly-plan`) · 다중 휴식 요일(`rest_days` `INTEGER[]`) · KST 시간대 SSoT 및 운영 컷오버 (v0.6.0, commit `a0b6701`)

- **Commit**: `a0b6701` (백엔드 배포 run `40469877206` / 활성 리비전 `eundunhealth-api--0000065` / 이미지 `a0b6701`)
- **Why**: 주간 운동 계획에 `plan_mode`(`gym` / `home`)와 주중 불변성(동일 주 중복 생성 `409 weekly_plan_exists`), 손상 계획 복구용 `DELETE /v2/weekly-plan` 계약을 도입하고, 고정 단일 휴식일(`rest_day`)을 제거해 주 1~6일 다중 휴식 요일(`rest_days`)을 지원하며, 구버전(`<=38`) 클라이언트의 프로필 덮어쓰기와 KST 시간대 드리프트를 원천 차단해야 했다.
- **What**:
  - **Alembic `d7f4a2c91b10` & DB 제약**: `weekly_plans.plan_mode`(`'gym'`/`'home'` CHECK 제약), `user_profiles.default_workout_plan_mode`(`'gym'`/`'home'` nullable CHECK 제약), `user_profiles.rest_days`(PostgreSQL `INTEGER[]` + `ck_user_profiles_rest_days_valid`: `cardinality BETWEEN 1 AND 6`, `1..7` 범위, 중복 금지; SQLite 테스트 호환 `ARRAY(Integer).with_variant(JSON, "sqlite")`), `data_reset_audits` 감사 테이블 추가 및 레거시 `rest_day` 컬럼 제거.
  - **v2 API 계약 & 클라이언트 최소 버전 가드**: `/v2/weekly-plan` 7개 라우트(`GET`, `POST` `INSERT ... ON CONFLICT DO NOTHING RETURNING` 기반 원자적 201/409, `DELETE` 204, `PATCH /complete`, `GET /history`, `GET /previous`, `GET /statistics`), 신체 계측 이력을 오염시키지 않는 `PUT /v2/profile/default-workout-plan-mode`, `GET /app-compatibility` 공개 라우트 및 `require_supported_android_client`(`MINIMUM_SUPPORTED_ANDROID_VERSION_CODE=39`, `PUT /profile` 포함) 구현.
  - **KST(`UTC+9`) SSoT 일원화 & 타입 안전성**: `backend/app/time_utils.py`(`KST`, `normalize_utc`, `today_kst`, `current_monday_kst`)를 신설해 `meal_plan.py`의 UTC 월요일 오판 버그를 수정하고, `meal_plan_service.py` 강타입 모델(`DailyMealPlan`/`MealInfo`) 및 `statistics_service.py` `day_plans` 단일 패스 파싱 적용.
  - **운영 C1/C2 완수**: `backend/scripts/reset_v2_user_data.py`로 운영 DB 1회 초기화(`resetId=v2-cutover-20261027`) 및 `verify_containerapp_deployment.py` 공개 라우트 5종 프로브 검증 후 `eundunhealth-api--0000065` + `eundunhealth-reaper` 동기 배포 완료.
- **Outcome**: Backend pytest 140/140 PASS (`ruff`/`mypy`/`bandit`/`pip-audit` clean), 운영 프로브 5종 200 및 버전 호환성 가드 실측 검증 완료.
- **설계 원문**: `docs/reports/2026-10-26-adaptive-weekly-workout-proposal.md`, `2026-10-27-flexible-rest-days-design.md` (`docs/plans/logs/android.md` 2026-10-27 entry 와 공동 흡수, git rm)

### 2026-09-20 — 식단 자동 생성 기능 설계 및 구현 명세 (DeepSeek-V3.2 MaaS)

- **버전**: v0.3.0
- **설계 원문**: `2026-09-20-meal-plan-api-selection-design.md` (본 entry 로 흡수, git rm)
- **Why**: 은둔헬스 사용자 맞춤 주간 식단 자동 생성 기능을 추가하여 운동 효과를 극대화. API 선정 및 Azure 인프라 연동 방식을 확정하고 구체적 구현 명세를 정립.
- **What**:
  - **AI 모델 및 호스팅**: DeepSeek-V3.2 채택(가성비 및 추론 성능). Azure AI Foundry MaaS(Serverless) Global Standard로 배포, 워크스페이스는 Korea Central 유지.
  - **IaC 및 보안**: Bicep(ARM Template) 기반 `Microsoft.CognitiveServices/accounts/deployments` 배포. Azure Key Vault 연동을 통해 Managed Identity로 `DEEPSEEK_ENDPOINT`, `DEEPSEEK_KEY` 로드.
  - **백엔드 (FastAPI)**: `openai` SDK 기반 비동기 클라이언트 연동. JSON Mode + Pydantic 스키마(`WeeklyMealPlanResponse`) 주입으로 Structured Outputs 안정성 확보. `POST /api/v1/meals/generate` 엔드포인트 정의 및 에러(Timeout/Quota/Validation) 분리 핸들링.
  - **클라이언트 (Android)**: Compose UI(식단 요약 말풍선, 요일별 아코디언, 매크로 프로그레스 바) 및 Hilt/Retrofit 연동.
- **Outcome**: 식단 생성 인프라 프로비저닝 완료 및 백엔드/클라이언트 아키텍처 확정.

## Older

- 2026-06-16 감사 LOW 후속(백엔드): CORS 와일드카드 차단 + alembic rest_day server_default (v0.1.15) ([#123](https://github.com/gunnysis/eundunHealth/pull/123)) — 감사 LOW — CORS 가 `["*"]` 잔존(네이티브 앱이라 웹 origin 불필요) + alembic fresh DB 분기에서 `rest_day` server_default 불일치.
- 2026-06-15 출시 준비 종합(백엔드): 입력검증 400·완료 정합성·manual 플래그 (v0.1.14) ([#122](https://github.com/gunnysis/eundunHealth/pull/122)) — Android 토글 해제 보존(INC-2026-06-15-26) 의 서버측 + 전수감사 입력검증/정합성.
- 2026-06-11 코드베이스 리팩토링 Bundle B (백엔드 실버그/정리) ([#108](https://github.com/gunnysis/eundunHealth/pull/108)) — 백엔드 감사에서 실버그 2건 + stale 문서 식별 — (1) `dependencies.
- 2026-05-28 Schema drift 복구 + Alembic 마이그레이션 자동화 (INC-2026-05-27-01) ([#47](https://github.com/gunnysis/eundunHealth/pull/47)) — INC-2026-05-27-01 — PUT /profile 500 (`column user_profiles.
