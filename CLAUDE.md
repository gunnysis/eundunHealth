# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

eundunHealth(은둔헬스) is a Korean health/fitness Android app with a **FastAPI (Python)** backend. Users input body metrics, receive auto-generated weekly workout plans from ExerciseDB, track completion via Health Connect, and earn badges. All UI text is Korean.

> 레거시 Ktor 백엔드 소스는 `D:\backup\dev\project\eundunHealth\`로 이동·보관됨. ACR의 `ktor-final` 이미지 태그도 함께 보존되어 있어 인프라 롤백은 여전히 가능 (`docs/ops/migration-runbook.md` §5 참조).

## Build & Run Commands

### Android App (root project)
```bash
./gradlew clean assembleDebug          # Debug build
./gradlew clean assembleRelease        # Release build (R8 enabled with ProGuard rules)
./gradlew :app:testDebugUnitTest       # Run all unit tests
./gradlew :app:testDebugUnitTest --tests "com.gunnys.eundunhealth.domain.usecase.SyncHealthDataUseCaseTest"  # Single test class

# 코드 품질 (Phase 4 v0.2 도입)
./gradlew :app:spotlessApply           # 자동 포맷 (ktlint)
./gradlew :app:spotlessCheck           # 포맷 검증 (CI에서 사용)
./gradlew :app:detektDebug             # 정적 분석
./gradlew :app:detektBaselineDebug     # 기존 위반을 baseline-debug.xml에 박제 (신규만 차단)

# git pre-commit hook 활성화 (clone 직후 1회)
git config core.hooksPath .githooks
```

### Backend (FastAPI, in `backend/`)
```bash
cd backend
# 가상환경 + 의존성 설치 (1회)
python -m venv .venv
.venv/Scripts/pip install -r requirements-dev.txt

# 로컬 실행 (Docker Compose 권장)
docker compose up -d                  # PostgreSQL + uvicorn 동시 기동
docker compose logs -f api

# 또는 호스트 직접 실행 (DB는 별도 기동 필요)
.venv/Scripts/uvicorn app.main:app --reload --port 8080

# 테스트
.venv/Scripts/pytest tests/ -v --cov=app

# 정적 검사
.venv/Scripts/ruff check app/ tests/
.venv/Scripts/mypy app/

# 보안 스캔
.venv/Scripts/bandit -r app -ll
.venv/Scripts/pip-audit -r requirements.txt --strict --ignore-vuln PYSEC-2026-161

# Alembic 마이그레이션
.venv/Scripts/alembic upgrade head
.venv/Scripts/alembic revision --autogenerate -m "..."
```

### Deployment (Docker → Azure Container Apps)
```bash
# Redeploy script at C:/programming/docker/eundunhealth-api/
bash C:/programming/docker/eundunhealth-api/redeploy.sh [tag]
```
FastAPI uvicorn 이미지를 빌드 → ACR `eundunhealthacr` → Container App `eundunhealth-api` (RG `apps`) 업데이트.
환경변수/마이그레이션 절차는 `docs/ops/migration-runbook.md` 참조.

Docker development location: `C:\programming\docker\eundunhealth-api`

### Device Testing (Android)
```bash
# Install directly via adb (bypasses Android Studio 16KB alignment warning)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Architecture

### Multi-Project Structure
- **Root project** includes only `:app` (Android). Backend는 별도 디렉토리 `backend/`에 FastAPI(Python) 프로젝트로 분리.
- Dependency versions centralized in `gradle/libs.versions.toml` (Android only).
- Build secrets (Supabase URL/key, ExerciseDB key, backend URL, Sentry DSN) loaded from `local.properties` into BuildConfig fields.
- Release signing credentials should also be in `local.properties` or environment variables — never hardcode in `build.gradle.kts`.

### Android App (`app/`)
Package: `com.gunnys.eundunhealth`

**Clean Architecture layers:**
- **UI** (`ui/`): Compose screens + ViewModels. Navigation via sealed `Screen` class in `navigation/`. Screens: splash, auth (login/signup/forgot-password), onboarding, home, profile, workout detail, history, badges.
- **Domain** (`domain/`): Models, repository interfaces, use cases. `domain/model/AppError.kt`에 통일 에러 sealed class + `Throwable.toAppError()` + `AppError.reportToSentry()`.
- **Data** (`data/`): Repository implementations, Retrofit API (`remote/api/EundunApi.kt`), Room database (`local/`), Health Connect datasource, Supabase auth (`auth/AuthRepositoryImpl.kt`), DataStore preferences.
- **DI** (`di/`): Hilt modules — `NetworkModule`, `SupabaseModule`, `DatabaseModule`, `RepositoryModule`, `CoilModule`.

**Key patterns:**
- ViewModels get userId via `AuthRepository.getCurrentUserId()` — never inject `SupabaseClient` directly into ViewModels.
- 모든 ViewModel은 `_error: MutableStateFlow<AppError?>` + `clearError()` 통일 패턴. `runCatching { ... }.onFailure { e -> e.toAppError().also { it.reportToSentry(); _error.value = it } }`.
- Token management via `AtomicReference` in `NetworkModule`, refreshed by `TokenAuthenticator` on 401.
- `RetryInterceptor` does exponential backoff (3 retries, 500ms/1s/2s).
- Auth errors mapped to Korean user-friendly messages in `AuthRepositoryImpl.mapAuthError()`.
- Shared UI components in `ui/components/` (`ProfileSummaryCard`, `ProfileSlider`, `SkeletonUi`, `ErrorContent`, `EmptyContent`).
- `SentryInitProvider` disabled in AndroidManifest (`tools:node="remove"`) — Sentry initialized manually in `EundunHealthApplication` with DSN blank check.

### Backend (`backend/` — FastAPI / Python 3.12)
Package: `app`

- `app/main.py` — FastAPI app + lifespan(DB 엔진/Sentry/CORS) + 글로벌 exception handler.
- `app/config.py` — pydantic-settings, `get_settings()` 의존성 함수.
- `app/database.py` — `Base = DeclarativeBase`, `get_db()` UoW 패턴 (Request 통해 app.state.session_factory).
- `app/dependencies.py` — JWKS 기반 JWT 검증 (`PyJWKClient` 24h TTL 캐시, ES256).
- `app/exceptions.py` — `AppException`/`NotFoundException`/`ConflictException`/`BadRequestException`.
- `app/models/` — SQLAlchemy 2.0 `Mapped[T] = mapped_column(...)` 스타일.
- `app/schemas/` — Pydantic `CamelSchema` 베이스 (alias_generator=to_camel, populate_by_name=True).
- `app/repositories/` — DB 접근 추상화.
- `app/services/` — 비즈니스 로직 (`account_service`가 Supabase Admin API로 Auth 사용자 삭제).
- `app/routers/` — 얇은 라우터, Service에 위임.
- `alembic/` — async 엔진 연동, 프로덕션은 `stamp head`로 초기화 (기존 테이블 보존).

**API Endpoints (all except /health require JWT):**
```
GET    /health
GET    /profile
PUT    /profile
GET    /weekly-plan?week_start=
POST   /weekly-plan
PATCH  /weekly-plan/complete
GET    /weekly-plan/history?page=&size=
GET    /badges
POST   /badges/{key}
DELETE /account
```

## Key Technical Details

### Android App
- **Kotlin 2.2.10**, KSP 2.3.2 (KSP 버전은 Kotlin과 호환 필요 — 불일치 시 빌드 실패 주의)
- **Gradle 9.4.1**, AGP 9.2.1
- **Min SDK 26**, Target SDK 37, Java 17
- **App version**: versionName `0.0.4`, versionCode `12`
- **Sentry 8.16.0** — requires 16KB page-aligned native libs; `packaging.jniLibs.useLegacyPackaging = false` in build.gradle.kts
- Supabase JWT algorithm is **ES256** (ECDSA), not HMAC256 — backend uses JWKS public key verification
- Network security config disables cleartext except localhost/10.0.2.2 in debug
- Korean timezone (KST) is the user-facing standard for dates

### Backend (FastAPI)
- **Python 3.12**, FastAPI 0.136.3, SQLAlchemy 2.0 async + asyncpg, Alembic 1.14
- **starlette 0.49.1** (CVE 패치 위해 명시 pin), PyJWT 2.12 (JWKS), httpx (Supabase Admin API)
- **Sentry SDK 2.19** (FastAPI integration)
- mypy strict 통과, ruff/bandit clean, pytest 20/20 PASS, coverage 82%

## Documentation

- `@docs/SPEC.md` — Full feature specification
- `@docs/CHANGELOG.md` — Version history and work log
- `@docs/PRD.md` — Product Requirements Document
- `@docs/TRD.md` — Technical Requirements Document
- `@docs/constitution.md` — Constitution
- `@docs/ops/migration-runbook.md` — Ktor → FastAPI 마이그레이션 절차
- `@docs/ops/monitoring-and-cost.md` — Sentry/ACR/Budget 운영 가이드
