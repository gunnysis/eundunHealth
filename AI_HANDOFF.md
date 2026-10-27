# Multi-AI Handoff Log

## 2026-10-27 (Gemini -> Claude/User) 사용자 작업(GitHub Ruleset, Actions 권한, SSH 서명) 전수 검증 및 점검 100% 완결

**현재까지의 작업 상태 (Current Status):**
- **사용자 수동 설정 항목(GitHub Ruleset, Actions PR 권한, SSH 서명 키) 전수 검증 및 완결 100% 통과**:
  1. **Actions PR 생성 권한**: `default_workflow_permissions: write`, `can_approve_pull_request_reviews: true` 정상 활성화 (PASS ✅).
  2. **GitHub 태그 보호 규칙 (Ruleset `default tag`, id 25954171)**: `Restrict creations` 해제 완료(`rules: [deletion, non_fast_forward, update]`). 신규 `v*` 릴리스 태그 생성 및 변조 방지 정상 동작 (PASS ✅).
  3. **GitHub main 보호 및 Repository Gate 필수 검사 (Ruleset `default branch`, id 25954096)**: `required_status_checks: [{"context": "Repository Gate"}]` 정상 지정 완료. PR 필수 검사 통과 강제 (PASS ✅).
  4. **SSH 서명 키 (GitHub 원격 & 로컬 Git)**:
     - GitHub 계정(`gunnysis`)에 `id_ed25519.pub` 서명 키(id 1276982) 등록 완료.
     - 로컬 Git: `gpg.format=ssh`, `user.signingkey=C:/Users/Administrator/.ssh/id_ed25519.pub`, `tag.gpgSign=true`, `allowed_signers` 등록 완료.
     - 실측 태그 서명(`git tag -s`) 및 검증(`git tag -v`) 결과: `Good "git" signature for qkr133456@gmail.com with ED25519 key ...` 완벽 통과 (PASS ✅).

**다음 할 일 (Next TODOs for User / Operator):**
- Codex가 준비한 v0.6.1/40 로컬 변경사항(의존성 갱신, lockfile, hash lock, verification-metadata, repository-gate.yml 등) 검토 및 PR 생성/머지 진행.
- 차기 릴리스 준비 시 SSH 서명 태그(`git tag -s v0.6.1 -m "..."`) 부착 및 푸시 진행.

---

## 2026-10-27 — v0.6.1/40 로컬 릴리스 후보·패키지/공급망 강화

- 저장소 SSoT를 `versionName=0.6.1`, `versionCode=40`으로 올렸지만 **태그·push·Play 업로드·Azure 배포는 하지 않았다**. 운영 기준은 계속 Play `v0.6.0/39`, backend revision `0000065` / image `a0b6701`이다.
- 휴식 요일 UI를 `FlowRow` 기반 48dp `FilterChip`으로, 프로필 요약을 줄바꿈 가능한 적층 레이아웃으로 개선하고 semantics/live-region 및 instrumentation 회귀 테스트를 추가했다.
- Android/Gradle 및 Python 의존성을 갱신하고 Gradle lock/verification metadata, Python hash lock, container digest, Action SHA pin, CODEOWNERS, Docker Dependabot, Repository Gate를 적용했다.
- release workflow는 Play 성공 후 `main`에 원장을 직접 push하지 않고 `release-ledger/v<version>-<code>` PR을 생성한다. GitHub 보호 규칙·Actions PR 권한·SSH tag signing·Play/Azure·실기기 검증은 사용자 승인/외부 gate로 남아 있다.
- 로컬 검증은 Android Spotless/AGP guard/Detekt/unit/lint/debug/androidTest compile/R8 release, Backend Ruff/Mypy/pytest 140/pip-audit/Bandit, Docker build, version monotonic 및 workflow SHA 검증을 통과했다. 연결 기기가 없어 instrumentation 실행은 미완료다.

---

## 2026-10-27 (Gemini -> Claude/User) 휴식일 선택기(`RestDaysSelector`) 고정 프리셋 제거 및 미니멀 직관 UI·프로필 요약 카드 레이아웃 고도화 완료

**현재까지의 작업 상태 (Current Status):**
- **사용자 요청 반영: 휴식요일 고정 프리셋 제거 및 레이아웃 개선 완수**:
  1. **`RestDaysSelector.kt`**:
     - `RestDayPreset`, `REST_DAY_PRESETS`, `FlowRow` 및 `FilterChip` 프리셋 블록 완전 제거.
     - 상단 헤더에 실시간 주간 운동·휴식 리듬 배지(`Surface(primaryContainer)` → `"운동 N일 · 휴식 M일"`) 신설.
     - 세그먼트 버튼 선택 요일에 `FontWeight.Bold` 강조 부여.
     - 하단에 "휴식일 (토·일)" 및 "운동일 (월·화·수·목·금)"을 직관적으로 보여주는 미니 인포 스트립(`Surface(surfaceVariant)`)과 아이콘 기반의 `errorContainer` 유효성 안내 배너를 추가.
     - Compose `@Preview` 지원.
  2. **`ProfileSummaryCard.kt`**:
     - 단순 텍스트 줄바꿈 대신 키/몸무게, 골격근량/체지방률, 주간 루틴을 좌우 정렬한 현대적인 대시보드 카드 레이아웃(`RoundedCornerShape(12.dp)`, `surfaceVariant` 65% alpha)으로 개선.
  3. **문서 동기화**:
     - `docs/plans/logs/android.md`, `docs/CHANGELOG.md`, `AI_HANDOFF.md` 동기화.
  4. **품질 검증**:
     - Android `spotlessCheck` PASS, `detektDebug` PASS (0 issues), `testDebugUnitTest` PASS (186 tests).

**다음 할 일 (Next TODOs for User / Operator):**
- 필요 시 차기 릴리스(v0.6.1 또는 v0.7.0) 버저닝 및 배포 진행.

**의존성 및 주의사항 (Context & Constraints):**
- git stash 목록에 사용자의 `autostash`(gradlew.bat 줄바꿈 변경)가 있으니 건드리지 마세요.

---

## 2026-10-27 (Gemini -> Claude/User) v0.6.0 (`versionCode=39`) 운영 배포(C1 ➔ C2 ➔ C3) 전 구간 성공 및 문서 SSoT 정합화 완료

**현재까지의 작업 상태 (Current Status):**
- **v0.6.0 (`versionCode=39`) 운영 컷오버 (C1 ➔ C2 ➔ C3) 100% 완수**:
  1. **C1 — 운영 DB 마이그레이션(`d7f4a2c91b10`) 및 v2 사용자 데이터 초기화(`reset_v2_user_data.py`)**:
     - Azure PostgreSQL Flexible Server(`healthapp`, `rg-eundunhealth-prod-krc`)에 임시 방화벽 규칙(`temp-v2-reset`)을 생성하여 `alembic upgrade head`(`d7f4a2c91b10`: `weekly_plans.plan_mode`, `user_profiles.default_workout_plan_mode`, `user_profiles.rest_days` `INTEGER[]` + 체크 제약, `data_reset_audits` 테이블) 적용 완료.
     - `--dry-run` 점검 후 `--confirmation RESET_ALL_V2_USER_DATA --reset-id v2-cutover-20261027 --image-sha 4562506 --execution-id prod-cutover-20261027` 실행: `{"deletedRows": {"badges": 0, "goals": 0, "meal_plan_items": 95, "meal_plans": 6, "smile_training_sessions": 0, "user_profile_history": 6, "user_profiles": 3, "weekly_plans": 11}, "resetId": "v2-cutover-20261027"}` (`data_reset_audits` 감사 기록 완료).
     - 작업 직후 `temp-v2-reset` 방화벽 규칙 즉시 삭제(잔존 규칙 `allow-azure-services` 1개만 유지).
  2. **C2 — 백엔드 CI/CD 운영 배포 (`backend.yml` run `40469877206`, commit `a0b6701`)**:
     - CI/CD 배포 환경 개선: Gitleaks 8.24.3 오탐(`WorkoutRepositoryImplTest.kt`의 `WeeklyPlanV2Api` 타입명)을 `.gitleaksignore`/`.gitleaks.toml`로 해결하고, `scripts/verify_containerapp_deployment.py`에 `/app-compatibility?platform=android&versionCode=39` 프로브를 추가하여 공개 라우트 5종(`/health`, `/health/ready`, `/app-compatibility`, `/privacy`, `/account-deletion`)을 매 배포마다 자동 검증하도록 강화.
     - `backend-production` 환경 승인 후 `eundunhealth-api` 활성 리비전 **`eundunhealth-api--0000065`** 및 `eundunhealth-reaper` Job을 이미지 `eundunhealthacr.azurecr.io/eundunhealth-api:a0b6701`로 동기 배포 완료.
     - 라이브 엔드포인트 실측 검증: `/health` (`200`), `/health/ready` (`200`), `/app-compatibility?platform=android&versionCode=38` (`updateRequired: true`, `minimumSupportedVersionCode: 39`), `/app-compatibility?platform=android&versionCode=39` (`updateRequired: false`), 무토큰 `/v2/weekly-plan` (`401`).
  3. **C3 — Android Google Play 프로덕션 릴리스 (`release.yml` run `40469947190`, 태그 `v0.6.0`)**:
     - 커밋 `a0b6701`에 태그 `v0.6.0` 부착 및 push ➔ `play-release` 환경 승인 ➔ `Preflight (룰 2)` 통과 ➔ Google Play **production 트랙 `completed`(100%) 업로드 성공**.
     - `release.yml`이 `docs/ops/play-upload-ledger.md`를 `LAST_UPLOADED_VERSION_CODE=39`로 자동 갱신(커밋 `f9eeefc`).
  4. **문서 거버넌스 및 SSoT 정합화**:
     - 출시 완료된 설계서 `docs/plans/2026-10-27-flexible-rest-days-design.md`를 `docs/plans/logs/android.md` (`2026-10-27` entry)로 흡수 후 `git rm`, `docs/plans/README.md` 인덱스 재생성 및 링크 가드 통과.
     - `docs/ops/operations-snapshot.md`, `CLAUDE.md`, `README.md`, `docs/PRD.md`, `docs/TRD.md`, `docs/SPEC.md`, `docs/CHANGELOG.md`, `docs/proposals/README.md` 최신 운영 상태(`v0.6.0/39`, 리비전 `0000065`, 이미지 `a0b6701`, 원장 `f9eeefc`) 동기화 완료.

**다음 할 일 (Next TODOs for User / Operator):**
- **실기기 사후 점검 (Post-release Operational Verification)**:
  - Play 스토어 프로덕션 트랙에서 `v0.6.0` (`versionCode=39`) 업데이트 배포본 설치 후 로그인, 온보딩/프로필 다중 휴식 요일(`restDays`) 선택, `헬스장` / `홈트레이닝` 주간 계획 생성, TalkBack 및 Health Connect 동작 확인.

**의존성 및 주의사항 (Context & Constraints):**
- 다음 릴리스 버전 코드(`versionCode`)는 최소 `40` 이상이어야 합니다 (`LAST_UPLOADED_VERSION_CODE=39`, 룰 13).
- git stash 목록에 사용자의 `autostash`(gradlew.bat 줄바꿈 변경)가 있으니 건드리지 마세요.

---

## 2026-10-27 (Gemini -> Claude/User) Senior Programmer / Architecture Lead / CTO 프로젝트 전체 시점 리팩토링·결함 근본 해결·UI/UX 고도화 완료 (운영 배포 요청 대기)

**현재까지의 작업 상태 (Current Status):**
- **어제(2026-10-26: 스마일 트레이닝 v0.5.0 + R8 ML Kit 핫픽스) 및 오늘(2026-10-27: 적응형 주간 운동 계획 v2 + 다중 휴식 요일 Zero-Legacy) 전 작업에 대한 CTO/아키텍처 전수 감사 및 리팩토링 완수**:
  1. **[P0 운영 차단 결함 근본 해결] 클라이언트 최소 지원 버전 드리프트(`38` → `39`) 및 구버전 프로필 덮어쓰기 차단**:
     - `backend/containerapp.yaml` (`MINIMUM_SUPPORTED_ANDROID_VERSION_CODE: '39'`) 및 `backend/app/config.py` (`minimum_supported_android_version_code: int = 39`)를 `39`로 정합화.
     - `PUT /profile` (`backend/app/routers/profile.py`)에 `Depends(require_supported_android_client)` 버전 가드를 추가하여 구버전(`<=38`) 앱이 `restDays` 미포함 요청으로 다중 휴식 요일을 기본값 `[7]`로 덮어쓰는 데이터 유실 경로를 원천 차단 + 회귀 테스트(`test_update_profile_blocks_unsupported_client_from_resetting_rest_days`) 추가.
  2. **[백엔드 아키텍처 & KST 시간대 SSoT 일원화]**:
     - `backend/app/time_utils.py` 신설 (`KST`, `normalize_utc`, `today_kst`, `current_monday_kst`).
     - `backend/app/routers/meal_plan.py`에서 컨테이너 UTC `datetime.date.today()` 사용으로 인해 매주 월요일 00:00~09:00 KST(9시간) 동안 지난주 월요일로 오판하던 버그를 `current_monday_kst()`로 근본 해결하고, `parse_day_plans_or_none` 및 `not dp.get("isRestDay")` 명시적 필터로 정합화.
     - `backend/app/services/smile_training_service.py` 및 `backend/app/routers/weekly_plan.py`의 중복 KST 계산을 `app.time_utils`로 통합.
     - `backend/app/services/meal_plan_service.py`의 비타입 `dict` 반환을 `DailyMealPlan`/`MealInfo` 강타입 모델로 전환하고, `backend/app/services/statistics_service.py`의 `day_plans` JSON 이중 파싱을 제거하여 단일 패스로 최적화.
  3. **[Android 도메인 SSoT 일원화 & ViewModel 상태 불변식 수정]**:
     - 5개 파일에 중복 산재해 있던 요일/휴식일 상수(`DEFAULT_REST_DAY`, `MIN_DAY_OF_WEEK`, `MAX_DAY_OF_WEEK`, `MAX_REST_DAYS`, `TOTAL_DAYS_IN_WEEK`) 및 정규화 함수(`normalizeRestDays`)를 `domain/model/UserProfile.kt` 단일 진실 공급원(SSoT)으로 통합.
     - `ProfileViewModel.saveProfile()`에서 성공 시 조기 `return@launch`로 인해 `ProfileUiState.Loaded`의 `profile` 및 `isSaving = false` 상태가 갱신되지 않던 도달 불능 코드(dead code) 결함을 수정하고 `normalizeRestDays` 도메인 정규화를 적용.
     - `HistoryViewModel`에 `fun refresh()`를 신설하고 `HistoryScreen`의 `PullToRefreshBox`에 연결하여, 전체 기록이 1페이지 이하(`hasMore == false`)일 때 당겨서 새로고침이 무시되던 버그를 해결 (`HistoryViewModelTest` 회귀 테스트 추가).
  4. **[UI/UX 레이아웃 및 사용자 안내 고도화]**:
     - `ProfileSummaryCard.kt`: `restDays` 전달 시 `"휴식 요일: 주 N일 (...) | 주 M일 운동"` 요약을 하단에 표시하도록 개선하고 `OnboardingScreen`·`ProfileScreen`에 연동.
     - `ProfileScreen.kt`: `rememberSaveable`에 초기 프로필 키 및 `ArrayList` 직렬화 안전성을 부여하고, `"휴식 요일 및 신체 정보 변경은 다음 주 계획 생성부터 반영되며, 이미 생성된 이번 주 운동 계획은 유지됩니다."` 안내 문구를 추가.
     - `WorkoutDetailScreen.kt`: `gifUrl.isBlank()`인 홈트레이닝 무장비 18종 운동 진입 시 `HomeExerciseGuideCard`(무장비 홈트레이닝 수행 가이드 배너)를 표시하도록 UX 개선.
  5. **[전수 품질 게이트 및 문서 SSoT 정합화 검증]**:
     - Backend: `ruff check` PASS, `ruff format --check` PASS, `mypy` (57 files) PASS, `pytest` **140 passed** (19 files).
     - Android: `spotlessCheck` PASS, `detektDebug` PASS (0 issues), `testDebugUnitTest` **186 `@Test` passed** (36 files), `assembleDebug` & `assembleRelease` (R8 full mode + `lintVitalRelease`) PASS.
     - Zero-Legacy 가드: `backend/app/`, `backend/tests/`, `backend/openapi.json`, `app/src/` 내 `rest_day` / `restDay` **0건**.

**다음 할 일 (Next TODOs for User / Operator):**
- 사용자의 운영 배포 요청 시 **C1(운영 DB `reset_v2_user_data.py` 실행) → C2(백엔드 ACA 배포 및 Alembic `d7f4a2c91b10` 적용) → C3(Android `v0.6.0`/`39` Play 프로덕션 릴리스)** 순서로 진행.

**의존성 및 주의사항 (Context & Constraints):**
- 운영 배포는 사용자의 명시적 배포 요청 후 실행합니다.
- git stash 목록에 사용자의 `autostash`(gradlew.bat 줄바꿈 변경)가 있으니 건드리지 마세요.

---

## 2026-10-27 (Gemini -> Claude/User) 휴식일 자유 다중 지정(Flexible Multi-Rest Days) 통합 구현 설계서(design) 작성 및 인덱스 정합화 완료 (사용자 검토 대기)

**현재까지의 작업 상태 (Current Status):**
- **단일 완성형 설계서 수립 (`docs/plans/`)**:
  - `docs/plans/logs/android.md` (`2026-10-27` entry로 흡수된 `2026-10-27-flexible-rest-days-design.md`) 단일 문서에 아키텍처, 알고리즘, UI/UX, 4단계 구현 로드맵 및 사용자 필수 작업을 완벽히 통합·병합 완료.
  - **기술 팩트체크 및 최적안 확정**:
    - **백엔드**: SQLAlchemy `RestDaysType = ARRAY(Integer).with_variant(JSON, "sqlite")` 채택 ➔ 프로덕션 Azure PostgreSQL 16의 네이티브 `INTEGER[]` 및 체크 제약(`cardinality(rest_days) BETWEEN 1 AND 6`) 보장 + 기존 139개 단위 테스트(SQLite) 100% 무수정 통과. Pydantic v2 `@model_validator`로 구버전 단일 `restDay` 자동 흡수.
    - **도메인**: `WeeklyPlanGenerator` & `HomeWeeklyPlanGenerator` 대근육 우선순위 큐(`[Push ➔ Legs ➔ Pull ➔ Cardio ➔ Mixed ➔ Cardio]`)로 주 1~6일 상·하체 밸런스 완전 보장.
    - **프론트엔드**: M3 `MultiChoiceSegmentedButtonRow` 채택으로 기존 7분할 요일 버튼 룩앤필 100% 계승, Room `IntListConverters` TypeConverter.
    - **완료율/통계**: `HomeViewModel`과 `StatisticsService`가 이미 `count { !it.isRestDay }` 동적 분모를 쓰고 있음을 실측 검증 완료 (무수정 연동).
  - **거버넌스 및 링크 정합화 통과**:
    - `python scripts/gen_plans_index.py`: **PASS** (`docs/plans/README.md` active: 1 등록).
    - `bash scripts/check-plans-links.sh`: **PASS** (끊긴 참조 0건).
    - `pytest scripts/agents/test_doc_audit.py -q`: **PASS** (36 passed).

**다음 할 일 (Next TODOs for User / Operator):**
- **1순위**: 현재 진행 중인 적응형 주간 운동 계획 v2 (v0.6.0/39) C1 데이터 리셋 승인 및 C2/C3 배포 완결.
- **2순위**: v2 배포 완료 후, 본 통합 구현 설계서([`docs/plans/logs/android.md`](docs/plans/logs/android.md)) 검토 후 구현 착수 승인.

**의존성 및 주의사항 (Context & Constraints):**
- 현재 활성 코드베이스(`app/`, `backend/`)는 일절 건드리지 않고 순수 문서 공간에서 작업되었습니다.
- 마이그레이션 생성 시에는 반드시 `bash scripts/alembic-autogen.sh` (룰 3)를 사용해야 합니다.

---

## 2026-10-27 (Gemini -> Claude/User) 적응형 주간 운동 계획 v2 (Adaptive Weekly Workout Plan v2) & 휴식일 자유 다중 지정 (Flexible Multi-Rest Days, Zero-Legacy) 엔드투엔드 구현 및 검증 완료 (운영 컷오버 C1~C3 사용자 승인 대기)

**현재까지의 작업 상태 (Current Status):**
- **`docs/reports/2026-10-26-adaptive-weekly-workout-proposal.md` 및 `docs/plans/logs/android.md` (r3 Zero-Legacy) 통합 풀스택 구현 완료 (`v0.6.0` / `versionCode=39`)**:
  - **R1 & R2 (백엔드 API 계약·Alembic·다중 휴식일·데이터 초기화 스크립트)**:
    - Alembic 마이그레이션 `backend/alembic/versions/d7f4a2c91b10_v2_weekly_plan_mode_and_reset_audit.py`: `weekly_plans.plan_mode`(`'gym'`/`'home'` CHECK 제약), `user_profiles.default_workout_plan_mode`(`'gym'`/`'home'` CHECK 제약, nullable), `user_profiles.rest_days`(`INTEGER[]` + `ck_user_profiles_rest_days_valid` 배열 제약, 레거시 `rest_day` 제거), `data_reset_audits` 감사 테이블 추가 (Alembic head `d7f4a2c91b10`, 총 9개 리비전).
    - `UserProfile` ORM(`ARRAY(Integer).with_variant(JSON, "sqlite")`) 및 `UserProfileRequest`/`UserProfileResponse` 스키마에서 레거시 `rest_day` 완전 제거 후 `rest_days: list[int]` (`1..6`개, `1..7` 요일 오름차순 중복 제거) 단독 일원화 + `meal_plan.py` 프롬프트 휴식 요일 한글 반영.
    - `/v2/weekly-plan` 라우터 (`GET`, `POST` 201/409 `weekly_plan_exists`, `DELETE` 204, `PATCH /complete`, `GET /history`, `GET /previous`, `GET /statistics`) 신설 및 레거시 `/weekly-plan` 라우트 완전 제거.
    - `PUT /v2/profile/default-workout-plan-mode` (`updateDefaultWorkoutPlanModeV2`): 신체 측정 이력(`profile_measurement_histories`) 오염 없이 기본 운동 환경만 원자적으로 변경·해제.
    - `GET /app-compatibility` 공개 엔드포인트(`minimumSupportedVersionCode = 39`) 및 `require_supported_android_client` 의존성(`Eundun-Client-Version-Code` 헤더 검증, 구버전 `403 client_update_required`) 적용.
    - `backend/scripts/reset_v2_user_data.py`: `--dry-run` 및 `--confirmation RESET_ALL_V2_USER_DATA --reset-id <id> --image-sha <sha> --execution-id <id>` 지원, 8개 앱 데이터 테이블(`RESET_TABLES`) 초기화 + `data_reset_audits` 감사 기록(PostgreSQL 16 컨테이너에서 `alembic upgrade head` ↔ `downgrade -1` ↔ `upgrade head` 및 `--dry-run` / 실행 리허설 완료).
    - `backend/openapi.json` 동기화 완료 (19 paths / 27 routes = 22 JWT + 5 Public).
  - **R3 & R4 (Android 데이터·도메인·UI/UX·테스트)**:
    - `version.properties`: `versionName=0.6.0`, `versionCode=39` (`bash scripts/bump-version.sh 0.6.0`, 단조성 검증 `39 > 38` 통과).
    - `WorkoutDatabase` v2 → v3 (`fallbackToDestructiveMigrationFrom(1, 2)`), `CachedPlanEntity.planMode`, `PlanJsonModels` v2 직렬화(`planMode`, `prescriptionUnit = REPS | SECONDS`, `validateDays` 7일 불변식).
    - `HomeWeeklyPlanGenerator` (무장비·저충격 홈트레이닝 18종 카탈로그 + 결정론적 7일 템플릿 + `local:home:<slug>:v2.0` ID) 및 `WeeklyPlanGenerator` (헬스장 카탈로그 + 소규모 유산소 풀 fallback 방어 보강).
    - `LoadCurrentWeekPlanUseCase`, `LoadDefaultWorkoutPlanModeUseCase`, `CreateWeeklyPlanUseCase`, KST 주차 기준 `ThemePreferences` 자동 생성 1회 가드(`hasAutoCreateAttempted` / `markAutoCreateAttempted`), `LocalDataWiper.ensureV2LocalResetCompleted()`.
    - `HomeViewModel` & `HomeScreen`: `HomePlanSectionState` (`Loading`, `AutoCreating`, `NoPlan`, `PlanReady`, `PlanDataError`), `NoWeeklyPlanCard`, `WorkoutPlanModeSelectionSheet` (`saveAsDefault` 체크박스 포함), `PlanModeBadge` (`헬스장` / `홈트`), 이번 주 계획 삭제 확인 다이얼로그, `WorkoutDetailScreen` 초(`초`) 단위 표기 및 홈트 안내 카드, `ProfileHubScreen` 기본 운동 환경 설정 시트, `MainAppShell` 구버전 클라이언트 업데이트 차단 다이얼로그(`ClientUpdateRequired`).
  - **R5 & R6 (검증 게이트 및 법적·기술 문서 동기화)**:
    - Backend pytest: **139 passed** (19 files), `ruff check` / `ruff format --check` / `mypy` PASS.
    - Android unit tests: **184 passed** (38 files), `spotlessCheck` / `detektDebug` (0 issues) / `assembleDebug` / `assembleRelease` (R8 full mode) / `check-agp10-compat.sh` PASS.
    - `docs/store/*.md` & `backend/app/legal/*.md`, `CLAUDE.md`, `README.md`, `docs/SPEC.md`, `docs/PRD.md`, `docs/TRD.md`, `docs/ops/operations-snapshot.md` 동기화 완료.

**다음 할 일 (Next TODOs for User / Operator):**
- **C1 (사전 조건 확인 및 데이터 초기화 승인)**:
  1. 구버전(`v0.5.0`, `versionCode=38`) 앱이 실행 중인 기기에서 앱을 완전히 종료했는지 확인.
  2. 운영 DB 대상 `reset_v2_user_data.py --dry-run` 점검 및 `--execute --confirm-token V2_RESET_2026_10_27` 실행 승인.
- **C2 & C3 (백엔드 배포 및 Google Play 릴리스)**:
  1. 백엔드 Alembic `d7f4a2c91b10` 마이그레이션 및 ACA 리비전 배포(`MINIMUM_SUPPORTED_ANDROID_VERSION_CODE=39`).
  2. `bash scripts/bump-version.sh 0.6.0` (`versionCode=39`) 버전 상향 및 Google Play 릴리스 승인 후 실기기 사후 점검(Post-release Operational Verification).

**의존성 및 주의사항 (Context & Constraints):**
- C1 destructive reset 및 운영 배포(C2/C3)는 반드시 사용자의 명시적 확인 후 실행해야 합니다.
- git stash 목록에 사용자의 `autostash`(gradlew.bat 줄바꿈 변경)가 있으니 건드리지 마세요.

## 2026-10-27 (Gemini -> Claude/User) 적응형 주간 운동 계획 v2 제안서 '15. 구현 전 재검토 요청' 전건 승인 반영, 홈트레이닝 18종 확정 카탈로그/요일 템플릿 수립 및 실기기 검증 일정 정합화 완료

**현재까지의 작업 상태 (Current Status):**
- **'15. 구현 전 재검토 요청' 전건 승인 및 설계 문서 최신화**:
  - `docs/reports/2026-10-26-adaptive-weekly-workout-proposal.md`에 대해 사용자가 5대 재검토 요청 항목을 전건 승인함에 따라, 문서 상태를 '조건부 대기'에서 **'최종 승인 완료 (Approved & Ready for Implementation)'**로 전면 갱신했습니다.
  - **홈트레이닝 콘텐츠 자율 권한 연구/검토/검증/확정 (§6.4)**:
    - 장비 제로(`equipment = "없음"`), 층간소음 방지 저충격 설계, 오프라인 완결(`gifUrl = ""`), 안정적 ID(`local:home:<slug>:v2.0`), WHO 성인 신체활동 가이드라인 부합.
    - **18종 확정 카탈로그**: Push 4종(정석 푸시업, 니 푸시업, 파이크 푸시업, 체어 딥스), Pull 4종(슈퍼맨 홀드, Y-T-W 레이즈, 버드독, 리버스 스노우 엔젤), Legs 4종(에어 스쿼트, 제자리 런지, 글루트 브릿지, 스탠딩 카프 레이즈), Core 3종(엘보우 플랭크, 데드버그, 레그 레이즈), Cardio 3종(마운틴 클라이머, 점핑잭, 스케이터 홉스).
    - **결정론적 7일 요일 배치 스펙 (`HomeWeeklyPlanGenerator`)**: 월(Push & Core 4종), 화(Cardio 2종), 수(Pull & Posterior 4종), 목(Cardio 2종), 금(Legs & Glutes 4종), 토(Mixed Strength & Core 3종), 지정 요일(Rest Day). `days.size == 7` 및 `hasExercises == true` 불변식 100% 보장.
  - **실기기 검증 일정 정합화 (§8, §9, §10, §11, §15, §16, §18, §19)**:
    - 사용자의 "실기기는 운영 배포 후에 확인할 예정" 결정에 따라, 빌드/CI 게이트는 Compose semantics 및 touch-target(48dp+) 자동 검사로 충족하고, 실기기(TalkBack, Health Connect) 점검은 **운영 배포 완료 후 사후 점검(Post-release Operational Verification)**으로 분리·확정.
  - **차기 기획(Proposals / RFC) 거버넌스 체계 신설 및 1호 기획서 등록 완료**:
    - 스코프 팽창을 방지하면서 가치 있는 브레인스토밍 아이디어를 인큐베이팅할 수 있는 `docs/proposals/` 거버넌스를 구축했습니다.
    - `docs/proposals/README.md` (기획 백로그 인덱스 및 Idea ➔ Exploring ➔ Accepted ➔ Promoted to Plan 라이프사이클), `_template.md` (경량 표준 템플릿) 생성.
    - **1호 기획서 채택 ([PROP-01](file:///c:/programming/apps/eundunHealth/docs/proposals/2026-10-27-flexible-rest-days.md))**: "휴식일 자유 다중 지정 (Flexible Multi-Rest Days)" — 사용자 자율성 보장, 요일별 FilterChips 멀티 선택, $N$일 기준 동적 주간 완료율 분모 계산 및 경량 순환(Round-Robin) 배치를 명세하여 `v0.6.0` 목표로 등록.
    - **후속 기획서 분리 신설 ([PROP-04](file:///c:/programming/apps/eundunHealth/docs/proposals/2026-10-27-adaptive-split-engine.md))**: "운동 일수($N$) 기반 적응형 동적 분할 루틴 엔진 (Adaptive Split Engine)" — 사용자 결정에 따라 복잡한 전신/2분할/3분할/4분할 동적 엔진 및 AI 식단 열량 미세조정은 `v0.7.0+` 후순위 과제로 독립 분리하여 인큐베이팅.
    - `docs/PRD.md`의 `### 향후 확장 후보` 테이블과 양방향 연동 완료.
  - **문서 무결성 검증 통과**:
    - `bash scripts/check-plans-links.sh`: **PASS** (끊긴 참조 0건)
    - `python scripts/gen_plans_index.py --check`: **PASS** (정합화 100%)
    - `pytest scripts/agents/test_doc_audit.py -q`: **PASS** (36 passed)

**다음 할 일 (Next TODOs for Claude / User):**
- 사용자의 후속 구현 요청에 따라 **적응형 주간 운동 계획 v2 구현 패키지(R1~R6)** 착수:
  - **R1 (계약·reset 기반)**: `weekly_plans.plan_mode`, `user_profiles.default_workout_plan_mode` Alembic 마이그레이션, `v2-reset` Job/Command, OpenAPI sync 및 oasdiff/PostgreSQL 병렬 contract runner 구성.
  - **R2 (백엔드 v2 정책)**: `POST /v2/weekly-plan`(201/409 create-or-conflict), `DELETE /v2/weekly-plan`(204), `PUT /v2/profile/default-workout-plan-mode`, v1 route/schema 제거.
  - **R3 (Android 데이터 기반)**: generated client 재생성, Room 2→3 destructive reset boundary, `AutoCreateAttemptStore`, `CurrentWeekPlanRead`.
  - **R4 (Android 제품 흐름)**: `GymWeeklyPlanGenerator`, `HomeWeeklyPlanGenerator`, Home UI 상태(NoPlan, AutoCreating, PlanReady, PlanDataError), 환경 선택/생성 흐름.
  - **R5 & R6 (레거시 제거 및 배포)**: v1 참조 0건 검증, C1 reset 실행(구버전 앱 종료 확인) → C2 배포 → C3 Play 릴리스 → 운영 배포 후 실기기 확인.

**의존성 및 주의사항 (Context & Constraints):**
- C1 destructive reset 실행 직전에는 사용자의 구버전 앱 종료 확인이 선행되어야 합니다.
- git stash 목록에 사용자의 `autostash`(gradlew.bat 줄바꿈 변경)가 있으니 건드리지 마세요.

## 2026-10-26 (Gemini -> Claude) v0.5.0/38 스마일 트레이닝 시작 시 릴리스 전용 NPE 크래시(Sentry EUNDUNHEALTH-E/F) 근본 원인 해결 및 다중 방어선·회귀 가드 구축 완료 (INC-2026-10-26-32)

**현재까지의 작업 상태 (Current Status):**
- **Sentry 크래시 수집 및 역난독화 원인 규명**:
  - `v0.5.0`(`versionCode=38`) 릴리스에서 `'스마일 트레이닝 시작하기'` 버튼 클릭 직후 발생한 `"앱이 중단됨"` 크래시 2건(`EUNDUNHEALTH-E` [id: `7812482834`], `EUNDUNHEALTH-F` [id: `7812482966`])을 Sentry API로 조회하여 역난독화된 스택트레이스를 확보했습니다:
    `NullPointerException: Attempt to invoke virtual method 'java.lang.Class java.lang.Object.getClass()' on a null object reference at com.google.mlkit.vision.face.FaceDetection.getClient(FaceDetection.0:3) <- MlKitFaceModuleInstaller.ensureModuleAvailable(MlKitFaceModuleInstaller.kt:43)`.
  - **Primary Root Cause**: ML Kit(`com.google.mlkit:common:18.5.0` / `play-services-mlkit-face-detection:17.1.0`) 내부 DI(`com.google.firebase:firebase-components:16.1.0`)는 `MlKitComponentDiscoveryService`에 등록된 `FaceRegistrar`, `VisionCommonRegistrar`, `CommonComponentRegistrar`를 `Class.forName(...).getDeclaredConstructor().newInstance()` 리플렉션으로 인스턴스화합니다. 그러나 `firebase-components:16.1.0` 번들 `proguard.txt`는 `-keep class * implements com.google.firebase.components.ComponentRegistrar`에 생성자/멤버 명세(`{ <init>(); *; }`)가 빠져 있어, AGP 9.3.2 **R8 full mode**가 3개 Registrar의 기본 생성자 `<init>()`을 모두 제거했습니다(`mapping.txt` 실측 확인). 그 결과 `MlKitContext.getInstance().get(zzc.class)`가 `null`을 반환하며 `FaceDetection.getClient()` 호출 즉시 NPE가 발생했습니다.
  - **Secondary Root Cause**: `MlKitFaceModuleInstaller.ensureModuleAvailable()`에서 `FaceDetection.getClient(options)`와 `ModuleInstall.getClient(context)`가 `try` 블록 바깥에 위치해 있어 예외가 `MlKitModuleState.Unavailable`로 전환되지 못하고 메인 스레드 코루틴을 크래시시켰습니다.
- **근본 원인 해결 및 유지보수/방어선(Defense-in-Depth) 개선**:
  1. `app/proguard-rules.pro`: `-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); *; }` 추가. `:app:assembleRelease` 후 `app/build/outputs/mapping/release/mapping.txt`에서 `CommonComponentRegistrar`, `VisionCommonRegistrar`, `FaceRegistrar` 3개 클래스 모두 `void <init>() -> <init>` 및 `getComponents()`가 보존됨을 실측 검증했습니다.
  2. `MlKitFaceModuleInstaller.kt`: `FaceDetection.getClient` 및 `ModuleInstall.getClient`를 `try` 블록 내부로 이동하고 `finally { runCatching { detector?.close() } }`로 리소스 누수를 차단했으며, `Throwable`(`CancellationException` 재던짐) 포착 시 Sentry breadcrumb(`smile.mlkit_init_failed`) + `Sentry.captureException` 기록 후 한국어 안내와 함께 `MlKitModuleState.Unavailable`을 반환하도록 보강했습니다.
  3. `SmileTrainingViewModel.kt`: `prepareModelAndProceed()` 내부 `moduleManager.ensureModuleAvailable()` 호출을 `try/catch (t: Throwable)`로 이중 방어하고, `MODEL_UNAVAILABLE` 및 `onCameraError` 전환 시 룰 8에 따라 Sentry breadcrumb(`smile.model_unavailable`, `smile.camera_error`)를 남기도록 개선했습니다.
  4. `SmileCameraStage.kt` & `MlKitSmileSignalAnalyzer.kt`: `MlKitSmileSignalAnalyzer` 생성, `ProcessCameraProvider.getInstance`, `bindFrontCameraUseCases` 전체(`hasCamera`, `Preview`, `ImageAnalysis`, `bindToLifecycle`)를 `runCatching`으로 감싸 `onCameraError` 인라인 복구로 연결하고, `detector.process(inputImage)` 동기 예외 시에도 `imageProxy.close()`가 보장되도록 강화했습니다.
  5. **회귀 테스트 및 문서화**:
     - `ProguardKeepRulesTest.kt`: `ComponentRegistrar` `<init>()` keep 규칙 존재 여부를 단위 테스트로 박제(룰 12 확장).
     - `SmileTrainingViewModelTest.kt`: `moduleManager.ensureModuleAvailable`에서 예기치 않은 `NullPointerException` 발생 시 크래시 없이 `MODEL_UNAVAILABLE`로 전환되고 재시도 시 `READY`로 복구되는 회귀 테스트 추가(Android `@Test` 165 → **167 passed**).
     - `docs/ops/incident-log.md`(`INC-2026-10-26-32`) 및 `CLAUDE.md`(룰 12 확장, 테스트 수 167 반영) 업데이트 완료.
- **전체 품질 게이트 검증 결과**:
  - `bash scripts/check-agp10-compat.sh`: **PASS**
  - `./gradlew :app:spotlessApply :app:spotlessCheck :app:detektDebug :app:testDebugUnitTest :app:assembleRelease`: **PASS (BUILD SUCCESSFUL, 167 `@Test` passed, detekt 0 issues)**
  - `pytest backend/tests/ -q`: **126 passed**
  - `pytest scripts/agents/test_doc_audit.py -q`: **36 passed**
  - `bash scripts/check-plans-links.sh` & `python scripts/gen_plans_index.py --check`: **PASS**

**다음 할 일 (Next TODOs for Claude / User):**
- Play 스토어에 핫픽스 릴리스(`v0.5.1`, `versionCode=39`)를 배포하려면 `bash scripts/bump-version.sh 0.5.1` 실행 후 커밋·푸시 및 `v0.5.1` 태그를 푸시하세요(`LAST_UPLOADED_VERSION_CODE=38`).

**의존성 및 주의사항 (Context & Constraints):**
- AGP R8 full mode에서는 `-keep class ...`에 `{ <init>(); *; }` 멤버 명세가 없으면 리플렉션으로 호출되는 기본 생성자 `<init>()`이 제거됩니다(룰 12).
- git stash 목록에 사용자의 `autostash`(gradlew.bat 줄바꿈 변경)가 있으니 건드리지 마세요.


## 2026-10-26 (Gemini -> Claude) v0.5.0/38 출시 기준 프로젝트 전 문서 최신화 및 플랜 페어 아카이브 완료

**현재까지의 작업 상태 (Current Status):**
- `v0.5.0`(`versionCode=38`, Play 프로덕션 `completed` 100% 출시, 원장 `LAST_UPLOADED_VERSION_CODE=38`, 커밋 `cbd155f`) 및 백엔드 활성 리비전 `eundunhealth-api--0000063`(이미지 `869407a`, Alembic head `a3869f575d6a`, 배포 run `40274391632`) 기준으로 프로젝트 핵심 문서를 전수 동기화했습니다:
  - `CLAUDE.md`, `README.md`, `docs/ops/operations-snapshot.md`, `docs/PRD.md`, `docs/TRD.md`, `docs/SPEC.md`, `docs/CHANGELOG.md`
  - 라우트 수(24개 = 20 JWT + 4 공개), 화면/VM/DI/클라이언트 수(14 Screens / 12 ViewModels / 4 Hilt modules / 7 OpenAPI clients), DB 테이블(앱 8 + `alembic_version`), 테스트 수(Android `@Test` 165 [35 files] · Backend pytest 126 [18 files]), 신규 의존성(CameraX 1.6.2, ML Kit Face Detection 17.1.0, WorkManager 2.10.1)을 코드베이스 실측치와 100% 일치시켰습니다.
- 완료된 설계/계획 문서(`2026-10-26-smile-training-{design,plan}.md` 및 `implementation_plan.md`)를 `docs/plans/logs/android.md` 토픽 렛저(`Recent` 3건)로 흡수·아카이브(`git rm`)하고 `docs/plans/README.md` 인덱스를 갱신했습니다(활성 작업 0건, 경고 0건).

**다음 할 일 (Next TODOs for Claude / User):**
- 차기 스토어 릴리스 시 `LAST_UPLOADED_VERSION_CODE=38`이므로 `bash scripts/bump-version.sh <new-ver>`를 통해 `versionCode >= 39`로 상향하세요.

**의존성 및 주의사항 (Context & Constraints):**
- `scripts/check-plans-links.sh`, `scripts/gen_plans_index.py --check`, `scripts/agents/test_doc_audit.py` 가드가 모두 통과하는 상태를 유지하세요.
- git stash 목록에 사용자의 `autostash`(gradlew.bat 줄바꿈 변경)가 있으니 건드리지 마세요.

## 2026-10-26 (Gemini -> Claude) 스마일 트레이닝(Smile Training) v0.5.0/38 풀스택 엔드투엔드 구현, 다중 역할 전수 감사·근본 원인 해결, UI/UX 고도화 및 프로덕션 배포(Azure Container Apps + Google Play) 완료

**현재까지의 작업 상태 (Current Status):**
- `docs/plans/2026-10-26-smile-training-design.md` 및 페어 계획 `docs/plans/2026-10-26-smile-training-plan.md`(→ `docs/plans/logs/android.md` 2026-10-26 entry 로 흡수)를 기반으로 스마일 트레이닝 기능(Phase 1~5)을 엔드투엔드로 구현하고, 다중 역할(개발자·책임자·운영자·유지보수자·QA/QC·어플 디자이너·DevOps/FDE·PM·CTO) 관점의 전수 코드 감사, 근본 원인 해결, UI/UX 리팩토링 및 **프로덕션 배포(`v0.5.0` / `versionCode=38`)**까지 완료했습니다.
- **Phase 1: Backend API & PostgreSQL Alembic Migration 완료**:
  - `backend/app/models/smile_training.py`: `SmileTrainingSession`(`smile_training_sessions` 테이블, `sa.Uuid(as_uuid=True)` PK, 체크 제약 3종, `(user_id, occurred_at)` 복합 인덱스).
  - `backend/alembic/versions/a3869f575d6a_create_smile_training_sessions.py`: `scripts/alembic-autogen.sh`로 PostgreSQL 16 컨테이너에서 자동 생성 및 upgrade/downgrade 검증(룰 3).
  - `backend/app/schemas/smile_training.py`, `smile_training_repo.py`, `smile_training_service.py`, `routers/smile_training.py`:
    - `POST /smile-training/sessions`: 클라이언트 UUID 멱등 생성(신규 201, 동일 payload 재전송 200, 상이 payload/타 사용자 UUID 충돌 409).
    - `GET /smile-training/sessions`: `(occurred_at DESC, id DESC)` 커서 페이지네이션.
    - `GET /smile-training/statistics`: KST(`UTC+9`) 기준 연속일(`currentStreakDays`, `longestStreakDays`) 및 당일 완료 여부(`completedToday`).
    - `DELETE /smile-training/sessions`: 인증 사용자의 전체 세션 삭제(`SmileTrainingDeleteResponse`).
  - 계정 삭제/고아 정리: `AccountService._purge_app_data()`에 `SmileTrainingRepository.delete_all_by_user()` 추가, `ProfileRepository.list_all_user_ids()`를 7개 사용자별 테이블 전체 `UNION`으로 확장.
  - `backend/openapi.json` 동기화 완료(`scripts/sync-openapi.sh`, 17 paths / 24 routes) 및 법적 고지(`docs/store/privacy-policy.md`, `docs/store/account-deletion.md` → `backend/app/legal/*.md`) 온디바이스 카메라 무저장·무전송 명시.
- **Phase 2: Android Persistence, WorkManager Sync, Remote Hydration & LocalDataWiper 완료**:
  - `SmileTrainingDatabase`(`smile_training.db`, version 1) 및 KSP 스키마 export(`app/schemas/com.gunnys.eundunhealth.data.local.SmileTrainingDatabase/1.json`).
  - `SmileSyncWorker`(`@HiltWorker`, `ExistingWorkPolicy.KEEP`, `NetworkType.CONNECTED`) 및 `EundunHealthApplication`의 `Configuration.Provider` 연동.
  - `SmileTrainingRepositoryImpl`: 오프라인 우선 Room SSOT, 빈 로컬 DB 원격 세션 자동 복원(`refreshRemoteHistoryIfEmpty`), KST 연속일 계산, `PENDING`/`SYNCED`/`BLOCKED` 분류(400/409/422는 `BLOCKED`로 전환해 무한 재시도 배터리 소모 차단), 수동 재시도(`retryBlockedSessions`) 및 전체 삭제(`deleteAllSessions`).
  - `LocalDataWiper`(`DefaultLocalDataWiper`)를 `AuthRepositoryImpl.deleteAccount()`에 연동하고 서버 삭제 호출 전 `userId`를 선캡처하여 로컬 DB 2종, DataStore, WorkManager 큐를 일괄 정리.
- **Phase 3 & 4: Pure Domain Signal Evaluator, CameraX + ML Kit & Root Modal UI/UX 리팩토링 완료**:
  - `SmileRoutineRule` & `SmileSignalEvaluator`: 단조 시간(`timestampElapsedMs`) 누적, 루틴/난이도별 히스테리시스, 1,000ms 슬라이딩 윈도우(>= 75% 충족률), `trackingId` 변경 시 홀드 초기화, 일시정지 시 완료된 반복 횟수(`completedReps`)를 보존하는 `clearActiveHold()` 분리.
  - `MlKitFaceModuleInstaller`(`ModuleInstallClient` 가용성 확인 및 다운로드 진행률) + `MlKitSmileSignalAnalyzer`(`STRATEGY_KEEP_ONLY_LATEST`, `ResolutionSelector(640x480)` 해상도 최적화, 모든 경로 `imageProxy.close()` 보장).
  - `SmileTrainingViewModel`(룰 11 UDF-Enhanced 준수, `FrameSignal.timestampElapsedMs` 단일 클록 도메인 기반 세션 소요 시간 계산, 일시정지·이어하기 및 `Lifecycle.Event.ON_STOP` 백그라운드 전환 자동 일시정지, 카메라 바인딩 오류 시 인라인 `AppError` 복구).
  - `SmileCameraStage` & `SmileTrainingScreen`:
    - `WindowInsets.safeDrawing` Edge-to-Edge 안전 영역 보장, 스프링 물리(`Spring.DampingRatioLowBouncy`) 보간 `SweepGradient` 프로그레스 링, 반복 단계 도트 인디케이터(`RepStepIndicatorRow`), 실시간 정렬 상태 배지(`AlignmentStatusBadge`), `ACTIVE` 단계 일시정지/중단 컨트롤.
    - 기기 카메라 하드웨어 유무(`FEATURE_CAMERA_FRONT`/`FEATURE_CAMERA_ANY`) 사전 검사, 루틴 카드의 난이도별 유효 유지 시간·눈 뜨기 배지, 균등 5분할 목표 반복 선택기 및 총 예상 유지 시간 표시, 한국어 시간 포맷(`formatDurationKorean`), 완료·회고 화면 세션 요약 배너(`SessionOutcomeSummaryBanner`).
  - `AppNavigation.kt` 루트 모달 `Screen.SmileTraining` 및 `ProgressHubScreen.kt` "마음 연습 > 스마일 트레이닝" 진입 카드 연결.
- **Phase 5: 전수 품질 게이트 및 프로덕션 운영 배포 완료 (`v0.5.0` / `versionCode=38`)**:
  - Backend: `ruff check`, `ruff format --check`, `mypy` (52 files, 0 issues), `bandit` (0 issues), `pytest` (**126 passed**), `test_doc_audit.py` (**36 passed**), Docker Compose 런타임 스모크 **PASS**.
  - Android: `check-agp10-compat.sh`, `spotlessCheck`, `detektDebug` (0 issues), `testDebugUnitTest` (**165 passed**), `preflight-release.sh` (AAB 13.10MB + APK 8.79MB + Sentry 매핑 `b32bb97e-99aa-368c-ac1a-6d35b8a1d764` 업로드) **PASS**.
  - **CI/CD Concurrency 트러블슈팅**: 2026-10-17부터 `waiting` 상태로 방치되어 `backend-production` concurrency 락을 잡고 있던 stale run(`39026541371`)을 식별·취소(`gh run cancel`)하여 배포 파이프라인 차단 근본 해소.
  - **Azure Container Apps 배포 완료** (`Backend CI/CD` run `40274391632`): 활성 리비전 **`eundunhealth-api--0000063`** (이미지 `eundunhealthacr.azurecr.io/eundunhealth-api:869407a`, `eundunhealth-reaper` Job 동일 이미지 동기화, `/health`·`/health/ready`·`/privacy`·`/account-deletion` 200 및 `/smile-training/sessions` 401 인증 가드 라이브 검증 완료).
  - **Google Play 프로덕션 배포 완료** (`Release (Play production)` run `40274395812`): 태그 `v0.5.0` (`versionCode=38`) Play 프로덕션 트랙 `completed`(100%) 업로드 및 원장 자동 갱신 커밋 `cbd155f`(`LAST_UPLOADED_VERSION_CODE=38`) 완료.
  - **Android CI** (run `40274391865`) 및 **docs-plans-index** (run `40274391387`): 모두 `completed (success)`.

**다음 할 일 (Next TODOs for Claude / User):**
- 실기기(Android)에서 Play 스토어 업데이트(`v0.5.0`) 또는 로컬 설치 후 전면 카메라 권한 허용/거부, 최초 실행 시 Google Play services ML Kit 얼굴 모듈 준비, 미러 뷰파인더 및 조도별 미소 유지 피드백을 점검하세요(`docs/plans/2026-10-26-smile-training-design.md` §10 참조 → `docs/plans/logs/android.md` 2026-10-26 entry 로 흡수).
- 차기 릴리스 시 `LAST_UPLOADED_VERSION_CODE=38`이므로 `versionCode >= 39`를 사용해야 합니다(`bash scripts/bump-version.sh`가 자동 검증).

**의존성 및 주의사항 (Context & Constraints):**
- `CameraXViewfinder`(`androidx.camera:camera-compose:1.6.2`)와 `SurfaceRequest`를 사용하므로 `androidx.camera.view.PreviewView`를 직접 참조하지 마세요.
- git stash 목록에 사용자의 `autostash`(gradlew.bat 줄바꿈 변경)가 있으니 건드리지 마세요.

## 2026-10-26 (Gemini -> Claude) 스마일 트레이닝 종합 설계 r9 ('3.4 진입점 및 내비게이션 아키텍처' 심층 고도화, 딥링크 세션 가드, 화면 켜짐 하드웨어 제어 및 A11y 내비게이션 명세 완료)

**현재까지의 작업 상태 (Current Status):**
- `docs/plans/2026-10-26-smile-training-design.md`(→ `docs/plans/logs/android.md` 2026-10-26 entry 로 흡수)의 **'3.4 진입점 및 내비게이션 아키텍처'**를 실제 안드로이드 코드베이스(`AppNavigation.kt`, `MainAppShell.kt`, `Screen.kt`, `ProgressHubScreen.kt`) 구조 및 안드로이드 최신 가이드라인에 맞추어 심층 조사·연구하여 r9로 전면 개선 완료했습니다.
  - **진입점 다각화 및 심리적 완충 설계 (§3.4.1)**:
    - `ProgressHubScreen` (진행 탭) 신규 "마음 연습" 카드: `ProgressRow` 패턴과 일관성을 유지하며 상시 노출. 당일 완료 체크 및 `🔥 3일 연속` 뱃지 표기.
    - `HomeScreen` (오늘 탭) 맥락형 넛지: `dayPlan.isCompleted == true && !smileState.isCompletedToday` 조건에 따라 운동 완주 직후 부드럽게 제안. DataStore `dismissed_date` 기반 "오늘 하루 닫기(X)" 중복 피로도 방지.
    - 딥링크: `eundunhealth://smile-training?routine={routine}` 및 웹 링크 `https://eundunhealth.app/smile-training?routine={routine}` 지원.
  - **풀스크린 카메라 & 루트 모달 프리젠테이션 아키텍처 (§3.4.2)**:
    - `NavigationSuiteScaffold`(80dp 하단 네비게이션 바)의 화면 가림 및 트레이닝 중 손가락 오터치 세션 유실 방지를 위해 최상위 `AppNavigation.kt` (Root NavHost) 레벨 독립 모달로 등록.
    - 모달 전환 애니메이션: `slideInVertically` (스프링 물리) + `fadeIn()` / `slideOutVertically` + `fadeOut()`.
    - `Screen.SmileTraining`, `AppNavigation`, `MainAppShell`, `MainContent` 간의 완벽한 콜백 전파 인터페이스 명세화.
  - **딥링크 보안 및 인증 세션 가드 (§3.4.3)**:
    - 외부 딥링크 진입 시 미인증(`SessionState.Unauthenticated`) 상태 방어. `Screen.AuthGate`로 우선 우회 후 인증 완료 시 펜딩된 딥링크 경로로 자동 착륙.
    - 세션 종료 시 앱 강제 종료를 방지하기 위해 `Screen.Main`을 베이스로 하는 합성 백스택(Synthetic BackStack) 보장.
  - **백스택 안전성 및 하드웨어 생명주기 제어 (§3.4.4)**:
    - **Android 14+ Predictive Back & `BackHandler`**: `ActivePhase` 중단 확인 다이얼로그(`showExitConfirmDialog`) 바인딩으로 연습 유실 방지.
    - **화면 켜짐 유지 (`FLAG_KEEP_SCREEN_ON`)**: 카메라 응시 중 터치가 없어도 화면 절전(30초 타임아웃)을 방지하는 `DisposableEffect` 윈도우 플래그 바인딩 및 세션 종료 시 즉시 해제.
    - **세로 화면 고정 (`ActivityInfo.SCREEN_ORIENTATION_PORTRAIT`)** 및 `WindowInsets.safeDrawing` 인셋 분리.
  - **접근성(A11y) 및 스크린 리더 지원 (§3.4.5)**:
    - TalkBack 사용자를 위한 Semantics(`role = Role.Button`, 상태 안내), 모달 진입 시 `FocusRequester` 자동 포커싱, 단계 전환 시 `liveRegion = LiveRegionMode.Polite` 음성 피드백.
- **검증 완료**:
  - `bash scripts/check-plans-links.sh`: **PASS (끊긴 링크 0건)**.
  - `python scripts/gen_plans_index.py --check`: **PASS (정합화 100%)**.
- `docs/plans/2026-10-26-smile-training-design.md`(→ `docs/plans/logs/android.md` 2026-10-26 entry 로 흡수)를 모던 UI/UX 인터랙션 설계와 그래픽 기술 고도화를 반영하여 r8로 전면 개정 완료했습니다.
  - **모던 UI/UX 레이아웃 & 비주얼 설계 (§3.5)**:
    - `IntroPhase`: M3 Expressive Card, 루틴 세그먼트 버튼, 난이도 칩, 횟수 스테퍼.
    - `ActivePhase`: Edge-to-Edge 풀스크린 카메라 + 다크 글래스모피즘(Dark Glassmorphism) 플로팅 HUD.
    - `CompositingStrategy.Offscreen` + `BlendMode.Clear`: 윈도우 배경 누수 없는 안면 비네트 오버레이 및 타원형 투과 뷰포트.
    - `SweepGradient` 프로그레스 링: 미소 유지 시 시계방향 회전 차오름.
    - `RepSuccessPhase`: 뷰포트 바운스, 파티클 폭죽(Canvas Particle Explosion), 2단계 햅틱 피드백.
    - `CompletedPhase`: 5단계 인터랙티브 감정 회고 이모지 시트, 연속일(Streak) 불꽃 배지.
  - **마이크로 인터랙션 & 햅틱 피드백 시퀀스 (§3.6, §3.7)**:
    - 스프링 물리 보간(`Spring.DampingRatioLowBouncy`, `Spring.StiffnessMediumLow`)을 통한 게이지 진척도 애니메이션.
    - 얼굴 안착(`TextHandleMove`), 미소 시작(`SegmentTick`), 게이지 홀딩(Micro-tick), 세트 성공(`LongPress` 2연타 틱), 세션 완주(3단 리듬 팡파르).
  - **카메라 뷰파인더 다크 글래스 테마 & 접근성 (§3.8, §3.9)**:
    - 실내/역광 환경에서도 시인성(Contrast Ratio >= 7:1)을 보장하는 Dark Translucent Glass Theme 기본 채택.
    - TalkBack 스크린리더를 위한 `liveRegion = LiveRegionMode.Polite` 음성 피드백 및 48dp 터치 타깃 준수.
  - **백엔드 서버 동기화 & 유지보수성 아키텍처 완비**:
    - FastAPI 세션 CRUD/통계 엔드포인트, Alembic 마이그레이션(룰 3, 룰 7), `_purge_app_data(user_id)` 회귀 가드, Room KSP 스키마 관리.
- **검증 및 인덱스 정합화 완료**:
  - `bash scripts/gen-plans-index.sh` 실행 및 `python scripts/gen_plans_index.py --check`: **PASS (정합화 100%)**.
  - `bash scripts/check-plans-links.sh`: **PASS (끊긴 링크 0건)**.

**다음 할 일 (Next TODOs for Claude):**
- 사용자가 design을 승인한 뒤에만 `2026-10-26-smile-training-plan.md`(→ `docs/plans/logs/android.md` 2026-10-26 entry 로 흡수) 페어를 작성하세요.
  - Phase 1: 백엔드 Alembic 마이그레이션, Model, Repository, Service, Router, OpenAPI 동기화.
  - Phase 2: 안드로이드 `SmileTrainingDatabase`, 오프라인 우선 동기화 Repository, 계정 삭제 연동.
  - Phase 3: CameraX + ML Kit 파이프라인, 전략 패턴 루틴 엔진, Compose UI 및 ViewModel.

**의존성 및 주의사항 (Context & Constraints):**
- 백엔드 마이그레이션 작성 시 반드시 `docker compose -f docker-compose.test.yml run --rm backend-test alembic revision --autogenerate` 명령을 사용하고 downgrade 블록을 검증하세요(룰 3, 룰 7).
- git stash 목록에 사용자의 `autostash`(gradlew.bat 줄바꿈 변경)가 있으니 건드리지 마세요.
- git stash 목록에 사용자의 `autostash`(gradlew.bat 줄바꿈 변경)가 있으니 건드리지 마세요.

## 2026-10-16 (Gemini -> Claude) '오늘의 활동' 완전 제거 및 메인페이지(HomeScreen) UI/UX 리디자인

**현재까지의 작업 상태 (Current Status):**
- **'오늘의 활동' 및 레거시 코드 완전 제거 (6개 파일 삭제)**:
  - 삭제 파일: `TodayActivityCard.kt`, `DailyActivity.kt`, `GetTodayActivityUseCase.kt`, `HealthConnectMappers.kt`, `GetTodayActivityUseCaseTest.kt`, `HealthConnectMappersTest.kt`.
  - Health Connect 권한 축소(4개 → 1개 `READ_EXERCISE` 단독): `AndroidManifest.xml`, `HealthConnectDataSource.kt`, `HealthRepository.kt`, `HealthRepositoryImpl.kt`, `PermissionsRationaleActivity.kt`, `HomeViewModel.kt`에서 `READ_STEPS`·`READ_TOTAL_CALORIES_BURNED`·`READ_HEART_RATE` 및 일일 활동 조회 로직을 전수 제거했습니다.
- **메인페이지(`HomeScreen.kt`, `SkeletonUi.kt`) Material 3 UI/UX 리디자인 (`PRD PLAN-03` 완수)**:
  - `WeeklyProgressCard`: 주차 날짜 범위(`M/d - M/d`), 완료율 배지, 애니메이션 `LinearProgressIndicator`(`StrokeCap.Round`), 월~일 7일 상태를 한눈에 보여주는 `WeeklyDayStampRow` 추가.
  - `DayPlanCard`: KST(`Asia/Seoul`) 기준 오늘 날짜(`isToday: Boolean` 원시 파라미터 전달로 Compose Strong Skipping 리컴포지션 최적화)에 Primary 2.dp 테두리 및 `"오늘"` 배지 표시, 운동 종목 수 서브타이틀 추가.
  - `ExerciseRow`: 운동 유형 아이콘(`STRENGTH`/`CARDIO`), 운동명, 부위·장비(`bodyPart · equipment`) 서브타이틀, `세트×횟수` 배지 및 `KeyboardArrowRight` 어포던스를 갖춘 구조화된 카드 행으로 개선.
- **재발방지 가드 테스트 및 문서/법적 고지 SSoT 동기화**:
  - `ManifestHealthConnectRationaleTest.kt`에 `READ_EXERCISE` 단독 권한 선언 가드 및 `PermissionsRationaleActivity` 레거시 문구 부재 검증 테스트(+2 `@Test`) 추가.
  - `HomeViewModelTest.kt`에 `today`(KST) 상태 보존 및 `toggleError` 복구 테스트 보강 (현재 총 Android 단위 테스트: **30개 파일 / 146 `@Test` PASS**).
  - `docs/store/privacy-policy.md`, `docs/store/account-deletion.md`, `docs/store/health-connect-permissions.md` 수정 및 `bash scripts/sync-legal-docs.sh`로 `backend/app/legal/*.md` 동기화 완료 (`pytest tests/test_legal.py` 통과).

**다음 할 일 (Next TODOs for Claude):**
- 차기 스토어 릴리스(예: `v0.3.2`) 시 Play Console의 **앱 콘텐츠 → 건강 앱 선언(Health Connect 권한)** 에서 `READ_STEPS`·`READ_TOTAL_CALORIES_BURNED`·`READ_HEART_RATE` 체크를 해제하고 `READ_EXERCISE` 1종만 남기세요 (`docs/store/health-connect-permissions.md` 참조).

**의존성 및 주의사항 (Context & Constraints):**
- `HealthConnectDataSource.kt`의 `private val client: HealthConnectClient by lazy` 명시적 타입 어노테이션은 detekt `RedundantSuspendModifier` 오탐 방지를 위해 반드시 유지해야 합니다.
- `ManifestHealthConnectRationaleTest`가 매니페스트의 `android.permission.health.*` 권한이 `READ_EXERCISE` 1종뿐인지 자동 검사하므로, 미사용 건강 권한을 매니페스트에 추가하지 마십시오.

## 2026-10-16 (Gemini -> Claude) 프로젝트 문서 전수 최신화 및 플랜 아카이브

**현재까지의 작업 상태 (Current Status):**
- `v0.3.1`(`versionCode=36`, Play 프로덕션 10% 단계적 출시 LIVE) 및 백엔드 revision `eundunhealth-api--0000062`(이미지 `2610d18`, Alembic head `0dda67f1638a`) 기준으로 프로젝트 전 문서를 동기화했습니다:
  - `CLAUDE.md`, `README.md`, `docs/ops/operations-snapshot.md`, `docs/PRD.md`, `docs/TRD.md`, `docs/SPEC.md`, `docs/CHANGELOG.md`
  - 라우트 수(20개 = 16 JWT + 4 공개), 화면/VM 수(11 Screens / 10 ViewModels / 5 Hilt modules / 6 OpenAPI clients), DB 테이블(앱 7 + `alembic_version`), Key Vault 활성 시크릿(8종), GitHub Actions 워크플로(7종), 테스트 수(Android `@Test` 151 · Backend pytest 119), 최신 의존성 버전을 실측치와 일치시켰습니다.
- `scripts/agents/doc_audit.py`의 `count_routes_in_text()`가 `APIRouter(..., prefix="/meals")`의 prefix를 라우트 경로에 결합하도록 보강하고 단위 테스트(`scripts/agents/test_doc_audit.py`)를 추가했습니다.
- 완료된 2026-10 설계/계획 문서 4건을 토픽 렛저로 아카이브(흡수 후 원본 삭제)하고 `docs/plans/README.md` 인덱스를 갱신했습니다:
  - `docs/plans/2026-10-15-exercise-korean-localization-design.md`(→ `docs/plans/logs/android.md` 2026-10-15 entry 로 흡수)
  - `docs/plans/2026-10-15-agp10-build-modernization-design.md`(→ `docs/plans/logs/process-infra.md` 2026-10-15 entry 로 흡수)
  - `docs/plans/2026-10-16-production-deployment-hardening-{design,plan}.md`(→ `docs/plans/logs/process-infra.md` 2026-10-16 entry 로 흡수)

**다음 할 일 (Next TODOs for Claude):**
- Play Console에서 `v0.3.1`(`versionCode=36`) 10% 단계적 출시 모니터링 후 이상 없을 시 100% 롤아웃 승격 시점에 `docs/ops/operations-snapshot.md` §1 표기만 갱신하세요.
- 신규 라우터에 `APIRouter(prefix=...)`를 사용할 경우 `doc_audit.py`가 자동으로 prefix를 결합하므로 문서의 라우트 표에는 전체 경로(`/meals/generate` 등)를 기재하세요.

**의존성 및 주의사항 (Context & Constraints):**
- `docs/plans/logs/*.md` 및 `docs/plans/README.md`는 `.gitattributes`에 의해 `eol=lf`가 강제됩니다.
- `scripts/check-plans-links.sh`, `scripts/gen_plans_index.py --check`, `scripts/agents/doc_audit.py --collect-only` 가드가 모두 통과하는 상태를 유지하세요.

## 2026-10-15 배포 전 전체 감사

- 배포는 수행하지 않았다. Android 151 unit tests/Lint/Detekt/R8와 backend 117 pytest/94% coverage/Ruff/mypy/Bandit/pip-audit, Docker PostgreSQL→Alembic→Uvicorn health smoke를 통과했다.
- PyJWT 2.14.0의 공개 취약점 2건을 2.15.1로 해소했고, backend CI에 `ruff format --check`를 추가했다.
- OpenAPI 입력을 로컬 `RegularFile`로 추적하고 generator를 process worker로 격리해 stale-cache 및 Gradle daemon Metaspace 문제를 제거했다.
- Windows/WSL에서 plans index와 OpenAPI sync가 각각 경로/환경 전달 때문에 실패하던 문제를 수정했다.
- 식단 AI는 중첩 retry와 비멱등 POST 재시도를 제거하고 timeout/token/schema/의료 면책을 보강했다. 근거 없는 30세·활동계수·4:4:2 목표 계산은 제거했다.
- v0.3 배포 전 사용자 확인 필요: Azure AI Foundry 모델의 실제 처리 지역·보존 조건을 확정하고 개인정보 처리방침/Play 데이터 안전 고지를 일치시킬 것.

## 2026-10-15 AGP 10 빌드 현대화

- AGP 10에서 삭제되는 `android.builtInKotlin=false`와 `android.newDsl=false`를 제거하고 AGP 9 built-in Kotlin/new DSL을 사용한다.
- Kotlin Android 플러그인 alias는 제거했다. OpenAPI Kotlin 생성 소스는 `AndroidSourceSet.kotlin.directories`로 등록한다.
- 근본 원인은 Hilt가 아니라 detekt 1.23.8 플러그인의 legacy variant API 및 Gradle 10 `ReportingExtension.file` 호출이었다.
- app에는 detekt 플러그인을 적용하지 않는다. 루트 `apply false`로 task type만 제공하고, `androidComponents` 안에서 `configureJavaCompileTask`의 classpath/destination + SDK boot classpath를 쓰는 `detektDebug`/`detektBaselineDebug` custom task를 유지한다. AGP 10에서 삭제되는 직접 `getJavaCompile()`과 `Variant.getCompileClasspath()`는 쓰지 않는다.
- detekt 2.x는 아직 alpha라 도입하지 않았다. 정식 릴리스 후 custom wiring 제거와 baseline drift를 함께 재검토한다.
- `android.dependency.useConstraints=true`와 `android.uniquePackageNames=false`도 제거해 AGP 9 기본값과 sync 최적화를 따른다.
- 재발 방지: `scripts/check-agp10-compat.sh`를 Android CI와 pre-commit에 연결했다.
- 상세: `docs/plans/2026-10-15-agp10-build-modernization-design.md`(→ `docs/plans/logs/process-infra.md` 2026-10-15 entry 로 흡수). 배포는 수행하지 않았다.

## 2026-10-15 운동 콘텐츠 한국어 로컬라이징

- `korean_exercises.json`은 앱이 현재 요청하는 추천 풀 46개를 `exerciseId` 기준으로 번역한다.
- 이름과 지침을 결합·정규화한 원문의 SHA-256이 일치할 때만 한글 이름/지침으로 교체하며, 변경·신규 운동은 영문으로 폴백한다.
- 서버/Room plan JSON은 영문 canonical로 유지하고 `WorkoutRepositoryImpl` 반환 경계에서만 한글화한다.
- asset 로드는 suspend single-flight이며 손상 시 빈 catalog를 캐시하고 coroutine 취소는 전파한다.
- 공식 Free V1의 AscendAPI 출처 링크를 프로필에 추가했다. 2026-10-15 현재는 무료·비상업 앱이나 운영 조건은 확정하지 않으며, 공개 범위·수익 모델·상용 운영 변경 시 AscendAPI 플랜을 재검토한다(Azure 비용과 무관).
- 실 API ID/지문 46/46 일치 및 translation/repository 단위 테스트 통과. 상세: `docs/plans/2026-10-15-exercise-korean-localization-design.md`(→ `docs/plans/logs/android.md` 2026-10-15 entry 로 흡수).
- 비용 정책: 런타임 번역/추가 DB/백엔드 호출 없음. Free V1 사용 적합성은 운영 조건이 바뀔 때마다 재평가하고, 누락 관측은 고유 50개로 제한한다.
- 릴리스 전 `pwsh scripts/verify-exercise-translations.ps1`로 현재 추천 풀 ID/이름·지침 지문 드리프트를 확인한다. 일시 오류는 제한 재시도하며, 외부 API rate limit 때문에 매 CI에는 배선하지 않았다.

## 2026-10-10 (Gemini -> Claude)

**현재까지의 작업 상태 (Current Status):**
- `HomeTopBarActions.kt` 내 오버플로 메뉴를 Material Design 3 가이드라인에 기반하여 3개의 역할별 그룹(운동 및 활동, 식단, 설정 및 계정)으로 재정렬하고, `HorizontalDivider`를 사용해 시각적으로 분리하는 리팩토링을 완료했습니다.
- 메뉴 가독성 및 개연성 향상을 위한 구조 개선 완료 (기록/통계/목표/배지 -> 식단 -> 프로필/테마/로그아웃).

**다음 할 일 (Next TODOs for Claude):**
- 메뉴에 추가 항목이 발생할 시 현재의 그룹핑(운동/식단/계정) 규칙을 유지하고 `HorizontalDivider`를 활용하세요.

**의존성 및 주의사항 (Context & Constraints):**
- Material 3의 `HorizontalDivider`가 적용되었습니다.

## 2026-09-26 (Gemini -> Claude)

**현재까지의 작업 상태 (Current Status):**
- 루트 `.env` 파일(DeepSeek Key 등 포함)이 공개 저장소에 유출되지 않도록 `.gitignore`에 등록 완료.
- `2026-09-20-meal-plan-api-selection-design.md` 문서(v0.3.0 완료 건)를 프로젝트 컨벤션(`docs/plans/README.md`)에 따라 `docs/plans/logs/backend.md` 토픽 렛저로 아카이브(흡수) 완료.
- `docs/SPEC.md` 내 설계 문서 참조에 리다이렉트(`(→ docs/plans/logs/backend.md 2026-09-20 entry 로 흡수)`)를 병기하여 링크 무결성 검증(`check-plans-links.sh`) 통과.
- `gen_plans_index.py`를 통해 `docs/plans/README.md` 인덱스 갱신 완료(활성 작업 0건).

**다음 할 일 (Next TODOs for Claude):**
- 추가적인 식단 기능 백엔드/클라이언트 작업 필요 시 `docs/plans/logs/backend.md`의 2026-09-20 엔트리 및 `docs/SPEC.md` 참조.

## 2026-09-24 (Gemini -> Claude)

**현재까지의 작업 상태 (Current Status):**
- ExerciseDB를 오픈소스 wger API로 전환하거나 정적 자체 DB(JSON)로 내재화하는 방안을 모두 검토 및 기획하였으나, **사용자(Product Owner)의 결정에 따라 기획이 반려(Reject)되었습니다.**
- 주된 반려 사유: wger API의 한국어 지원 미비 및 마땅한 한국어 대체 API의 부재.
- 작성되었던 마이그레이션 기획 문서(`2026-09-24-exercisedb-to-wger-migration-design.md`)는 파기되었습니다.

**다음 할 일 (Next TODOs for Claude):**
- 현재는 마이그레이션 없이 **기존 ExerciseDB 연동 방식을 그대로 유지**합니다. 
- 이와 관련하여 추가적인 코드 수정이나 아키텍처 변경 작업을 수행하지 마십시오.

**의존성 및 주의사항 (Context & Constraints):**
- 운동 데이터 소스는 당분간 기존 `ExerciseDbApi` 및 `ExerciseDbDataSource` (RapidAPI/AscendAPI 기반) 구조를 그대로 유지합니다.
