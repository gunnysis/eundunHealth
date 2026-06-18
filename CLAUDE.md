# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

eundunHealth(은둔헬스) is a Korean health/fitness Android app with a **FastAPI (Python 3.12)** backend. Users input body metrics, receive auto-generated weekly workout plans from the **OSS ExerciseDB** (`oss.exercisedb.dev`, no auth), track completion via Health Connect, set goals (weight / body fat) and earn badges. All UI text is Korean.

**Current state**: versionName `0.1.17` (versionCode `31` — 공개 출시 전 전체 감사(브랜치 `fix/pre-release-audit`): Rule 8 inline 에러 배너(OnboardingScreen·HomeScreen·ProfileScreen) + HistoryScreen 완료/미완료 a11y contentDescription + BadgeViewModel 특성화 테스트(3건) + 백엔드 프로필 극단값 경계 테스트(2건) + account_service 로그 구조화 + 문서 버전 드리프트 5건 정정(android @Test 139→142·backend pytest 77→79). 직전 v0.1.16/30 — 출시 후 심층 감사 개선(브랜치 `feature/deep-audit-improvements`): JWKS 이벤트루프 블로킹 제거(`asyncio.to_thread`+5s timeout) · RetryInterceptor/Profile/History/Statistics/Onboarding/Goal 테스트(@Test 118→138) · GoalScreen silent-failure→`ErrorContent` · DayPlanCard 포맷팅 `remember` perf · 오늘의활동 a11y(`mergeDescendants`) · 백엔드 `pool_pre_ping`·history COUNT window 1쿼리·`user_profile_history` 복합인덱스(alembic `b78b256c2b20`)·계정삭제 orphan reaper(fail-safe)·sentry-sdk 2.63.0; 공식문서 fact-check 2건 정정(PyJWKClient 기본 timeout 30s · Compose strong skipping 기본활성→stability config Won't-do). 설계 `docs/plans/2026-06-17-post-release-audit-improvements-{design,plan}.md`. **후속 PR #127**: orphan reaper Container Apps Job 프로비저닝(UAI·주간 cron `0 18 * * 0`·수동실행 Succeeded·운영 런북 `docs/ops/azure-container-apps-jobs.md`) + 하드닝(reaper 트랜잭션 사용자단위 commit·스크립트 self-locating·requirements cp949 pre-commit 가드; @Test 139·backend pytest 77). 직전 v0.1.15 = 감사 LOW 후속: ① SideEffect 수집 라이프사이클-aware(`ObserveAsEvents`) + 백엔드(alembic rest_day server_default·CORS 와일드카드 차단) + starlette 1.3.1 CVE, PR #123 `078a24fb`. 직전 v0.1.14 출시 준비 종합: 실기기 제보 2버그 근본수정(① 빈 운동계획 = R8 keep 갭 → 패키지 단위 keep + 자가치유 ② 완료 토글 해제 보존 = 수동 우선 `manuallySet`) + 4-에이전트 전수감사 출시차단 해소(완료 정합성·입력검증 500→400·인증 토큰갱신 견고화·운동상세 GIF/복사/데이터흐름·캐시/파싱/KST·폴리시) + 재발방지 가드, PR #122 squash `e2d7460`; 백엔드 자동배포 완료(`manual`/`manuallySet` live), Android Play 업로드 대기; 직전 v0.1.13 = 코드베이스 리팩토링 #107~#112). 버전 SSoT = 루트 `version.properties`(앱) + `backend/app/__init__.py:__version__`(API `1.0.0`, 앱과 독립) — 정책 `docs/conventions/versioning.md`, bump `bash scripts/bump-version.sh`. v0.1·v0.2·v0.3 spec all implemented. Production cutover from Ktor → FastAPI completed. **백엔드 인프라(2026-06-09)**: scale-to-zero cold start(측정 21.5s) 제거 = `min/max 1/3` warm baseline + **Key Vault full IaC**(secret→KV 참조 · system MI pull/resolve · health probe 3종 startup/liveness=`/health`·readiness=`/health/ready` · `backend/containerapp.yaml` `--yaml` 배포). Play Store: **출시 전(pre-release) — 프로덕션 미출시**. 0.1.13/27 프로덕션 심사는 **취소**(이후 개선 지속), 프로덕션 사용자 0(DB 테이블 0건·reaper purged 0 과 일관). 최신 버전 = **0.1.17/31**(코드 머지·백엔드 자동배포 완료; 마지막 실제 빌드 산출물 = **v0.1.17/31**(2026-06-19 preflight — AAB 8.35MB·APK 5.97MB·Sentry 매핑 `af1a233a`), Play 업로드만 실제 출시 시점에 — Claude 불가). 0.1.14/28 은 ① 미포함. 출시 산출물 단일 위치 `app/build/outputs/bundle`(preflight·AS 마법사 동일 경로, stale `app/release/` 삭제). Detailed runtime snapshot: `docs/ops/operations-snapshot.md`.

> Legacy Ktor backend source is archived under `D:\backup\dev\project\eundunHealth\`. Infrastructure rollback would require rebuilding from that archive (Ktor images were removed from ACR after FastAPI stabilized).

## Build & Run Commands

### Android App (root project)
```bash
./gradlew :app:assembleDebug           # Debug build
./gradlew :app:assembleRelease         # Release APK (R8 + signing)
./gradlew :app:bundleRelease           # Release AAB (Play Store)
./gradlew :app:testDebugUnitTest       # Run all unit tests
./gradlew :app:testDebugUnitTest --tests "com.gunnys.eundunhealth.domain.usecase.SyncHealthDataUseCaseTest"

# 코드 품질
./gradlew :app:spotlessApply           # 자동 포맷 (ktlint)
./gradlew :app:spotlessCheck           # 포맷 검증 (CI에서 사용)
./gradlew :app:detektDebug             # 정적 분석 (baseline-debug.xml로 기존 위반 박제)
./gradlew :app:detektBaselineDebug     # baseline 재생성

# git pre-commit hook 활성화 (clone 직후 1회)
git config core.hooksPath .githooks
```

### Backend (FastAPI, in `backend/`)
```bash
cd backend
python -m venv .venv
.venv/Scripts/pip install -r requirements-dev.txt

# 로컬 실행 (Docker Compose 권장)
docker compose up -d                  # PostgreSQL + uvicorn 동시 기동
docker compose logs -f api
docker compose down -v                # 정리

# 호스트 직접 실행 (DB는 별도 기동 필요)
.venv/Scripts/uvicorn app.main:app --reload --port 8080

# 테스트 + 정적 검사
.venv/Scripts/pytest tests/ -v --cov=app
.venv/Scripts/ruff check app/ tests/
.venv/Scripts/mypy app/
.venv/Scripts/bandit -r app -ll
.venv/Scripts/pip-audit -r requirements.txt --strict --ignore-vuln PYSEC-2026-161

# Alembic 마이그레이션 (현재 head: b78b256c2b20)
.venv/Scripts/alembic upgrade head
.venv/Scripts/alembic revision --autogenerate -m "..."

# OpenAPI 스펙 동기화 — 라우터/스키마 변경 시 필수
# backend/openapi.json이 Android openapi-generator(:app:openApiGenerate)의 입력이자
# backend.yml의 drift detection step 기준이 된다.
bash ../scripts/sync-openapi.sh
```

### Deployment

**자동 (권장)** — main 브랜치 push로 GitHub Actions가 빌드 → Trivy → ACR push → secret precheck → Container App 업데이트 → /health 검증을 일관 수행. paths 필터는 `backend/**` 또는 `.github/workflows/backend.yml`.
```bash
# 수동 트리거 (긴급 재배포 / secret rotation 검증)
gh workflow run backend.yml --ref main
```

**수동 (로컬)** — CI를 우회해야 할 때만:
```bash
bash C:/programming/docker/eundunhealth-api/redeploy.sh [tag]
```
FastAPI uvicorn 이미지 빌드 → ACR `eundunhealthacr` → Container App `eundunhealth-api` (RG `apps`, Korea Central) 업데이트 → /health 헬스체크 → timestamp 태그 자동 정리(최근 5개만 보존). 환경변수 변경은 별도 `az containerapp update --set-env-vars` 또는 `secret set`. 자세한 절차는 `docs/ops/migration-runbook.md`.

**Secret 등록 / SP 만료 갱신** — `AZURE_CREDENTIALS` 등록 또는 service principal credential 만료 시:
```powershell
pwsh -File scripts\register-azure-credentials.ps1 -Verify
```

Docker development location: `C:\programming\docker\eundunhealth-api`

### Device Testing (Android)
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
# 또는 release APK
adb install -r app/build/outputs/apk/release/app-release.apk
```

**Live Edit (Compose UI 반복 개발 — IDE 전용, 저장소 산출물 없음)**
> 출처: [developer.android.com/develop/ui/compose/tooling/iterative-development](https://developer.android.com/develop/ui/compose/tooling/iterative-development) (Last updated 2026-06-02). per-developer IDE 설정이라 repo/`.idea` 커밋 대상 아님.

켜기: `File > Settings > Editor > Live Edit` → 활성화. 모드는 **Manual on Save (`Ctrl+S`)** 권장 — Automatic 은 spotless/detekt 가 무거운 본 프로젝트에서 의도치 않은 빈번 push 유발. Running Devices 창 우측 상단 **초록 체크 = up-to-date**, **"Out Of Date" 클릭 → 컴파일 에러 표시**.

호환성 (MEASURED 2026-06-08, 블로커 0):
- 디바이스 **API 30+ 필수** — 본 앱 `minSdk=26` 이므로 **API 30+ 에뮬레이터/기기로 배포**해야 Live Edit 활성 (API 26~29 기기는 비활성).
- `kotlinOptions.moduleName` 커스텀 금지 → `app/build.gradle.kts:132-134` 는 `jvmTarget` 만 설정 (위반 없음). AGP 9.2.1 / Compose BOM 2026.05.01 모두 요구치 상회. `android.builtInKotlin=false` 는 Live Edit 가 Gradle 미경유라 무관.

적용 범위 — **Composable 함수 *바디* 만** 핫스왑:
- ✅ `Modifier`(padding/spacing), `Color`/`dp` 상수, 애니메이션 튜닝, 레이아웃 미세조정 → `ui/theme/`·`ui/components/` 시각 마감 작업의 inner-loop 가속.
- ❌ 함수 시그니처/추가·삭제, import 추가·삭제, 클래스 계층, 비-Composable 필드 변경 → **ViewModel/UDF 상태(룰 11)·Hilt/DI·네비·로직 변경은 대상 아님** (전체 Run 재배포 필요). 개별 Composable + 다크모드/locale 변형 검증은 `@Preview`(이미 6파일 사용) 병행이 더 적합.

주의: Live Edit 가 적용된 프로세스는 약간의 오버헤드가 있어 **성능 측정 금지** → clean release build(룰 2 preflight 경로)로만 벤치. debugger 사용 시 수정 클래스는 전체 재실행 필요.

## Architecture

### Multi-Project Structure
- **Root project** includes only `:app` (Android). Backend는 별도 디렉토리 `backend/`에 FastAPI(Python) 프로젝트로 분리.
- Dependency versions centralized in `gradle/libs.versions.toml` (Android).
- Build secrets (Supabase URL/key, Backend URL, Sentry DSN) loaded from `local.properties` into BuildConfig fields. ExerciseDB는 OSS API라 키 불필요.
- Release signing credentials도 `local.properties`에 (`RELEASE_STORE_PASSWORD`, `RELEASE_KEY_PASSWORD`, `RELEASE_KEY_ALIAS=eundunhealth_sign_key`). `build.gradle.kts`에 하드코드 금지.

### Android App (`app/`)
Package: `com.gunnys.eundunhealth`

**Clean Architecture layers:**
- **UI** (`ui/`): Compose 화면 + ViewModels. Navigation은 `navigation/` 의 sealed `Screen`. 화면: splash, auth(login/signup/forgot-password), onboarding, home, profile, workout detail, history, badges, **statistics**(v0.2), **goal**(v0.3).
- **Domain** (`domain/`): 모델, 리포 인터페이스, use case. `domain/model/AppError.kt`에 통일 에러 sealed class + `Throwable.toAppError()` + `AppError.reportToSentry()`.
- **Data** (`data/`): Repository 구현, Retrofit API(`remote/api/EundunApi.kt`), Room(`local/`), Health Connect, Supabase auth(`auth/AuthRepositoryImpl.kt`), DataStore.
- **DI** (`di/`): Hilt 모듈 — `NetworkModule`, `SupabaseModule`, `DatabaseModule`, `RepositoryModule`(GoalRepository 포함), `CoilModule`.

**Key patterns:**
- **UDF-Enhanced ViewModel** (룰 11): 단일 `_uiState: MutableStateFlow<XxxUiState>` + `@Immutable` UiState/SideEffect sealed class + `collectAsStateWithLifecycle` + `Channel<SideEffect>`. 12 VM 전수 마이그레이션 완료 (2026-06-06). 자세한 체크리스트는 룰 11 참조.
- **Auth ViewModel 분리**: `AuthViewModel`(session lifecycle) + `LoginViewModel` / `SignupViewModel` / `ForgotPasswordViewModel`(per-screen). AuthVM 에 화면별 로직 추가 금지.
- ViewModel은 `AuthRepository.getCurrentUserId()`로 userId를 받는다 — `SupabaseClient` 직접 주입 금지.
- Token: `NetworkModule`의 `AtomicReference`, `TokenAuthenticator`가 401 시 5초 timeout으로 갱신 + 실패 시 무효화.
- `RetryInterceptor` 지수 백오프 (3회 / 500ms·1s·2s).
- Auth 에러는 `AuthRepositoryImpl.mapAuthError()`로 한국어 사용자 메시지.
- 공통 UI: `ui/components/` (`ProfileSummaryCard`, `ProfileSlider`, `SkeletonUi`, `ErrorContent`, `EmptyContent`, `AuthErrorBanner`).
- `SentryInitProvider`는 AndroidManifest `tools:node="remove"`로 비활성 — `EundunHealthApplication`에서 DSN blank 검사 후 수동 init.
- **WeeklyPlanDao.getPlan(userId, weekStart)**: userId 필터링 필수 (v0.1 CRITICAL fix). EundunDatabase version=2 + fallbackToDestructiveMigration.

### Backend (`backend/` — FastAPI / Python 3.12)
Package: `app`

- `app/main.py` — FastAPI 앱 + lifespan(DB 엔진/Sentry) + 모듈 레벨 CORS(`add_middleware`는 lifespan 내부 금지) + 글로벌 exception handler.
- `app/config.py` — pydantic-settings, `get_settings()` `@lru_cache`.
- `app/database.py` — `Base = DeclarativeBase`, `get_db()` UoW 패턴 (Request → `app.state.session_factory`).
- `app/dependencies.py` — JWKS 기반 JWT 검증 (`PyJWKClient` 24h TTL 캐시, ES256, `authenticated` audience).
- `app/exceptions.py` — `AppException`/`NotFoundException`/`ConflictException`/`BadRequestException`.
- `app/models/` — SQLAlchemy 2.0 `Mapped[T] = mapped_column(...)` (Postgres UUID).
- `app/schemas/` — Pydantic `CamelSchema` (alias_generator=to_camel, populate_by_name=True). v0.3에 `goal.py`, `statistics.py` 추가.
- `app/repositories/` — DB 접근 추상화. `profile_history_repo`, `goal_repo` (v0.3).
- `app/services/` — 비즈니스 로직. `account_service`가 Supabase Admin API로 Auth 사용자 삭제. `statistics_service` (v0.2), `goal_service` (v0.3).
- `app/routers/` — 얇은 라우터, Service 위임. v0.3 `goal.py` 신규.
- `alembic/` — async 엔진 연동. **head: `b78b256c2b20` (user_profile_history `(user_id, recorded_at)` 복합 인덱스; 직전 `c849579de6c4` rest_day server_default 일관화)**.

**API Endpoints (14개 JWT 필요 + 공개 6개. 공개 라우트 `/health`·`/health/ready`·`/.well-known/assetlinks.json`·`/auth/confirm`·`/privacy`·`/account-deletion`. 마지막 둘은 `docs/store/*.md` 를 `app/legal/` 로 동기화(sync-legal-docs.sh)해 md→HTML 렌더 — Play 등록 URL. HTML 브라우저 라우트 3종(`/privacy`·`/account-deletion`·`/auth/confirm`)은 `include_in_schema=False` → openapi.json(Android 생성기 입력)에서 제외[죽은 클라이언트 메서드 방지], 라우트는 그대로 동작):**
```
GET    /health
GET    /privacy                              # 공개 — 개인정보 처리방침 (Play URL)
GET    /account-deletion                     # 공개 — 계정·데이터 삭제 안내 (Play URL)
GET    /profile
PUT    /profile                              # restDay 포함
GET    /profile/history?limit=50             # v0.3
GET    /weekly-plan?week_start=
POST   /weekly-plan
PATCH  /weekly-plan/complete
GET    /weekly-plan/history?page=&size=
GET    /weekly-plan/previous?week_start=     # v0.2 — 알고리즘 입력
GET    /weekly-plan/statistics?weeks=12      # v0.2 — 완료율 + 스트릭
GET    /badges
POST   /badges/{key}                         # 9종 (마일스톤 4 + 목표 달성 2 추가)
GET    /goals                                # v0.3
PUT    /goals                                # v0.3
DELETE /account
```

## Key Technical Details

### Android App
- **Kotlin 2.2.10**, KSP 2.3.2 (Kotlin과 호환 필요)
- **Gradle 9.5.1**, AGP 9.2.1
- **Min SDK 26**, Target SDK 37, Java 17
- **버전 관리**: SSoT = 루트 `version.properties`(앱 versionName/versionCode) + `backend/app/__init__.py:__version__`(API, 앱과 독립). 정책·bump·프론트 표시 절차는 `docs/conventions/versioning.md`. bump 은 `bash scripts/bump-version.sh <new-version>`.
- **App version**: versionName **`0.1.17`**, versionCode **`31`** — SSoT 는 루트 `version.properties`(직접 편집 대신 `bash scripts/bump-version.sh <ver>`), 이력은 `docs/CHANGELOG.md`. versionCode 는 단조증가 정수(최대 2,100,000,000). 정책 전문: `docs/conventions/versioning.md`
- **Sentry Android 8.43.2** (eundunhealth 프로젝트) — 16KB page-aligned native libs; `packaging.jniLibs.useLegacyPackaging = false`
- **Vico 3.2.2** (compose-m3) — 통계 + 목표 진행 차트
- **OkHttp 5.3.2** + **Coil 3.4.0** (coil3 module group `io.coil-kt.coil3`, `coil-network-okhttp` 포함)
- **Detekt 1.23.8 + Spotless 8.6.0 + ktlint 1.5.0**
- Supabase JWT algorithm: **ES256 (ECDSA)** — backend uses JWKS public key verification
- Network security config disables cleartext except localhost/10.0.2.2 in debug
- 시간대: 한국(KST)
- **UI 문자열 = 한국어 하드코딩 리터럴(의도된 결정)**: 한국어 전용 제품이라 `strings.xml`은 `app_name`만 두고 화면 텍스트·`contentDescription`·에러 메시지를 코드에 직접 한국어로 둔다. string resource 리소스화/i18n 은 다국어 요구가 생기기 전까지 **비대상** — 감사·리뷰 시 "string resource 미사용"을 결함으로 재플래그하지 말 것.
- pre-commit hook (`.githooks/pre-commit`)이 .kt 변경 시 spotlessApply + detektDebug + **collectAsState anti-pattern 검사** (룰 11) 자동 실행

### Backend (FastAPI)
- **Python 3.12**, FastAPI 0.137.1, SQLAlchemy 2.0.51 async + asyncpg 0.31.0, Alembic 1.18.4
- **API version `1.0.0`** — `backend/app/__init__.py:__version__` → `FastAPI(version=)` → OpenAPI `info.version`. 앱(`version.properties`)과 **독립**. bump 시 `bash scripts/sync-openapi.sh` 재싱크 필수(drift 가드). Dockerfile 은 `apt-get upgrade` 레이어로 base-image OS CVE 자가치유(Trivy HIGH 차단 회피)
- **starlette 1.3.1** (PYSEC-2026-161 + GHSA-82w8-qh3p-5jfq + GHSA-jp82-jpqv-5vv3 fix 포함; PR #123), PyJWT 2.13.0 (JWKS), httpx 0.28.1 (Supabase Admin API)
- **Sentry SDK 2.63.0** (eundunhealth-backend 프로젝트) — DSN secretref `sentry-dsn-backend`
- mypy strict 통과, ruff/bandit clean, pytest 87/87 PASS, coverage ~97% (coverage 측정 코어 = `sysmon`/PEP 669 — 기본 ctrace 는 async `await` 이후 라인 과소측정, `pyproject.toml [tool.coverage.run] core` 참조; mypy 실행은 래퍼 깨짐 회피 위해 `python -m mypy`)
- Alembic head `b78b256c2b20` (user_profile_history `(user_id, recorded_at)` 복합 인덱스; 직전 `c849579de6c4` rest_day server_default 일관화)
- `/health` (process liveness) + `/health/ready` (DB `SELECT 1` → 200/503, readiness probe 전용)

### Infrastructure
- **Container App** `eundunhealth-api` (RG `apps`, Korea Central, **Min/Max 1/3 warm baseline** — cold start 제거. health probe 3종. IaC: `backend/containerapp.yaml` `--yaml` 배포)
- **Key Vault** `kv-eundunhealth` (RG `apps`, Standard, **Azure RBAC**, 90d soft-delete + purge protection) — 백엔드 secret 4개(KV 참조, 직접값 아님). Container App **system MI** = Secrets User(KV) + AcrPull(ACR), CI SP = Secrets User(KV). audit → Log Analytics `workspace-appsDOlM`
- **ACR** `eundunhealthacr` (Basic SKU — retention 정책 미지원, redeploy.sh가 timestamp 태그 최근 5개만 보존)
- **Azure PostgreSQL** Flexible Server `healthapp` (B1ms, 32GB, Korea Central). Firewall 기본 차단 + Container App IP만 허용 + `allow-azure-services`
- **Supabase** Korea 리전, project `ttzzbfoksncqazvcsfiu`
- **Sentry**: Android `eundunhealth`, Backend `eundunhealth-backend` (각 별도 project)
- CI: GitHub Actions (`backend.yml` + `android.yml`) + Dependabot

## 운영 안전 규칙 (Claude 작업 시 필독)

지난 인시던트들의 root cause는 모두 `docs/ops/incident-log.md`에 기록됨. 다음 5개 룰은 그 결과로 만들어진 강제 가이드 — 어기면 운영 사고로 직결.

### 룰 1 — ACR 정리는 `untag`만 사용 (INC-01)
`az acr repository delete --image <repo>:<tag>`는 그 tag가 가리키는 **manifest 자체를 삭제**해서 같은 digest를 공유하는 `latest`·`fastapi-latest` 등이 함께 사라진다. 옛 timestamp 태그 정리는 반드시:
```bash
az acr repository untag --name eundunhealthacr --image eundunhealth-api:<tag>
```
또는 `bash C:/programming/docker/eundunhealth-api/redeploy.sh`의 자동 후크에 맡길 것. `monitoring-and-cost.md §6.1` 매트릭스 참조.

### 룰 2 — 릴리스 산출물은 `releaseArtifacts` 하나로만 (INC-04)
AAB와 APK를 따로 빌드하면 사이에 versionCode가 바뀌어 어긋날 수 있다. **반드시**:
```bash
bash scripts/preflight-release.sh      # 모든 게이트 + AAB + APK + Sentry 매핑 (출시용, 권장)
# 또는 (게이트 없이 산출물만)
./gradlew :app:releaseArtifacts -PsentryRelease=true   # 실제 출시 시 플래그 필수
./gradlew :app:releaseArtifacts                         # 로컬 실험용 (Sentry 매핑/업로드 생략)
```
**Sentry 매핑 게이트**: `-PsentryRelease=true`(preflight 가 자동 설정) 가 있어야만 ProGuard 매핑 UUID 생성 + Sentry 업로드. 없으면 로컬 release 빌드가 결정적 + 업로드 없음 (build.gradle.kts `sentry` 블록). **출시 산출물은 반드시 preflight 또는 플래그 경로로 빌드** — 안 그러면 production crash deobfuscation 불가.

### 룰 3 — Alembic autogenerate은 PostgreSQL 컨테이너 사용 (INC-07)
`pytest`가 띄우는 SQLite로 autogenerate를 돌리면 UUID↔NUMERIC 거짓 양성이 마이그레이션 파일에 박혀 프로덕션에서 cast 에러 발생. **항상**:
```bash
bash scripts/alembic-autogen.sh "add user_settings table"
```
이 스크립트가 `docker compose up -d db`로 PG 16을 띄우고 그 위에 autogen을 수행. 끝나면 컨테이너 자동 정리(`-k`로 유지 가능).

### 룰 4 — `lifespan` 안에서 `app.add_middleware()` 호출 금지 (INC-03)
starlette 0.49+ 부터 lifespan startup에서 middleware 추가하면 `RuntimeError: Cannot add middleware after application started`. CORS 등은 **모듈 레벨**에서 등록. `backend.yml`의 `runtime-smoke` job이 docker compose로 이 회귀를 PR 단계에서 차단.

### 룰 5 — Supabase 프로젝트는 v1.0 출시 후 절대 교체 금지 (INC-14)
프로젝트가 갈리면 user_id namespace가 바뀌어 기존 사용자가 모두 orphan이 된다. 출시 전(현 상태)에만 5개 사용자 테이블 TRUNCATE로 안전 교체 가능. 출시 후 불가피하면 매핑 테이블 + 백필 + 사용자 공지 절차 필수.

### 룰 6 — backend.yml `secretref` 추가는 항상 세 가지 동시 변경 (INC-18)
`.github/workflows/backend.yml`의 `--set-env-vars`에 새 `<ENV>=secretref:<name>`을 넣으면 같은 PR에서 반드시:
1. `az containerapp secret set --secrets "<name>=<value>"` 실행 (운영자가 PR 머지 전)
2. `backend.yml`의 "Verify required Container App secrets exist" step `REQUIRED` 문자열에 `<name>` 추가
3. `docs/ops/operations-snapshot.md` §2 Secrets 목록 갱신

세 가지 중 하나라도 빠지면 main 머지 후 첫 deploy job에서 `ContainerAppSecretRefNotFound`로 실패한다. PR template Backend 섹션 체크리스트 + `monitoring-and-cost.md §6.6` 참조.

### 룰 7 — 스키마 변경 PR 은 같은 PR 에서 entrypoint 검증 포함 (INC-2026-05-27-01)
`backend/alembic/versions/` 에 새 파일을 추가하면 반드시 같은 PR 에서:
1. `bash scripts/alembic-autogen.sh "..."` 로 PG 컨테이너 위에서 작성 (룰 3 — SQLite false positive 방지). 수동 작성도 동일하게 `docker compose up -d db` 위에서 검증.
2. 로컬 `cd backend && docker compose up -d --build` → `docker compose logs api` 에 `[entrypoint] ... alembic upgrade head` 라인 + `/health` 200 둘 다 확인. 같은 PR 의 `backend.yml runtime-smoke` job 이 CI 가드.
3. `docs/ops/operations-snapshot.md` Alembic head 값을 새 revision hash 로 교체.

자동 적용 경로 (수동 작업 0): main 머지 → `backend.yml` deploy job → 새 image 의 `backend/entrypoint.sh` 가 `alembic upgrade head` 실행 → Container Apps startup probe (`/health`) 통과 시 traffic 전환. Alembic `alembic_version` row lock 이 다중 인스턴스 race 안전성 보장.

**예외**: 5분 이상 데이터 백필 마이그레이션은 Container Apps startup probe timeout 으로 entrypoint 가 실패할 수 있음 → Container Apps Jobs 패턴으로 분리 검토 (`docs/plans/logs/backend.md` 의 schema-drift entry §9 잔여 리스크).

### 룰 8 — Auth/UI 의 사용자 액션 실패 표시는 inline + persistent 패턴 (INC-2026-05-26-01)
Auth (signup / login / forgot-password) + 기타 사용자 액션 결과의 실패/에러 UI 는 **inline component** (form 안 banner / card / surface) + **사용자 액션까지 persistent** + **a11y `liveRegion`** + **Sentry breadcrumb** 4 요소 모두 만족. **Snackbar 단독 사용 금지** — 가시성 결함 (위치 하단 + 자동 dismiss + 짧은 duration) 의 반복 회귀 차단.

체크리스트 (새 화면의 Failed/Error UI 작성 시):
1. **위치**: form 의 CTA 버튼이 보이는 시각 영역 안 — 보통 headline 아래 + 첫 input 위. 사용자가 CTA 누르기 전 반드시 시야에 들어와야 함.
2. **dismiss 정책**: 자동 timeout 금지. 사용자 의도 신호 (button enabled 시점 / 명시적 X / 다음 액션) 까지 유지. input typo 같은 미세 변경은 보존.
3. **a11y**: Compose `Modifier.semantics { liveRegion = LiveRegionMode.Polite }` — TalkBack 사용자에게 즉시 음성 알림.
4. **Sentry**: `Sentry.addBreadcrumb(Breadcrumb().apply { category = "<domain>.error_banner_shown"; level = SentryLevel.INFO; setData(...) })` — production 디버깅 시 사용자가 어떤 에러를 봤는지 timeline 추적 가능.
5. **컴포넌트 위치**: 첫 화면 = `<Screen>.kt` 안 `@Composable private fun` (YAGNI). 두 번째 화면 마이그레이션 시점에 `ui/components/` 로 promote — premature generic 회피.

참조 구현: `app/src/main/java/com/gunnys/eundunhealth/ui/components/AuthErrorBanner.kt` (v0.1.7 promote, v0.1.6 #58 SignupScreen private 으로 시작). v0.1.7 (#TBD) 에서 LoginScreen + ForgotPasswordScreen 마이그레이션 시점에 `ui/components/` 로 promote — 3 Auth 화면 공유.

**예외**: 비-critical 일회성 알림 (e.g., 성공 toast "저장됐습니다") 은 Snackbar 그대로 OK. 룰의 대상 = "사용자 액션이 실패했고, 사용자 후속 액션이 필요한 경우".

### 룰 9 — Design doc 의 baseline / 추정값은 측정 후 결정 (PR #68 lesson L2)
Design 또는 plan 작성 시 "약 N건", "~M 파일" 같은 정량 표현은 **측정 명령으로 확정 후 기록**. 추정 후 측정하면 chain 전체 drift (예: PR #68 — D415 2건이 모두 `main.py` ignore 안에 있어 실제 작성 대상 63 → 59, plan task scope 가 drift).

**체크리스트**:
1. Design doc 의 정량 표현마다 측정 명령 1줄 동봉 (e.g., `grep -c ... | wc -l` 결과 = N).
2. 측정 환경 부재 시 3 라벨 명시 — `MEASURED` / `DEFERRED — verify at Phase N` / `ESTIMATE-ONLY` (`_templates/design.md` 참조).
3. spec self-review step 에서 controller 가 측정값 1회 재확인 — drift 시 fix.

**예외**: 정성 표현 (e.g., "복잡한 case", "trivial fix") 은 본 룰 비대상.

### 룰 10 — Subagent reviewer 의 측정 결과는 controller 가 직접 fact-check (PR #68 lesson L6)
SDD (superpowers:subagent-driven-development) 의 spec reviewer / code quality reviewer 가 **측정 수치** (lint 위반 수, 테스트 수, 커버리지 등) 보고 시 controller 가 같은 명령 1회 실행 + 결과 일치 확인. 불일치 시 reviewer 의 명령 형태 (e.g., 룰 9 의 측정 명령 함정, ruff `--select` 함정 [[ruff-select-flag-pitfall]]) 의심.

**Trigger 좁히기**:
- 측정 수치 보고 시 → fact-check 필수
- 정성 평가 (e.g., "코드 깔끔", "스타일 OK") → fact-check 면제 (verify 비용 > 효용)
- 일반 Agent tool (Explore / general-purpose) 호출 결과 → 측정 수치 보고 시만

**사례**: PR #68 Task 3 spec reviewer 가 D107 위반 85건 보고 → controller 가 직접 측정 = 32건. D107 글로벌 ignore 누락 (룰 9 + ruff `--select` 함정). controller 재측정 + plan fix.

**예외**: SDD 외 일반 대화의 답변, code-explorer 의 발견 사항 등은 비대상 (별도 verify 룰).

### 룰 11 — ViewModel 은 UDF-Enhanced 패턴 준수 (2026-06-06 Phase 1-5 마이그레이션 후)
12 ViewModel 전수를 UDF-Enhanced 패턴으로 마이그레이션 완료. 신규 VM 작성 · 기존 VM 수정 시 아래 5개 체크리스트 **모두** 만족.

**체크리스트** (새 ViewModel 작성 · 기존 ViewModel 수정 시):
1. **단일 `_uiState` MutableStateFlow** — 화면의 모든 렌더 상태를 `data class XxxUiState(...)` 하나로 관리. 별도 `_error` / `_isLoading` StateFlow 금지 (허용 예외 아래 참조).
2. **`@Immutable` 필수** — UiState sealed class + SideEffect sealed class + 모든 하위 타입에 `@Immutable` 어노테이션.
3. **`collectAsStateWithLifecycle` 전용** — Screen Composable 에서 `collectAsState()` 사용 **금지**. 반드시 `collectAsStateWithLifecycle()` 사용 (lifecycle-aware, onStop 시 collection 중단).
4. **SideEffect Channel** — 일회성 이벤트 (navigation, snackbar) 는 `Channel<SideEffect>(Channel.BUFFERED)` + `receiveAsFlow()` 로 전달. UiState 에 넣지 않는다.
5. **AuthViewModel 독립성** — `AuthViewModel` 은 session lifecycle (로그인 상태 · 딥링크) 전용. per-screen 로직 (signup validation, login form) 추가 금지 — `LoginViewModel` / `SignupViewModel` / `ForgotPasswordViewModel` 에 위임.

**허용 예외** (독립 sub-operation — 메인 UiState 와 lifecycle 이 다른 경우):
- `AuthViewModel`: `_sessionState` / `_deepLinkError` / `_pendingEmail` (session-scoped, 전 화면 공유)
- `LoginViewModel` / `SignupViewModel`: `_resendCooldownSec` / `_resendError` (이메일 재발송 타이머, 메인 UiState 와 독립)
- `HomeViewModel`: `themeMode` (DataStore `stateIn()`, MutableStateFlow 아님)

**자동 가드**:
- CI: `.github/workflows/android.yml` "Check collectAsState anti-pattern" step (import + 호출부 grep)
- Pre-commit: `.githooks/pre-commit` staged `.kt` 파일 한정 동일 검사
- 항목 2 (`@Immutable`) / 항목 4 (SideEffect) / 항목 5 (AuthVM scope) 는 AST 분석 필요 → 코드 리뷰 + Claude Code 준수로 경감

**Baseline** (MEASURED 2026-06-06):
- `collectAsState()` 호출: **0건** (`grep -rn '\.collectAsState(' app/src/main/java/`)
- `collectAsStateWithLifecycle` 사용: **20건** across 13 files (2026-06-16 재측정 — 2026-06-06 마이그레이션 시 33건, 이후 리팩토링·`ObserveAsEvents` 도입으로 감소. 핵심 불변식 = `collectAsState()` 0건 유지)

### 룰 12 — Gson 반사 모델은 R8 keep 전수 + 패키지 단위 (INC 2026-06-15 빈 운동계획 회귀)

Gson 으로 역직렬화되는 모델(Retrofit 응답 타입 + 그 중첩·래퍼 타입 **전부**)은 **필드에 `@SerializedName` 가 있거나 `-keep class … { *; }`** 로 보호돼야 한다. 둘 다 없으면 릴리스 R8 이 필드/클래스를 제거 → Gson 이 못 채워 nullable/default(예: `List<T>` → `emptyList()`)로 **silent** 폴백한다. 디버그/단위테스트는 R8 미적용이라 못 잡는다.

- **단일 클래스 keep 금지 → 패키지 단위 keep**: `ExerciseDto` 만 keep 하고 래퍼 `ExerciseListResponse`/`PageMeta` 를 빠뜨려 릴리스에서 운동 계획이 통째로 빈 채 생성·저장·고착된 사고(2026-06-15). 새 Gson 모델은 keep 된 패키지(`data.remote.exercisedb.**`, `data.remote.api.dto.**`, `api.generated.model.**`) 안에 두면 자동 보호.
- **자동 가드**: `app/src/test/.../ProguardKeepRulesTest` 가 위 3개 keep 규칙의 존재를 박제(삭제 시 `:app:testDebugUnitTest` 실패). 새 Gson 모델 패키지 추가 시 `proguard-rules.pro` keep + 이 테스트 목록을 함께 갱신.
- 검증은 단위테스트 불가 → **릴리스 빌드 실기기/계측**으로만 확인.

### Destructive 명령 실행 직전 5문항 (`monitoring-and-cost.md §6.8`)
1. 대상이 운영 리소스(RG `apps`, `eundunhealthacr`, `healthapp` PG)인가?
2. `--yes`/`--no-confirm` 플래그가 무엇을 묵시적으로 동의하는가?
3. 연쇄 영향(manifest 공유, secretref, firewall rule)은?
4. 롤백 경로(이미지 캐시, git 백업, DB PITR)는?
5. 실패 시 Sentry/Health Check로 즉시 인지 가능한가?

### 병렬 작업 규칙

- **같은 루트 디렉토리에서는 Claude Code 콘솔 1개만 실행**
- 병렬 작업이 필요한 경우 `git worktree`로 디렉토리를 분리한 뒤 각 디렉토리에서 콘솔 1개씩 실행
```bash
  git worktree add ../eundunHealth-feat-A feature/작업명A
  git worktree add ../eundunHealth-feat-B feature/작업명B
  # 각 디렉토리에서 별도 claudex 실행
```
- 각 세션 시작 시 **수정 대상 파일/디렉토리를 프롬프트에 명시** (영역 외 파일 수정 금지)
- 작업 완료 후 worktree 정리: `git worktree remove ../eundunHealth-feat-A`

### Commit / Push 워크플로우
- main 직접 작업 시: 모든 부수 변경 (changelog, docs 갱신) 을 포함한 뒤 **1회 push**.
- `/changelog` 는 push 전에 실행 → `git commit --amend --no-edit` 로 직전 커밋에 합침.
- 이미 push 한 뒤 changelog 를 뒤늦게 추가하면 별도 커밋이 된다 (main force push 금지).
- Fallback: 여러 커밋이 쌓였으면 push 전 `git rebase -i HEAD~N` 으로 squash 가능.
- PR 워크플로우 (feature branch) 에서는 GitHub squash merge 로 자연 해결.

## PowerShell / Windows 11 환경 빠른 참조

이 저장소의 개발 호스트는 Windows 11 Pro + **PowerShell 7.6 LTS** (`pwsh.exe`, .NET 10). `~/.claude/settings.json`에 `CLAUDE_CODE_USE_POWERSHELL_TOOL=1`, `defaultShell=powershell`, `CLAUDE_BASH_MAINTAIN_PROJECT_WORKING_DIR=1`이 등록되어 있어 Claude는 **PowerShell tool**을 primary shell로 사용하고 input-box `!`도 PowerShell로 라우팅된다. `defaultShell=powershell`이면 Claude Code가 `pwsh.exe` (7.x)를 자동 감지하여 사용한다. Bash tool은 POSIX 스크립트(`scripts/*.sh`)용으로 보조.

> **실행 파일 구분**: `pwsh.exe` = PowerShell 7.x (Core), `powershell.exe` = 5.1 (Desktop, side-by-side 설치됨). 본 프로젝트는 `pwsh` 전용.

**인코딩**: `$OutputEncoding` = UTF-8 NoBOM (7.x 기본). 5.1은 US-ASCII 였으므로 한국어 파이프라인 출력이 깨질 수 있었으나 7.x에서 해소.

**에러 표시**: `$ErrorView = 'ConciseView'` (7.x 기본) — 짧은 에러 메시지. 풀 stacktrace는 `Get-Error`.

**Profile 경로**: `~\Documents\PowerShell\` (7.x) vs `~\Documents\WindowsPowerShell\` (5.1). 현재 7.x 프로파일 미생성 (기본값 사용).

**README/runbook의 bash 1-liner를 PowerShell로 옮길 때 자주 어긋나는 곳:**
| Bash | PowerShell 7 |
|---|---|
| `cmd1 && cmd2` | `cmd1 && cmd2` (pwsh 7+ 네이티브 지원) |
| `cmd > /dev/null 2>&1` | `cmd *> $null` |
| `cmd1 \| head -20` | `cmd1 \| Select-Object -First 20` |
| `cat x.json \| jq .` | `Get-Content x.json -Raw \| ConvertFrom-Json` (또는 `jq`도 그대로 사용) |
| `VAR=x cmd` | `$env:VAR='x'; cmd` (inline prefix 없음) |
| `find . -name "*.kt"` | Glob tool (NOT `Get-ChildItem -Recurse`) |
| `grep -r foo .` | Grep tool (NOT `Select-String -Recurse`) |
| `rm -rf path` | `Remove-Item -Recurse -Force path` (ACR 정리는 **룰 1 — untag**) |

**7.x 신규 연산자 (5.1에 없음):**
| 구문 | 예시 |
|---|---|
| 삼항 연산자 | `$count -gt 0 ? 'yes' : 'no'` |
| Null-coalescing `??` | `$env:PORT ?? '8080'` |
| Null-coalescing 대입 `??=` | `$x ??= 'default'` |
| Null-conditional `?.` | `${obj}?.Property` (Bash 경유 시 변수 확장 문제 — pwsh 직접 사용) |
| `ForEach-Object -Parallel` | `1..10 \| ForEach-Object -Parallel { $_ * 2 } -ThrottleLimit 4` |

**자주 깨지는 syntax:**
- here-string 닫는 `'@`는 **column 0 (들여쓰기 0)** 이어야 함. 안 그러면 parse error.
- `-ErrorAction SilentlyContinue`는 출력만 죽이고 exit code는 1. 진짜 무시하려면 `try { Cmdlet ... -ErrorAction Stop } catch {}`.
- `$PSVersionTable.PSVersion` 같은 변수 표현은 Bash tool로 보내면 `.PSVersion...`으로 잘려 파싱 에러 — 반드시 PowerShell tool 사용.
- `Get-WmiObject` 등 WMI cmdlet은 7.x에서 **제거**됨 → `Get-CimInstance` 등 CIM cmdlet 사용.

**권한 동작 (`~/.claude/settings.json` 기준):**
- `Get-*`, `Test-Path`, `Select-String`, `git status/log/diff`, `gh pr view/list`, `docker ps`, `az containerapp show/logs`, `./gradlew *`, `adb devices`는 prompt 없이 통과.
- `Remove-Item`, `Set-Content`, `Stop-Process`, `git push`, `gh secret set`, `docker push`, `az containerapp delete/update`, `az acr repository untag/delete`는 ask로 막혀 매번 확인.
- `Format-Volume`, `Clear-Disk`, `Stop-Computer`, `Restart-Computer`, `git push --force origin main/master`는 deny — 우회 불가.

## Documentation

- `@docs/CHANGELOG.md` — 버전 이력 (v0.1.0 통합)
- `@docs/PRD.md` — Product Requirements
- `@docs/TRD.md` — Technical Requirements
- `@docs/SPEC.md` — 기능 명세
- `@docs/store/privacy-policy.md` — 개인정보 처리방침 (Play Store URL 호스팅 대상)
- `@docs/store/account-deletion.md` — 계정 및 데이터 삭제 안내 (Play Store 계정 삭제 요청 URL 호스팅 대상)
- `@docs/plans/README.md` — design+plan 페어 인덱스 (frontmatter 기반 자동 생성, status/PR/인시던트 추적 컬럼 포함)
- `@docs/ops/operations-snapshot.md` — **현재 운영 상태 단일 출처**
- `@docs/ops/incident-log.md` — 인시던트 이력 + root cause + 재발 방지 패턴
- `@docs/ops/migration-runbook.md` — Ktor → FastAPI 마이그레이션 절차 + 사후 정리
- `@docs/ops/monitoring-and-cost.md` — Sentry/ACR/Budget + §6 Destructive 명령 안전 패턴
- `@docs/ops/azure-container-apps-jobs.md` — Container Apps **Job**(private ACR + KV + MI) 프로비저닝 재현 패턴 + 함정 회피(E1~E4: az `--args` leading-dash / system MI chicken-egg → UAI-first / 개인 MSA RBAC CLI 불가 → 포털·SP / job `--registry-identity` CLI 버그 → `--yaml`). orphan reaper 워크드 예시
- `@docs/ops/play-store-release.md` — 첫 출시 8단계 + 데이터 안전 답변
- `@docs/ops/dependency-deferred.md` — v0.1.0 출시 후 재검토할 의존성 보류 항목. 2026-05-29 starlette 1.1.0 (#54) + healthConnect 1.1.0 stable (#53) 해소. 남은 항목: kotlin 2.4 (Hilt 2.59.3+ 출시 대기 + build.gradle.kts DSL 마이그레이션 필요)
- `@docs/ops/containerapp-env-ktor-backup.json` — cutover 직전 env 스냅샷 (historical)
- `@docs/conventions/naming.md` — 명명/문서화 SSoT (5종 공식 가이드 + 본 프로젝트 결정 D1~D10)
- `@docs/conventions/versioning.md` — 버전 관리 SSoT (앱 `version.properties` + 백엔드 `__version__` 독립 / semver 정책 / versionCode 규칙 / bump 절차 / 프론트 표시 / drift 방지)

### 자동화 스크립트 (`scripts/`)
> `.sh` 스크립트는 CI (GitHub Actions) 호환성 위해 bash 유지. 로컬에서는 Git Bash로 실행.

- `scripts/preflight-release.sh` — Spotless + Detekt + Tests + releaseArtifacts 일괄 (INC-04 방지). 첫 gradle 호출 전 `ensure-java.sh` 로 JDK 보장(JAVA_HOME 미설정 셸 자가치유).
- `scripts/ensure-java.sh` — `./gradlew` 용 JDK 자가탐지(JDK 17 우선). 시스템 JAVA_HOME 이 있어도 stale 터미널이 못 상속하는 함정 대응(2026-06-19). preflight 가 source. 상세는 스크립트 헤더 주석.
- `scripts/bump-version.sh` — 앱 버전 bump 단일 진입점. `version.properties` 의 versionName 갱신 + versionCode +1 + semver/단조 가드 + current-state 문서(README/PRD/operations-snapshot) 동기화. `--dry-run` 지원. 정책: `docs/conventions/versioning.md`
- `scripts/alembic-autogen.sh` — postgres:16-alpine 컨테이너 기반 autogenerate (INC-07 방지)
- `scripts/sync-openapi.sh` — FastAPI 스펙을 `backend/openapi.json`으로 추출. 라우터/스키마 변경 시 필수 실행 + 같은 PR에 커밋. backend.yml의 drift detection step이 미커밋을 fast-fail로 차단.
- `scripts/gen-plans-index.sh` (+ `gen_plans_index.py`) — `docs/plans/*.md` frontmatter 기반 `docs/plans/README.md` 자동 생성. **root-only scan** (`glob`, not `rglob`) — 하위 디렉토리(`_staging/`, `logs/` 등) 무시. status: `proposed`/`approved`/`in-progress`/`holding`/`deferred`/`shipped`/`superseded`/`abandoned`. active 섹션은 status 그룹별 하위 섹션 (진행 중/대기/보류) 렌더링. `docs/plans/_staging/` 은 gitignored scratch 폴더. pre-commit hook 자동 호출 + 별도 CI workflow (`docs-plans-index.yml`) 가 drift 차단. **D5**: missing frontmatter 는 silent skip (점진 도입 + 다중 PR coordination 안전), malformed 만 fail.
- `scripts/setup-azure-alerts.sh` — Azure Monitor alert 8개 idempotent 프로비저닝 (`--dry-run`, `--delete`). Action Group + Activity Log 4 + Metric 4. 설계: `docs/plans/2026-06-03-azure-monitor-alerts-design.md`
- `scripts/setup-sentry-alerts.ps1` — Sentry Issue/Metric Alert 8개 idempotent 프로비저닝 (`-DryRun`). Issue Alert 6(Android·Backend 신규·회귀·빈도급증) + Metric Alert 2(Backend p95·에러율). B1~B5 재발방지 주석(PS case-insensitive 변수충돌·environment 404·interval 무효·dataset deprecated·team targetType) 포함.
- `scripts/register-azure-credentials.ps1` — SP 생성/패치 + AcrPush + GitHub secret 등록 (INC-17, 운영자 1회/만료 갱신)
- `scripts/warm-gradle.sh` — Gradle 데몬 사전 구동
- `scripts/claude-context.sh` / `claude-precompact.sh` — SessionStart/PreCompact 훅
- `scripts/hooks/secretref-guard.sh` — git commit 시 backend.yml 신규 secretref 가 Container
  App 에 등록됐는지 자동 검증 (룰 6 1차 가드). PreToolUse hook 으로 자동 실행. fail-open.

### 에이전트 스크립트 (`scripts/agents/`) — **WIP, 삭제 금지**
> Claude Agent SDK 기반 헤드리스 자동화. errant artifact 로 오해해 삭제했다 복구한 이력 있음. 상세: `docs/plans/logs/process-infra.md` (2026-06-16 Agent SDK 적용 검토 entry).

- `scripts/agents/doc_audit.py` — 문서 드리프트 감사관. 2단계: (1) collector — `version.properties`·`backend/app/__init__.py`·alembic head·pytest 수 등 canonical SSoT 파싱(결정론적, SDK 불필요); (2) auditor — Claude Agent SDK로 문서 사본(CLAUDE.md/README/operations-snapshot/TRD/PRD/CHANGELOG)과 대조(read-only, `dontAsk`+`[Read,Grep,Glob]` → 파일 변경 불가). 설계 배경: INC-27(`bump-version.sh` blind-replace).
- `scripts/agents/doc-audit.sh` — 로컬 wrapper (구독 인증 + 출력 포맷).
- `scripts/agents/test_doc_audit.py` — collector/파서 단위테스트 (SDK·인증 불필요).
- `scripts/agents/requirements.txt` — `claude-agent-sdk` (Python 3.10+).

```bash
# 결정론적 사실 수집만 (SDK·인증 불필요)
python scripts/agents/doc_audit.py --collect-only

# 전체 감사 (collector → SDK auditor → 사람용 리포트)
bash scripts/agents/doc-audit.sh

# 단위 테스트
backend/.venv/Scripts/python.exe -m pytest scripts/agents/test_doc_audit.py -q
```

**CI**: `.github/workflows/doc-audit.yml` — 2 job. `collector-test`(시크릿 불필요·항상 실행) + `audit`(주간 cron 월 09:00 KST + `workflow_dispatch`, `CLAUDE_CODE_OAUTH_TOKEN` 또는 `ANTHROPIC_API_KEY` 미설정 시 GREEN 스킵). advisory — `--strict` 미지정이라 드리프트가 있어도 run 은 green(Step Summary + artifact 로만 리포트).

### Claude Code 슬래시 명령 (`.claude/commands/`)

- `/verify-deploy <inc-id>` — MCP (Sentry/Azure) 로 Phase 5 운영 검증 자동화 (alembic head
  + 스키마 컬럼 + Sentry 신규 issue). INC 별 검증 1-command. 자세한 내용:
  `docs/plans/2026-05-28-mcp-integration-setup-design.md` §3.3.
- `/naming-audit` — 명명/문서화 컨벤션 drift 점검 (ruff D + detekt naming + Azure CAF 표 sync).
  자세한 룰: `docs/conventions/naming.md`. 결정 기록:
  `docs/plans/2026-06-02-naming-convention-audit-design.md`. 분기당 1~2회 권장.

### OpenAPI Generator (Android)
- 입력: `backend/openapi.json` (git checked-in, `sync-openapi.sh`로 갱신)
- 출력: `app/build/generated/openapi/src/main/kotlin/com/gunnys/eundunhealth/api/generated/` (gitignored, `:app:openApiGenerate`로 자동 생성, `preBuild` 의존성으로 컴파일 전 항상 최신)
- Repository는 generated client 사용 (`EundunApi.kt`·`ApiDtos.kt` 제거됨). `di/NetworkModule.kt` 에 5개 generated Api provider, `data/repository/*RepositoryImpl.kt` 에서 `bodyOrThrow()` 사용.
- 라우터에 추가/변경 시 체크리스트: ① 라우터에 `operation_id="..."` 명시(Android 함수명과 일치) ② Query param은 `alias="..."`로 camelCase 노출 ③ `bash scripts/sync-openapi.sh` ④ 같은 PR에 `backend/openapi.json` 포함
