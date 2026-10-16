# 은둔헬스 (eundunHealth)

> PT 트레이너 없이 헬스장에서 혼자 운동하는 사용자를 위한 Android 헬스 앱

신체 정보(키·몸무게·골격근량·체지방률·성별)를 입력하면 [OSS ExerciseDB](https://oss.exercisedb.dev) 운동 라이브러리(추천 운동 46종 한국어 현지화 번들)를 바탕으로 **맞춤형 주간 운동 계획**을 자동 생성하고, **AI 맞춤 주간 식단**(Azure AI Foundry `DeepSeek-V3.2`)을 추천받으며, **Health Connect** 와 연동해 완료를 추적하고, **목표(체중·체지방)** 와 **배지**로 동기를 유지합니다.

[![Android CI](https://github.com/gunnysis/eundunHealth/actions/workflows/android.yml/badge.svg)](https://github.com/gunnysis/eundunHealth/actions/workflows/android.yml)
[![Backend CI/CD](https://github.com/gunnysis/eundunHealth/actions/workflows/backend.yml/badge.svg)](https://github.com/gunnysis/eundunHealth/actions/workflows/backend.yml)
![versionName](https://img.shields.io/badge/versionName-0.3.1-blue)
![versionCode](https://img.shields.io/badge/versionCode-36-blue)
![Min SDK](https://img.shields.io/badge/Min%20SDK-26-orange)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-7F52FF)
![Python](https://img.shields.io/badge/Python-3.14-3776AB)
![License](https://img.shields.io/badge/License-Proprietary-lightgrey)

---

## 목차

- [소개](#소개)
- [주요 기능](#주요-기능)
- [기술 스택](#기술-스택)
- [아키텍처](#아키텍처)
- [프로젝트 구조](#프로젝트-구조)
- [시작하기](#시작하기)
- [배포](#배포)
- [운영 안전 규칙](#운영-안전-규칙)
- [문서](#문서)
- [프로젝트 상태 및 로드맵](#프로젝트-상태-및-로드맵)
- [기여](#기여)
- [라이선스 및 개인정보 처리](#라이선스-및-개인정보-처리)
- [연락처](#연락처)

---

## 소개

**은둔헬스** 는 헬스장은 다니지만 PT(개인 트레이너)는 받지 않는 운동 초~중급자를 위한 Android 앱입니다. "오늘 뭐 해야 하지?" 의 진입 장벽을 신체 정보 기반 자동 계획 생성으로 제거하고, "꾸준히 못 하겠다" 의 동기 문제를 Health Connect 자동 추적 + 배지 + 목표 진행 시각화로 해소하는 것이 제품의 핵심 가치입니다.

- **대상 사용자** — 헬스장 회원, PT 미수강, 운동 초~중급자
- **언어 / 지역** — 한국어 UI, KST 시간대, 한국 사용자 대상
- **현재 단계** — **프로덕션 정식 출시(LIVE)**. 저장소 = 스토어 = **v0.3.1 (versionCode 36, 프로덕션 배포 하드닝 & 운동 한국어 현지화)**. 2026-10-16 태그 `v0.3.1` push → `release.yml` 이 프로덕션 트랙에 **10% 단계 출시(`status: inProgress`, `userFraction: 0.1`)**로 자동 업로드(run `38838518612`, 원장 자동 커밋 `1a19eb7`). 직전 v0.3.0/35(2026-09-21 AI 맞춤 주간 식단 출시) ← v0.2.0/34(2026-09-02 Entra 전환) ← v0.1.19/33(2026-07-03) ← 첫 프로덕션 출시 v0.1.18/32(2026-06-29). 백엔드는 main 머지 시 `backend-production` 환경 승인 후 자동 배포로 운영 중(활성 revision `eundunhealth-api--0000062` / 이미지 `2610d18`). 저장소는 2026-07-02 public 전환(사전 보안감사·식별자 스크럽 후 secret scanning·push protection·CodeQL 활성). 2026-07-29 Azure RG 이관(`apps` → `rg-eundunhealth-prod-krc`, 단일 RG — 서비스 URL 무영향)

상세 제품 요구사항은 [docs/PRD.md](docs/PRD.md), 기술 요구사항은 [docs/TRD.md](docs/TRD.md), 기능 명세는 [docs/SPEC.md](docs/SPEC.md) 참조.

---

## 주요 기능

- **자동 주간 운동 계획 & 한국어 현지화** — 신체 정보(키·몸무게·골격근량·체지방률·성별)와 휴식일 설정 기반으로 OSS ExerciseDB 에서 매주 자동 생성하며, 추천 운동 46종은 번들된 한국어 데이터(`korean_exercises.json` v2, SHA-256 지문 대조 + 영문 폴백)로 제공
- **AI 맞춤 주간 식단** (v0.3.0 / v0.3.1) — 신체 지표·목표·주간 운동량을 바탕으로 Azure AI Foundry `DeepSeek-V3.2` 모델이 7일×4끼 맞춤 식단과 영양소(칼로리·탄단지)를 생성 (국외 이전 사전 고지 및 `X-AI-Data-Processing-Consent: true` 명시적 동의 기반)
- **Health Connect 연동** — 운동 세션 자동 감지 및 완료 추적
- **통계 대시보드** (v0.2) — 12 주간 완료율 + 스트릭 차트
- **목표 설정 및 진행 시각화** (v0.3) — 체중 / 체지방률 목표 + 프로필 변화 이력 차트
- **배지 시스템** — 9 종(마일스톤 4 + 목표 달성 2 + 기타 3)
- **인증** — Microsoft Entra External ID(브라우저 위임 · Authorization Code + PKCE). 이메일/비밀번호 입력과 가입·비밀번호 재설정은 Entra 호스팅 페이지에서 처리
- **에러 표시 일관성** — 사용자 액션 실패는 inline 영구 표시 + a11y liveRegion + Sentry breadcrumb ([CLAUDE.md 룰 8](CLAUDE.md))

---

## 기술 스택

### Android 클라이언트 (`app/`)

| 영역 | 선택 | 비고 |
|------|------|------|
| 언어 / 런타임 | Kotlin 2.4.10, Java 17 | KSP 2.3.11 |
| 빌드 | Gradle 9.7.1, AGP 9.3.2 | Min SDK 26 / Target SDK 37, 내장 Kotlin + 새 DSL 기본 활성 |
| UI | Jetpack Compose (BOM 2026.08.00) | Material 3 |
| DI | Hilt 2.60.1 | 5개 모듈 (`NetworkModule`, `DatabaseModule`, `RepositoryModule`, `CoilModule`, `CoroutineModule`) |
| 비동기 | kotlinx-coroutines + Flow | |
| 네트워크 | Retrofit 3.0.0 + OkHttp 5.5.0 + Sentry-OkHttp | `TokenAuthenticator` 401 자동 갱신 |
| 로컬 DB | Room 2.8.5 (version=2) | `EundunDatabase` |
| 차트 | Vico 3.3.1 (compose-m3) | 통계 + 목표 진행 |
| Auth | MSAL Android 8.4.2 | RS256 JWT · `oid` claim |
| 건강 데이터 | Health Connect 1.1.0 (stable) | |
| 모니터링 | Sentry Android 8.57.0 (Gradle plugin 6.21.0) | 16KB page-aligned native libs |
| API 클라이언트 | OpenAPI Generator 7.25.0 (`api.generated.*`) | `backend/openapi.json` 입력, `preBuild` 자동, 6개 generated API |
| 품질 도구 | Detekt 1.23.8 (`detekt-cli` + `androidComponents`) + Spotless 8.10.1 + ktlint 1.5.0 | pre-commit hook + `check-agp10-compat.sh` 가드 |

### Backend (`backend/`)

| 영역 | 선택 | 비고 |
|------|------|------|
| 언어 / 런타임 | Python 3.14 | |
| 프레임워크 | FastAPI 0.141.1 + uvicorn 0.53.0 | |
| API 버전 | `1.0.0` (`backend/app/__init__.py:__version__`) | OpenAPI `info.version`, 앱과 독립 |
| ORM | SQLAlchemy 2.0.54 async + asyncpg 0.31.0 | `Mapped[T]` 패턴 |
| 마이그레이션 | Alembic 1.20.0 (head: `0dda67f1638a`) | async 엔진 연동 |
| HTTP 코어 | starlette 1.6.0 | |
| Auth 검증 | PyJWT[crypto] 2.15.1 + JWKS | **RS256** (Entra External ID), 24h TTL 캐시 |
| AI 클라이언트 | openai 3.16.2 (`AsyncOpenAI`) | Azure AI Foundry `DeepSeek-V3.2` MaaS 연동 |
| 모니터링 | Sentry SDK 2.69.2 (`sentry-sdk[fastapi]`) | `eundunhealth-backend` 프로젝트 |
| 품질 도구 | ruff 0.16.8 + mypy 2.3.1 strict + bandit 1.9.4 + pip-audit 2.10.1 | pytest 9.1.1 — 119/119 PASS, coverage 94% (sysmon core) |

### 인프라

| 구성 요소 | 값 |
|-----------|---|
| Container App | `eundunhealth-api` (RG `rg-eundunhealth-prod-krc`, Korea Central, **warm min 1 / max 3** + Key Vault 참조 + system MI + probe 3종, active revision `0000062`) |
| Key Vault | `kv-eundunhealth` (Standard, Azure RBAC, 90d soft-delete + purge protection) — 백엔드 secret **8개** |
| Container Registry | ACR `eundunhealthacr` (Basic SKU, 주간 `acr purge` 스케줄 Task 2개) |
| Database | Azure PostgreSQL Flexible Server `healthapp` (B1ms, 32GB) |
| Auth | Microsoft Entra External ID 외부 테넌트 `eundunhealthciam` (Asia Pacific — 한국 리전 미지원) |
| AI 모델 | Azure AI Foundry `eundunhealth-ai-foundry` (East US 2, `DeepSeek-V3.2` `GlobalStandard` MaaS) |
| 운동 데이터 소스 | OSS ExerciseDB (`oss.exercisedb.dev`, 무인증 + 로컬 한국어 번역 번들 46종) |
| CI/CD | GitHub Actions 7개 워크플로 (`android.yml`, `backend.yml`, `backend-rollback.yml`, `release.yml`, `warm-baseline-check.yml`, `doc-audit.yml`, `docs-plans-index.yml`) + Dependabot |
| 에러 추적 | Sentry (Android / Backend 별도 프로젝트) |

---

## 아키텍처

### Android — Clean Architecture

```
ui/          # Jetpack Compose 화면 11개 + ViewModel 10개 + Navigation (sealed Screen)
  components/    # 공용 컴포넌트 8개 (AuthErrorBanner, BodyMetricsSliders, LineChart, ProfileSlider, ProfileSummaryCard, Skeleton, Error, Empty)
  auth/ home/ onboarding/ profile/ statistics/ goal/ meal/ history/ badges/ workout/ splash/
domain/      # 모델 + 리포지토리 인터페이스 + UseCase + AppError sealed class
data/        # Repository 구현 + OpenAPI 생성 클라이언트 + Room + Health Connect + MSAL(auth/) + 한국어 번역(translation/) + DataStore
di/          # Hilt 모듈 5개 (NetworkModule, DatabaseModule, RepositoryModule, CoilModule, CoroutineModule)
```

핵심 패턴
- UDF-Enhanced ViewModel([CLAUDE.md 룰 11](CLAUDE.md)): 단일 `_uiState: MutableStateFlow<XxxUiState>` + `@Immutable` UiState/SideEffect + `collectAsStateWithLifecycle` + `Channel<SideEffect>`
- ViewModel 의 userId 획득은 `AuthRepository.getCurrentUserId()` — MSAL 클라이언트 직접 주입 금지. userId = 토큰의 `oid` claim(`sub` 아님)
- 401 → `TokenAuthenticator` 가 `AtomicReference` 의 토큰을 5초 timeout 으로 갱신
- 일시 장애는 `RetryInterceptor` 가 지수 백오프(3 회 / 500ms·1s·2s)

### Backend — Layered FastAPI

```
app/main.py           # FastAPI 앱 + lifespan(DB / Sentry / DeepSeek AI client) + 모듈 레벨 CORS + 글로벌 예외 핸들러
app/config.py         # pydantic-settings (@lru_cache)
app/database.py       # DeclarativeBase + get_db() UoW (app.state.session_factory)
app/dependencies.py   # JWKS 기반 JWT 검증 (RS256, Entra External ID)
app/exceptions.py     # AppException 계층 (NotFound / Conflict / BadRequest / ServiceUnavailable / BadGateway)
app/models/           # SQLAlchemy 2.0 Mapped[T] (user_profile, weekly_plan, badge, profile_history, goal, meal_plan)
app/schemas/          # Pydantic CamelSchema (alias_generator=to_camel)
app/repositories/     # DB 접근 추상화
app/services/         # 비즈니스 로직 (account / statistics / goal / meal_plan / meal_ai_client ...)
app/routers/          # 얇은 라우터, Service 위임
alembic/versions/     # async 엔진 연동 마이그레이션 (head: 0dda67f1638a)
```

### API 엔드포인트 (20 개 — JWT 필요 16 + 공개 4)

**공개 (4)**

| Method | Path | 비고 |
|--------|------|------|
| `GET` | `/health` | 헬스체크 — liveness (Container App probe) |
| `GET` | `/health/ready` | readiness probe (DB `SELECT 1` → 200/503) |
| `GET` | `/privacy` | 개인정보 처리방침 (Play 등록 URL, `docs/store/` 렌더) |
| `GET` | `/account-deletion` | 계정·데이터 삭제 안내 (Play 등록 URL) |

> HTML 브라우저 라우트(`/privacy`·`/account-deletion`)는 `include_in_schema=False` — openapi.json(Android 생성기 입력)에서 제외해 앱이 호출하지 않는 죽은 클라이언트 메서드 생성을 막는다. 라우트 자체는 정상 동작(브라우저·크롤러 직접 접근).

**JWT 필요 (16)**

| Method | Path | 비고 |
|--------|------|------|
| `GET` / `PUT` | `/profile` | `gender`, `restDay` 포함 |
| `GET` | `/profile/history?limit=50` | v0.3 |
| `GET` / `POST` | `/weekly-plan` | 주간 운동 계획 |
| `PATCH` | `/weekly-plan/complete` | 일자별 완료 표시 |
| `GET` | `/weekly-plan/history?page=&size=` | 페이지네이션 |
| `GET` | `/weekly-plan/previous?weekStart=` | v0.2 알고리즘 입력 |
| `GET` | `/weekly-plan/statistics?weeks=12` | v0.2 완료율 + 스트릭 |
| `GET` / `POST` | `/badges`, `/badges/{key}` | 9 종 |
| `GET` / `PUT` | `/goals` | v0.3 |
| `POST` | `/meals/generate` | v0.3.0 / v0.3.1 AI 주간 식단 생성 (`X-AI-Data-Processing-Consent: true` 필수) |
| `GET` | `/meals/current` | v0.3.0 현재 주간 식단 조회 |
| `DELETE` | `/account` | Microsoft Graph 연동 (삭제 204 + `deletedItems` 즉시 파기) |

---

## 프로젝트 구조

```
.
├── app/                      # Android 앱 (Kotlin / Compose)
├── backend/                  # FastAPI 백엔드 (Python 3.14)
│   ├── app/                  # 애플리케이션 코드
│   ├── alembic/              # DB 마이그레이션 (head: 0dda67f1638a)
│   ├── tests/                # pytest (119 PASS, coverage 94%)
│   ├── openapi.json          # Android OpenAPI Generator 입력
│   └── docker-compose.yml    # 로컬 PG + uvicorn 동시 기동
├── docs/                     # 모든 비코드 문서
│   ├── PRD.md / TRD.md / SPEC.md
│   ├── CHANGELOG.md
│   ├── store/                # Play Store 호스팅 대상 (개인정보 처리방침·계정 삭제 안내)
│   ├── plans/                # design + plan 페어 / 토픽 ledger
│   └── ops/                  # 운영 스냅샷·배포 전 체크리스트·런북·인시던트
├── scripts/                  # 자동화 (preflight / agp10-compat / verify_containerapp_deployment / alembic / openapi-sync / hooks)
├── config/                   # Detekt 설정·baseline
├── gradle/libs.versions.toml # Android 버전 카탈로그
├── .github/workflows/        # 7 workflows (android / backend / backend-rollback / release / warm-baseline-check / doc-audit / docs-plans-index)
├── .githooks/                # pre-commit (spotless + detekt + plans-index)
├── CLAUDE.md                 # 운영 안전 규칙 + AI 협업 컨벤션
└── README.md                 # ← 본 문서
```

---

## 시작하기

### 사전 요구사항

| 도구 | 버전 | 용도 |
|------|------|------|
| JDK | 17 | Android / Gradle |
| Android Studio | Hedgehog 이상 권장 | 빌드 / 디바이스 디버깅 |
| Android SDK | API 37 | Target SDK |
| Python | 3.14 | Backend |
| Docker Desktop | latest | Backend 로컬 실행 + Alembic autogenerate |
| Git | 2.40+ | hooks (`core.hooksPath`) |
| (선택) Azure CLI | latest | 운영 / 시크릿 관리 |
| (선택) gh CLI | latest | PR / 워크플로 |

### 1) 저장소 클론 및 초기 설정

```bash
git clone https://github.com/gunnysis/eundunHealth.git
cd eundunHealth

# pre-commit hook 활성화 (clone 직후 1 회)
git config core.hooksPath .githooks

# local.properties 작성 (Backend URL, Sentry DSN, Entra API scope, release signing)
cp local.properties.example local.properties
# → 파일 열어 비밀값 채우기
```

> [!IMPORTANT]
> `local.properties` 의 비밀값은 절대 커밋하지 마세요. `build.gradle.kts` 에 하드코드 금지 — BuildConfig 필드 로딩만 사용합니다.

### 2) Android 빌드

```bash
# 디버그 빌드
./gradlew :app:assembleDebug

# 릴리스 산출물 (AAB + APK 동시) — 운영 룰 2
./gradlew :app:releaseArtifacts
# 또는 모든 게이트(spotless + detekt + tests) 포함 일괄
bash scripts/preflight-release.sh

# 단위 테스트
./gradlew :app:testDebugUnitTest

# 코드 품질
./gradlew :app:spotlessApply      # 자동 포맷
./gradlew :app:spotlessCheck      # 포맷 검증 (CI 사용)
./gradlew :app:detektDebug        # 정적 분석 (detekt-cli + androidComponents)
bash scripts/check-agp10-compat.sh # AGP 10 호환성 가드
```

### 3) Backend 실행 (Docker Compose 권장)

```bash
cd backend

# 가상환경 (도구 실행용) — Windows 경로 기준. macOS/Linux 는 `.venv/bin/` 사용.
python -m venv .venv
.venv/Scripts/pip install -r requirements-dev.txt

# 컨테이너로 PG + uvicorn 동시 기동
docker compose up -d
docker compose logs -f api
docker compose down -v            # 정리

# 호스트 직접 실행 (DB 는 별도 기동 필요)
.venv/Scripts/uvicorn app.main:app --reload --port 8080
```

### 4) Backend 테스트 및 정적 검사

```bash
cd backend
.venv/Scripts/pytest tests/ -v --cov=app
.venv/Scripts/ruff check app/ tests/
.venv/Scripts/mypy app/
.venv/Scripts/bandit -r app -ll
.venv/Scripts/pip-audit -r requirements.txt --strict
```

### 5) Alembic 마이그레이션

```bash
cd backend
.venv/Scripts/alembic upgrade head
```

> [!WARNING]
> 새 마이그레이션 작성은 반드시 `bash scripts/alembic-autogen.sh "..."` 사용. SQLite 위에서 autogenerate 하면 UUID↔NUMERIC 거짓 양성이 들어가 운영 cast 에러로 직결됩니다 ([CLAUDE.md 룰 3](CLAUDE.md), INC-07).

### 6) OpenAPI 스펙 동기화 (라우터·스키마 변경 시 필수)

```bash
bash scripts/sync-openapi.sh
# → backend/openapi.json 갱신 → 같은 PR 에 커밋
# → Android :app:openApiGenerate 가 preBuild 단계에서 자동 재생성
```

### 7) 디바이스에 설치

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
# 또는
adb install -r app/build/outputs/apk/release/app-release.apk
```

---

## 배포

### 자동 (권장)

`main` 브랜치 push(트리거 paths: `backend/**`, `.github/workflows/backend.yml`, `scripts/verify_containerapp_deployment.py`) 또는 `workflow_dispatch(deploy=true)` → GitHub Actions `backend.yml` 이 빌드 → Trivy 스캔 → ACR push → `backend-production` 환경 승인 게이트 → secret precheck → Container App 및 Reaper Job 동기 업데이트 → `/health`·`/health/ready` 및 `verify_containerapp_deployment.py` 불변 조건 검증을 일관 수행.

```bash
# 긴급 재배포 / secret 회전 검증용 수동 트리거 (deploy=true 필수)
gh workflow run backend.yml --ref main -f deploy=true

# 긴급 롤백 (ACR 이미지 태그 지정 → Container App + Reaper Job 동시 복구)
gh workflow run backend-rollback.yml --ref main -f image_tag=<sha7> -f reason="..."
```

### 수동 (로컬, CI 우회용)

```bash
bash C:/programming/docker/eundunhealth-api/redeploy.sh [tag]
```

uvicorn 이미지 빌드 → ACR `eundunhealthacr` → Container App `eundunhealth-api` 업데이트 → `/health` 헬스체크 → timestamp 태그 자동 정리(최근 5 개 보존). 자세한 절차는 [docs/ops/migration-runbook.md](docs/ops/migration-runbook.md) 및 [docs/ops/predeployment-readiness.md](docs/ops/predeployment-readiness.md).

### Service Principal 갱신 (OIDC 장애 시 긴급 폴백 전용)

```powershell
pwsh -File scripts/register-azure-credentials.ps1 -Verify
```

---

## 운영 안전 규칙

지난 인시던트의 root cause 가 모두 누적된 결과로 도출된 **13 개 강제 규칙**. 자세한 본문은 [CLAUDE.md](CLAUDE.md), 인시던트 이력은 [docs/ops/incident-log.md](docs/ops/incident-log.md).

| # | 룰 | 출처 |
|---|----|------|
| 1 | ACR 정리는 `untag` 만 — `delete --image` 는 공유 manifest 까지 삭제 | INC-01 |
| 2 | 릴리스 산출물은 `releaseArtifacts` 또는 `preflight-release.sh` 하나로 | INC-04 |
| 3 | Alembic autogenerate 는 PostgreSQL 컨테이너 위에서만 (`alembic-autogen.sh`) | INC-07 |
| 4 | `lifespan` 안에서 `app.add_middleware()` 호출 금지 — 모듈 레벨 등록 | INC-03 |
| 5 | Auth 제공자/테넌트는 실사용자 확보 후 절대 교체 금지 | INC-14 |
| 6 | `backend.yml` 의 새 `secretref` 는 시크릿 set + 가드 step + 스냅샷 동시 갱신 | INC-18 |
| 7 | 스키마 변경 PR 은 같은 PR 에서 entrypoint(`alembic upgrade head`) 검증 포함 | INC-2026-05-27-01 |
| 8 | Auth/UI 실패 표시는 inline + persistent + a11y liveRegion + Sentry breadcrumb | INC-2026-05-26-01 |
| 9 | Design doc 의 baseline / 추정값은 측정 후 결정 | PR #68 |
| 10 | Subagent reviewer 의 측정 결과는 controller 가 직접 fact-check | PR #68 |
| 11 | ViewModel 은 UDF-Enhanced 패턴 준수 (`collectAsStateWithLifecycle` 전용) | 2026-06-06 |
| 12 | Gson 반사 모델은 R8 keep 전수 + 패키지 단위 (`ProguardKeepRulesTest`) | INC 2026-06-15 |
| 13 | Play 업로드 전 versionCode 는 원장(`play-upload-ledger.md`)과 대조 | INC-2026-06-19-28 |

> [!CAUTION]
> Destructive 명령(`Remove-Item -Recurse`, `az containerapp delete`, `git push --force`, `az acr repository delete`)을 실행하기 전 항상 [§6.8 5문항](docs/ops/monitoring-and-cost.md) 점검.

---

## 문서

| 문서 | 내용 |
|------|------|
| [CHANGELOG](docs/CHANGELOG.md) | 버전별 변경 이력 |
| [버전 관리](docs/conventions/versioning.md) | 앱/백엔드 버전 SSoT · semver 정책 · bump 절차 |
| [PRD](docs/PRD.md) | 제품 요구사항 |
| [TRD](docs/TRD.md) | 기술 요구사항 + 구현 후 변경 사항 |
| [SPEC](docs/SPEC.md) | 기능 명세 |
| [개인정보 처리방침](docs/store/privacy-policy.md) | Play Store 게시 대상 |
| [계정 및 데이터 삭제](docs/store/account-deletion.md) | Play Store 계정 삭제 요청 URL 대상 |
| [Plans 인덱스](docs/plans/README.md) | design+plan 페어 + 토픽 ledger (자동 생성) |
| [운영 스냅샷](docs/ops/operations-snapshot.md) | 현재 운영 상태 단일 출처 |
| [배포 전 체크리스트](docs/ops/predeployment-readiness.md) | v0.3.1 배포 게이트 · 불변 조건 검증 · 롤백 런북 |
| [Play 업로드 원장](docs/ops/play-upload-ledger.md) | versionCode 단조성 SSoT (`LAST_UPLOADED_VERSION_CODE=36`) |
| [인시던트 로그](docs/ops/incident-log.md) | 인시던트 이력 + root cause + 재발 방지 패턴 |
| [마이그레이션 런북](docs/ops/migration-runbook.md) | Ktor → FastAPI 절차 + 사후 정리 |
| [Container Apps Job 런북](docs/ops/azure-container-apps-jobs.md) | orphan reaper Job 프로비저닝 패턴 + 함정 회피(E1~E4, 공식문서 fact-check) |
| [모니터링 및 비용](docs/ops/monitoring-and-cost.md) | Sentry / ACR / Budget + 안전 패턴 |
| [Play Store 출시](docs/ops/play-store-release.md) | 첫 출시 8 단계 + 데이터 안전 답변 |
| [의존성 보류](docs/ops/dependency-deferred.md) | 출시 후 재검토 항목 |
| [CLAUDE.md](CLAUDE.md) | AI 협업 컨벤션 + 운영 안전 룰 1~13 |

---

## 프로젝트 상태 및 로드맵

**현재 버전** — 저장소 = Play 프로덕션 = `0.3.1` (versionCode `36`, 프로덕션 배포 하드닝 & 운동 한국어 현지화) — **Google Play 프로덕션 정식 출시(LIVE)**. v0.3.1 = 2026-10-16 태그 `v0.3.1` push → `release.yml` 이 프로덕션 트랙에 **10% 단계 출시(`status: inProgress`, `userFraction: 0.1`)**로 업로드 + 원장 자동 갱신(`1a19eb7`, run `38838518612`); 백엔드는 `eundunhealth-api--0000062`(이미지 `2610d18`) 활성. 직전 v0.3.0/35(2026-09-21) = **AI 맞춤 주간 식단 출시**(Azure AI Foundry `DeepSeek-V3.2` 연동 · `POST /meals/generate` & `GET /meals/current` · `MealPlanScreen` · 프로필 성별 추가). 직전 v0.2.0/34(2026-09-02) = **프로덕션 트랙 첫 자동 출시**(Supabase → Entra External ID 전환). 직전 v0.1.19/33(2026-07-03 승격; 첫 출시 v0.1.18/32, 2026-06-29 승인) = Android CD 첫 자동 출시.

### 마일스톤 진행

스펙 마일스톤(v0.1 / v0.2 / v0.3)은 모두 구현 완료. 출시 후 v0.1.x ~ v0.3.x 는 기능 확장 / 안정화 / 배포 하드닝 릴리스 라인.

**스펙 (3 트랙)**
- [x] **v0.1** — 신체 정보 기반 주간 운동 계획 + 완료 추적 + 배지(7 종)
- [x] **v0.2** — 통계 대시보드 (12 주간 완료율 + 스트릭)
- [x] **v0.3** — 목표 설정 (체중 / 체지방) + 프로필 변화 차트 + 휴식일 커스터마이징 + 배지(9 종) + 비밀번호 재설정 + 회원 탈퇴

**출시 및 기능 고도화 라인**
- [x] **v0.1.1** — 가입 이메일 확인 흐름
- [x] **v0.1.2** — Supabase encoding hotfix
- [x] **v0.1.3** — Android App Links 자동 로그인
- [x] **v0.1.4** — Supabase explicit redirectUrl + backend 422 observability
- [x] **v0.1.5** — Vico 2.1 → 3.1 + Health Connect 1.1.0 stable + starlette 1.1.0
- [x] **v0.1.6** — Signup Failed UX inline error banner (INC-2026-05-26-01)
- [x] **v0.1.7** — LoginScreen + ForgotPasswordScreen 룰 8 적용 + `AuthErrorBanner` promote
- [x] **v0.1.8** — UDF-Enhanced 12 VM 리팩토링 + OkHttp5/Coil3 + 의존성 bump (정식 출시)
- [x] **백엔드 인프라 (2026-06-09)** — cold start 제거(min-replicas=1) + Key Vault full IaC (health probe 3종 · system MI · `--yaml` 배포)
- [x] **v0.1.9** — Health Connect 체중·체지방 가져오기 + 홈 "오늘의 활동" 요약(걸음·칼로리·심박) + HC 동기화 경로 정리/갤럭시 워치 온보딩 (정식 출시)
- [x] **v0.1.11** — Play Store 계정 삭제 페이지 + 계정 삭제 완전성(목표·신체이력 purge) 수정 + Health Connect 권한 rationale intent(Android 14+ 연동 버튼 무반응) 수정
- [x] **v0.1.12** — Health Connect 체성분(체중·체지방) 가져오기 제거 + `READ_WEIGHT`/`READ_BODY_FAT` 권한 회수(6→4) + 신체 4지표 수동 단일화
- [x] **v0.1.13** — 코드베이스 리팩토링 (내부 품질, 사용자 영향 없음) — WeeklyPlanGenerator 추출·테스트화 + 백엔드 실버그(JWT except·goal createdAt) + detekt baseline 단일화 + 중복·죽은코드 정리 (#107~#112)
- [x] **v0.1.14** — 출시 준비 종합 — 실기기 제보 2버그 근본수정(빈 운동계획=R8 keep 갭, 완료 토글 해제 보존=수동 우선) + 4-에이전트 전수감사 출시차단 해소(완료 정합성·입력검증·인증 견고화·운동상세 GIF/복사/데이터흐름·캐시/파싱/KST·폴리시) + 재발방지 가드 (PR #122)
- [x] **v0.1.15** — 감사 LOW 후속(내부 품질) — SideEffect 수집 라이프사이클-aware(`ObserveAsEvents` 헬퍼) + 백엔드(alembic rest_day server_default · CORS 와일드카드 차단) + starlette 1.3.1 CVE bump (PR #123)
- [x] **v0.1.16** — 출시 후 심층 감사 개선 — JWKS 이벤트루프 블로킹 제거(`asyncio.to_thread`) + 무테스트 ViewModel 테스트 + GoalScreen 에러상태 + DayPlanCard `remember` perf + 활동 a11y + history COUNT window + `user_profile_history` 복합인덱스 + 계정삭제 orphan reaper(Container Apps Job 주간 cron) + 재발방지 런북 (PR #126/#127)
- [x] **v0.1.17** — 공개 출시 전 7-도메인 전체 감사(출시차단 0건) — Rule 8 inline 에러 배너(Onboarding·Home·Profile) + HistoryScreen a11y + BadgeViewModel 테스트 + 백엔드 프로필 경계 테스트 + account_service 로그 구조화 + 개인정보/계정삭제 백엔드 공개 라우트(`GET /privacy`·`/account-deletion`, md→HTML) + 문서 드리프트 정정 (PR #128)
- [x] **v0.1.18** — 출시 재업로드 — versionCode 31 Play 중복 거부(INC-2026-06-19-28) → 32 재빌드(앱 동작 변화 없음=v0.1.17 빌드 동일) + versionCode 단조성 가드(원장 `play-upload-ledger.md` · `check-version-monotonic.sh` · 룰 13)
- [x] **v0.1.19** — Android CD 첫 자동 출시(내부 트랙) — 태그 push → `release.yml`(preflight 게이트 → 서명 AAB → Play 내부 트랙 업로드 → 원장 자동 갱신) 실 e2e 실증 + 의존성 배치(#139) + release 서명 keystore 존재-조건부화(INC-2026-07-02-29). 사용자 가시 동작 변화 없음 (PR #143)
- [x] **v0.2.0** — **인증 제공자 전환 Supabase Auth → Microsoft Entra External ID**(브라우저 위임 MSAL, RS256, `oid` 식별자, 계정삭제 Microsoft Graph) + 기술부채 청산 T0~T7(Python 3.14 · Gradle 9.7.1 · openapi-generator 7.25.0) + 하드닝 H1~H10(R8 릴리스 빌드를 PR 게이트에 추가 포함) + Azure 정리(`acr purge` Task, 2.21→0.60 GiB) (PR #165). 후속: Play 트랙 internal → **production** 전환 + Supabase 전량 폐기(프로젝트·KV·GitHub secret) + 실기기 골든패스에서 **로그인 100% 실패 결함 발견·수정**(MSAL 예약 scope 직접 요청, INC-2026-09-02-31). **2026-09-02 Play 프로덕션 출시 완료**
- [x] **v0.3.0** — **AI 맞춤 주간 식단 기능 출시** — Azure AI Foundry `DeepSeek-V3.2` MaaS 연동 + `meal_plans`/`meal_plan_items` 테이블(alembic `718783053fcd`) + 프로필 `gender` 필드(alembic `0dda67f1638a`) + `POST /meals/generate` & `GET /meals/current` + Android `MealPlanScreen`/`MealPlanViewModel` (PR #170/#171). **2026-09-21 Play 프로덕션 출시 완료**
- [x] **v0.3.1** — **프로덕션 배포 하드닝 & 운동 한국어 현지화** — 추천 운동 46종 한국어 번들(`korean_exercises.json` v2, SHA-256 지문 대조 + 영문 폴백) + AGP 10 대비 빌드 현대화(`org.jetbrains.kotlin.android` 제거, `detekt-cli` + `androidComponents`, `check-agp10-compat.sh`) + AI 식단 명시적 동의 헤더(`X-AI-Data-Processing-Consent: true`) & `MEAL_AI_ENABLED` kill switch + `backend-production` 환경 승인 게이트 & `backend-rollback.yml` 롤백 워크플로 + `verify_containerapp_deployment.py` 배포 사후 불변 조건 검증 + Play 프로덕션 **10% 단계 출시** (PR #178/#181/#186/#188/#191). **2026-10-16 Play 프로덕션 10% 단계 출시 완료**

**다음**
- [ ] **v0.3.1 100% 롤아웃 확대** — Play Console Vitals 및 Sentry 모니터링 확인 후 25% → 50% → 100% 단계 확대

세부 history 는 [docs/CHANGELOG.md](docs/CHANGELOG.md) 와 [docs/plans/logs/](docs/plans/logs/) 토픽 ledger 참조.

---

## 기여

본 저장소는 단독 개발 프로젝트(2026-07-02 공개 전환)지만, 작업 시 다음 컨벤션을 따릅니다.

### 브랜치 정책

- `main` — 항상 `origin/main` 과 동기, **모든 작업은 feature 브랜치 + PR**
- 명명: `feat/<topic>`, `fix/<topic>`, `chore/<topic>`, `docs/<topic>`

### 커밋 메시지

[Conventional Commits](https://www.conventionalcommits.org/) 형식. 예시:

```
feat(auth): inline error banner promote to ui/components
fix(backend): add rest_day column to user_profiles (INC-2026-05-27-01)
chore(release): v0.3.1 versionCode 36 + docs sync
```

### PR 체크리스트

PR 작성 시 [.github/PULL_REQUEST_TEMPLATE.md](.github/PULL_REQUEST_TEMPLATE.md) 자동 적용. Backend 변경은 룰 6 의 3-점 체크리스트, 스키마 변경은 룰 7 의 entrypoint 검증 항목 필수.

### 로컬 게이트 (pre-commit hook 자동)

1. `spotlessApply` — Kotlin 자동 포맷
2. `detektDebug` — 정적 분석 (`config/detekt/baseline-debug.xml`)
3. `gen-plans-index.sh` — `docs/plans/README.md` 자동 재생성

### 새 design+plan 페어 추가 시

`docs/plans/_templates/{design,plan}.md` 복사 → frontmatter 의 `ledger_topic` 필드 명시(`android` / `backend` / `dependencies` / `process-infra`) → PR 머지 후 해당 ledger 의 Recent 섹션에 entry 통합 + 페어 파일 `git rm`. 자세한 워크플로는 [docs/plans/README.md](docs/plans/README.md).

---

## 라이선스 및 개인정보 처리

- **라이선스** — **Proprietary / All Rights Reserved**. 저장소·앱 소스·아트워크의 무단 복제·재배포·수정·상업적 이용을 금지합니다.
- **개인정보 처리방침** — [docs/store/privacy-policy.md](docs/store/privacy-policy.md) (Play Store 등재 URL 호스팅 대상).
- **계정 및 데이터 삭제** — [docs/store/account-deletion.md](docs/store/account-deletion.md) (Play Store 계정 삭제 요청 URL 호스팅 대상).
- **외부 의존성 라이선스** — 각 의존성은 자체 라이선스를 따릅니다(예: OSS ExerciseDB, MSAL, Sentry SDK, FastAPI 등).

---

## 연락처

- **개발자 / 유지보수** — [@gunnysis](https://github.com/gunnysis) (qkr133456@gmail.com)
- **버그 리포트 / 기능 제안** — GitHub Issues
- **운영 인시던트** — [docs/ops/incident-log.md](docs/ops/incident-log.md) 에 사후 root cause 기록
