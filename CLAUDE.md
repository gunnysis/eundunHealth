# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

eundunHealth(은둔헬스) is a Korean health/fitness Android app with a **FastAPI (Python 3.12)** backend. Users input body metrics, receive auto-generated weekly workout plans from the **OSS ExerciseDB** (`oss.exercisedb.dev`, no auth), track completion via Health Connect, set goals (weight / body fat) and earn badges. All UI text is Korean.

**Current state**: versionName `0.1.0` (versionCode `13`). v0.1·v0.2·v0.3 spec all implemented. Production cutover from Ktor → FastAPI completed. Ready for Play Store **Internal Testing** track. Detailed runtime snapshot: `docs/ops/operations-snapshot.md`.

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

# Alembic 마이그레이션 (현재 head: 24d0fe2eb397)
.venv/Scripts/alembic upgrade head
.venv/Scripts/alembic revision --autogenerate -m "..."
```

### Deployment (Docker → Azure Container Apps)
```bash
# Redeploy script at C:/programming/docker/eundunhealth-api/
bash C:/programming/docker/eundunhealth-api/redeploy.sh [tag]
```
FastAPI uvicorn 이미지 빌드 → ACR `eundunhealthacr` → Container App `eundunhealth-api` (RG `apps`, Korea Central) 업데이트 → /health 헬스체크 → timestamp 태그 자동 정리(최근 5개만 보존). 환경변수 변경은 별도 `az containerapp update --set-env-vars` 또는 `secret set`. 자세한 절차는 `docs/ops/migration-runbook.md`.

Docker development location: `C:\programming\docker\eundunhealth-api`

### Device Testing (Android)
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
# 또는 release APK
adb install -r app/build/outputs/apk/release/app-release.apk
```

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
- ViewModel은 `AuthRepository.getCurrentUserId()`로 userId를 받는다 — `SupabaseClient` 직접 주입 금지.
- 모든 ViewModel: `_error: MutableStateFlow<AppError?>` + `clearError()` 통일. `runCatching { ... }.onFailure { e -> val a = e.toAppError(); a.reportToSentry(); _error.value = a }`.
- Token: `NetworkModule`의 `AtomicReference`, `TokenAuthenticator`가 401 시 5초 timeout으로 갱신 + 실패 시 무효화.
- `RetryInterceptor` 지수 백오프 (3회 / 500ms·1s·2s).
- Auth 에러는 `AuthRepositoryImpl.mapAuthError()`로 한국어 사용자 메시지.
- 공통 UI: `ui/components/` (`ProfileSummaryCard`, `ProfileSlider`, `SkeletonUi`, `ErrorContent`, `EmptyContent`).
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
- `alembic/` — async 엔진 연동. **head: `24d0fe2eb397` (v0.3 history + goals)**.

**API Endpoints (12개 — `/health` 제외 모두 JWT 필요):**
```
GET    /health
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
- **Gradle 9.4.1**, AGP 9.2.1
- **Min SDK 26**, Target SDK 37, Java 17
- **App version**: versionName **`0.1.0`**, versionCode **`13`** (다음 빌드부터 14, 15, ...)
- **Sentry Android 8.16.0** (eundunhealth 프로젝트) — 16KB page-aligned native libs; `packaging.jniLibs.useLegacyPackaging = false`
- **Vico 2.1.0** (compose-m3) — 통계 + 목표 진행 차트
- **Detekt 1.23.7 + Spotless 7.0.4 + ktlint 1.5.0**
- Supabase JWT algorithm: **ES256 (ECDSA)** — backend uses JWKS public key verification
- Network security config disables cleartext except localhost/10.0.2.2 in debug
- 시간대: 한국(KST)
- pre-commit hook (`.githooks/pre-commit`)이 .kt 변경 시 spotlessApply + detektDebug 자동 실행

### Backend (FastAPI)
- **Python 3.12**, FastAPI 0.136.3, SQLAlchemy 2.0 async + asyncpg, Alembic 1.14
- **starlette 0.49.1** (CVE 패치 위해 명시 pin), PyJWT 2.12 (JWKS), httpx (Supabase Admin API)
- **Sentry SDK 2.19** (eundunhealth-backend 프로젝트) — DSN secretref `sentry-dsn-backend`
- mypy strict 통과, ruff/bandit clean, pytest 41/41 PASS, coverage 82%
- Alembic head `24d0fe2eb397`

### Infrastructure
- **Container App** `eundunhealth-api` (RG `apps`, Korea Central, Min replicas 0 → ScaledToZero)
- **ACR** `eundunhealthacr` (Basic SKU — retention 정책 미지원, redeploy.sh가 timestamp 태그 최근 5개만 보존)
- **Azure PostgreSQL** Flexible Server `healthapp` (B1ms, 32GB, Korea Central). Firewall 기본 차단 + Container App IP만 허용 + `allow-azure-services`
- **Supabase** Korea 리전, project `ttzzbfoksncqazvcsfiu`
- **Sentry**: Android `eundunhealth`, Backend `eundunhealth-backend` (각 별도 project)
- CI: GitHub Actions (`backend.yml` + `android.yml`) + Dependabot

## Documentation

- `@docs/CHANGELOG.md` — 버전 이력 (v0.1.0 통합)
- `@docs/PRD.md` — Product Requirements
- `@docs/TRD.md` — Technical Requirements
- `@docs/SPEC.md` — 기능 명세
- `@docs/privacy-policy.md` — 개인정보 처리방침 (Play Store URL 호스팅 대상)
- `@docs/ops/operations-snapshot.md` — **현재 운영 상태 단일 출처**
- `@docs/ops/migration-runbook.md` — Ktor → FastAPI 마이그레이션 절차 + 사후 정리
- `@docs/ops/monitoring-and-cost.md` — Sentry/ACR/Budget 운영 가이드
- `@docs/ops/play-store-release.md` — 첫 출시 8단계 + 데이터 안전 답변
- `@docs/ops/containerapp-env-ktor-backup.json` — cutover 직전 env 스냅샷 (historical)
