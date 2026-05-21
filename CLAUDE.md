# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

eundunHealth(은둔헬스) is a Korean health/fitness Android app with a Ktor backend. Users input body metrics, receive auto-generated weekly workout plans from ExerciseDB, track completion via Health Connect, and earn badges. All UI text is Korean.

## Build & Run Commands

### Android App (root project)
```bash
./gradlew clean assembleDebug          # Debug build
./gradlew clean assembleRelease        # Release build (ProGuard enabled)
./gradlew :app:testDebugUnitTest       # Run all unit tests
./gradlew :app:testDebugUnitTest --tests "com.gunnys.eundunhealth.domain.usecase.SyncHealthDataUseCaseTest"  # Single test class
```

### Backend (separate Gradle project in `backend/`)
```bash
cd backend
./gradlew run                          # Run dev server (port 8080)
./gradlew shadowJar                    # Build fat JAR
./gradlew test                         # Run backend tests
```

### Deployment (Docker → Azure Container Apps)
```bash
# Redeploy script at C:/programming/docker/eundunhealth-api/
bash C:/programming/docker/eundunhealth-api/redeploy.sh
```
Builds shadowJar → Docker image → pushes to ACR `eundunhealthacr` → updates container app `eundunhealth-api`.

### Device Testing
```bash
# Install directly via adb (bypasses Android Studio 16KB alignment warning)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Architecture

### Multi-Project Structure
- **Root project** includes only `:app` (Android). Backend is a **separate** Gradle project under `backend/` with its own `build.gradle.kts` and `settings.gradle.kts`.
- Dependency versions centralized in `gradle/libs.versions.toml`.
- Build secrets (Supabase URL/key, ExerciseDB key, backend URL, Sentry DSN) loaded from `local.properties` into BuildConfig fields.

### Android App (`app/`)
Package: `com.gunnys.eundunhealth`

**Clean Architecture layers:**
- **UI** (`ui/`): Compose screens + ViewModels. Navigation via sealed `Screen` class in `navigation/`. Screens: splash, auth (login/signup), onboarding, home, profile, workout detail, history, badges.
- **Domain** (`domain/`): Models, repository interfaces, use cases.
- **Data** (`data/`): Repository implementations, Retrofit API (`remote/api/EundunApi.kt`), Room database (`local/`), Health Connect datasource, Supabase auth (`auth/AuthRepositoryImpl.kt`), DataStore preferences.
- **DI** (`di/`): Hilt modules — `NetworkModule`, `SupabaseModule`, `DatabaseModule`, `RepositoryModule`, `CoilModule`.

**Key patterns:**
- ViewModels get userId via `AuthRepository.getCurrentUserId()` — never inject `SupabaseClient` directly into ViewModels.
- Token management via `AtomicReference` in `NetworkModule`, refreshed by `TokenAuthenticator` on 401.
- `RetryInterceptor` does exponential backoff (3 retries, 500ms/1s/2s).
- Auth errors mapped to Korean user-friendly messages in `AuthRepositoryImpl.mapAuthError()`.
- Shared UI components in `ui/components/` (e.g., `ProfileSummaryCard`, `ProfileSlider`, `SkeletonUi`).
- `SentryInitProvider` disabled in AndroidManifest (`tools:node="remove"`) — Sentry initialized manually in `EundunHealthApplication` with DSN blank check.

### Backend (`backend/`)
Package: `com.gunnys.eundunhealth`

- `Application.kt` — Entry point, initializes Sentry + database + plugins.
- `plugins/Security.kt` — JWKS-based JWT verification (Supabase uses ES256, not HMAC256). Keys fetched from `{supabaseUrl}/auth/v1/.well-known/jwks.json` with 24h cache.
- `config/AppConfig.kt` — Central config, reads env vars with dotenv fallback.
- `db/DatabaseFactory.kt` — HikariCP pool → Azure PostgreSQL, Exposed ORM.
- `plugins/Routing.kt` — CORS, StatusPages, health check, authenticated route groups.

**API Endpoints (all except /health require JWT):**
```
GET    /health
GET    /profile
PUT    /profile
GET    /weekly-plan?weekStart=
POST   /weekly-plan
PATCH  /weekly-plan/complete
GET    /weekly-plan/history?page=&size=
GET    /badges
POST   /badges/{key}
```

## Key Technical Details

- **Kotlin** throughout (Android + Backend)
- **Gradle 9.4.1**, AGP 9.2.1, Kotlin 2.2.10 (app) / 2.3.0 (backend)
- **Min SDK 26**, Target SDK 37, Java 17
- **Sentry 8.16.0** (Android) — requires 16KB page-aligned native libs; `packaging.jniLibs.useLegacyPackaging = false` in build.gradle.kts
- Supabase JWT algorithm is **ES256** (ECDSA), not HMAC256 — backend uses JWKS public key verification
- Network security config disables cleartext except localhost/10.0.2.2 in debug
- Korean timezone (KST) is the user-facing standard for dates

## Documentation

- `docs/SPEC.md` — Full feature specification
- `docs/CHANGELOG.md` — Version history and work log
