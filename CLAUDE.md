# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

eundunHealth(은둔헬스) is a Korean health/fitness Android app with a **FastAPI (Python 3.12)** backend. Users input body metrics, receive auto-generated weekly workout plans from the **OSS ExerciseDB** (`oss.exercisedb.dev`, no auth), track completion via Health Connect, set goals (weight / body fat) and earn badges. All UI text is Korean.

**Current state**: versionName `0.1.0` (versionCode `14` — 13은 첫 시도, 14는 출시 직전 안정화 후 재빌드). v0.1·v0.2·v0.3 spec all implemented. Production cutover from Ktor → FastAPI completed. Ready for Play Store **Internal Testing** track. Detailed runtime snapshot: `docs/ops/operations-snapshot.md`.

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
- **App version**: versionName **`0.1.0`**, versionCode **`14`** (13은 첫 internal testing 시도, 14는 출시 직전 안정화 후 재빌드. 다음 빌드부터 15, 16, ...)
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
./gradlew :app:releaseArtifacts        # AAB + APK 동시
# 또는 (모든 게이트 + 빌드를 한 번에)
bash scripts/preflight-release.sh
```

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

### Destructive 명령 실행 직전 5문항 (`monitoring-and-cost.md §6.8`)
1. 대상이 운영 리소스(RG `apps`, `eundunhealthacr`, `healthapp` PG)인가?
2. `--yes`/`--no-confirm` 플래그가 무엇을 묵시적으로 동의하는가?
3. 연쇄 영향(manifest 공유, secretref, firewall rule)은?
4. 롤백 경로(이미지 캐시, git 백업, DB PITR)는?
5. 실패 시 Sentry/Health Check로 즉시 인지 가능한가?

## PowerShell / Windows 11 환경 빠른 참조

이 저장소의 개발 호스트는 Windows 11 Pro + PowerShell 7(`pwsh`). `~/.claude/settings.json`에 `CLAUDE_CODE_USE_POWERSHELL_TOOL=1`, `defaultShell=powershell`, `CLAUDE_BASH_MAINTAIN_PROJECT_WORKING_DIR=1`이 등록되어 있어 Claude는 **PowerShell tool**을 primary shell로 사용하고 input-box `!`도 PowerShell로 라우팅된다. Bash tool은 POSIX 스크립트(`scripts/*.sh`)용으로 보조.

**README/runbook의 bash 1-liner를 PowerShell로 옮길 때 자주 어긋나는 곳:**
| Bash | PowerShell 7 |
|---|---|
| `cmd1 && cmd2` | `cmd1 && cmd2` (pwsh 7+는 그대로 OK, 5.1 X) |
| `cmd > /dev/null 2>&1` | `cmd *> $null` |
| `cmd1 \| head -20` | `cmd1 \| Select-Object -First 20` |
| `cat x.json \| jq .` | `Get-Content x.json -Raw \| ConvertFrom-Json` (또는 `jq`도 그대로 사용) |
| `VAR=x cmd` | `$env:VAR='x'; cmd` (inline prefix 없음) |
| `find . -name "*.kt"` | Glob tool (NOT `Get-ChildItem -Recurse`) |
| `grep -r foo .` | Grep tool (NOT `Select-String -Recurse`) |
| `rm -rf path` | `Remove-Item -Recurse -Force path` (ACR 정리는 **룰 1 — untag**) |

**자주 깨지는 syntax 3개:**
- here-string 닫는 `'@`는 **column 0 (들여쓰기 0)** 이어야 함. 안 그러면 parse error.
- `-ErrorAction SilentlyContinue`는 출력만 죽이고 exit code는 1. 진짜 무시하려면 `try { Cmdlet ... -ErrorAction Stop } catch {}`.
- `$PSVersionTable.PSVersion` 같은 변수 표현은 Bash tool로 보내면 `.PSVersion...`으로 잘려 파싱 에러 — 반드시 PowerShell tool 사용.

**권한 동작 (`~/.claude/settings.json` 기준):**
- `Get-*`, `Test-Path`, `Select-String`, `git status/log/diff`, `gh pr view/list`, `docker ps`, `az containerapp show/logs`, `./gradlew *`, `adb devices`는 prompt 없이 통과.
- `Remove-Item`, `Set-Content`, `Stop-Process`, `git push`, `gh secret set`, `docker push`, `az containerapp delete/update`, `az acr repository untag/delete`는 ask로 막혀 매번 확인.
- `Format-Volume`, `Clear-Disk`, `Stop-Computer`, `Restart-Computer`, `git push --force origin main/master`는 deny — 우회 불가.

## Documentation

- `@docs/CHANGELOG.md` — 버전 이력 (v0.1.0 통합)
- `@docs/PRD.md` — Product Requirements
- `@docs/TRD.md` — Technical Requirements
- `@docs/SPEC.md` — 기능 명세
- `@docs/privacy-policy.md` — 개인정보 처리방침 (Play Store URL 호스팅 대상)
- `@docs/plans/README.md` — design+plan 페어 인덱스 (frontmatter 기반 자동 생성, status/PR/인시던트 추적 컬럼 포함)
- `@docs/ops/operations-snapshot.md` — **현재 운영 상태 단일 출처**
- `@docs/ops/incident-log.md` — 16건 인시던트 + root cause + 재발 방지 패턴
- `@docs/ops/migration-runbook.md` — Ktor → FastAPI 마이그레이션 절차 + 사후 정리
- `@docs/ops/monitoring-and-cost.md` — Sentry/ACR/Budget + §6 Destructive 명령 안전 패턴
- `@docs/ops/play-store-release.md` — 첫 출시 8단계 + 데이터 안전 답변
- `@docs/ops/dependency-deferred.md` — v0.1.0 출시 후 재검토할 의존성 보류 항목 (kotlin 2.3, starlette 1.1, healthConnect 1.2.0-alpha04) + 재개 조건 + 검증 절차
- `@docs/ops/containerapp-env-ktor-backup.json` — cutover 직전 env 스냅샷 (historical)

### 자동화 스크립트 (`scripts/`)
- `scripts/preflight-release.sh` — Spotless + Detekt + Tests + releaseArtifacts 일괄 (INC-04 방지)
- `scripts/alembic-autogen.sh` — postgres:16-alpine 컨테이너 기반 autogenerate (INC-07 방지)
- `scripts/sync-openapi.sh` — FastAPI 스펙을 `backend/openapi.json`으로 추출. 라우터/스키마 변경 시 필수 실행 + 같은 PR에 커밋. backend.yml의 drift detection step이 미커밋을 fast-fail로 차단.
- `scripts/gen-plans-index.sh` (+ `gen_plans_index.py`) — `docs/plans/*.md` frontmatter 기반 `docs/plans/README.md` 자동 생성. pre-commit hook 자동 호출 + 별도 CI workflow (`docs-plans-index.yml`) 가 drift 차단. **D5**: missing frontmatter 는 silent skip (점진 도입 + 다중 PR coordination 안전), malformed 만 fail.
- `scripts/register-azure-credentials.ps1` — SP 생성/패치 + AcrPush + GitHub secret 등록 (INC-17, 운영자 1회/만료 갱신)
- `scripts/warm-gradle.sh` — Gradle 데몬 사전 구동
- `scripts/claude-context.sh` / `claude-precompact.sh` — SessionStart/PreCompact 훅
- `scripts/hooks/secretref-guard.sh` — git commit 시 backend.yml 신규 secretref 가 Container
  App 에 등록됐는지 자동 검증 (룰 6 1차 가드). PreToolUse hook 으로 자동 실행. fail-open.

### Claude Code 슬래시 명령 (`.claude/commands/`)

- `/verify-deploy <inc-id>` — MCP (Sentry/Azure) 로 Phase 5 운영 검증 자동화 (alembic head
  + 스키마 컬럼 + Sentry 신규 issue). INC 별 검증 1-command. 자세한 내용:
  `docs/plans/2026-05-28-mcp-integration-setup-design.md` §3.3.

### OpenAPI Generator (Android)
- 입력: `backend/openapi.json` (git checked-in, `sync-openapi.sh`로 갱신)
- 출력: `app/build/generated/openapi/src/main/kotlin/com/gunnys/eundunhealth/api/generated/` (gitignored, `:app:openApiGenerate`로 자동 생성, `preBuild` 의존성으로 컴파일 전 항상 최신)
- 현재는 **side-by-side** — 기존 `EundunApi.kt`와 공존, Repository는 아직 generated 미사용. Phase 5 후속 PR에서 점진 전환.
- 라우터에 추가/변경 시 체크리스트: ① 라우터에 `operation_id="..."` 명시(Android 함수명과 일치) ② Query param은 `alias="..."`로 camelCase 노출 ③ `bash scripts/sync-openapi.sh` ④ 같은 PR에 `backend/openapi.json` 포함
