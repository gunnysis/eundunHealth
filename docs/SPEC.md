# eundunHealth 기능 명세서

> **문서 버전:** v1.0 (초기 설계) — 본문은 그대로 보존.
> **현재 제품 상태:** 최신 버전·구현 상태의 단일 출처(SSoT)는 [ops/operations-snapshot.md](./ops/operations-snapshot.md) + [CHANGELOG.md](./CHANGELOG.md) — 본 명세 작성 이후 v0.1.x 다수 릴리스(v0.2·v0.3 spec 모두 구현 완료). 본문(기능 명세)은 초기 설계 그대로 보존하며, 버전 스냅샷은 여기에 하드코딩하지 않는다(릴리스마다 drift 방지 — 과거 v0.1.7 에 고착된 이력 있음). 자세한 차이는 [TRD.md](./TRD.md) 참조.
>
> **인증 제공자 (2026-09 전환):** Supabase Auth → **Microsoft Entra External ID**(외부 테넌트, 브라우저 위임). IdP 는 JWT 발급(RS256/JWKS) + 가입·이메일 검증·비밀번호 재설정 페이지만 담당하며, 모든 비즈니스 데이터는 Azure PostgreSQL(`healthapp`)에 저장한다(범위는 전환 전후 동일). **아래 인증 관련 본문(로그인/회원가입 화면, 이메일 확인 흐름, App Links)은 전환 이전 설계이며 현재 구현과 다르다** — 현행은 `docs/plans/2026-09-01-entra-external-id-migration-design.md`(→ `docs/plans/logs/process-infra.md` 2026-09-02 entry 로 흡수) §5 참조.
> 
> **AI 식단 생성 및 운동 한국어 현지화 (2026-09~10, v0.3.0~v0.3.1):** 주간 식단 자동 생성 기능은 Azure AI Foundry MaaS(`DeepSeek-V3.2`)를 통해 구현 완료되었다(v0.3.0). 프론트엔드는 `MealPlanScreen` Compose UI와 Hilt+Retrofit(전용 130s read timeout) 통신을 사용하고, 백엔드는 FastAPI에서 `AsyncOpenAI` + Tenacity 재시도·Pydantic JSON 검증·`X-AI-Data-Processing-Consent: true` 국외 이전 사전 동의 헤더 강제 및 `MEAL_AI_ENABLED` 킬스위치를 채택했다(v0.3.1). 운동 카탈로그 46종(이름·수행 방법·팁) 및 근육/부위/장비 라벨도 한국어로 전면 현지화되었다(`ExerciseLocalization`). 상세 내용은 `docs/plans/logs/backend.md`(2026-09-20) 및 `docs/plans/logs/process-infra.md`(2026-10-16) 참조.
>
> **적응형 메뉴트리 및 온디바이스 스마일 트레이닝 (2026-10, v0.4.0~v0.5.0):** 로그인 후 탐색은 `오늘·식단·진행·프로필` 4개 최상위 목적지(`MainAppShell`, `ProgressHubScreen`, `ProfileHubScreen`)로 개편되었다(v0.4.0). `진행` 허브에서 진입하는 **스마일 트레이닝**(`SmileTrainingScreen`, v0.5.0)은 전면 카메라(`CameraX` 1.6.2)와 온디바이스 `ML Kit Face Detection`(17.1.0)으로 영상·사진을 저장하거나 전송하지 않고 기기 메모리에서만 실시간 미소 근육 운동을 코칭하며, 오프라인 우선 Room(`SmileTrainingDatabase`) + `WorkManager`(`SmileSyncWorker`)와 백엔드 `smile_training_sessions` 테이블(alembic `a3869f575d6a`, `/smile-training/*` 4개 API)로 세션 요약 및 연속일 통계를 동기화한다. 상세 내용은 `docs/plans/logs/android.md`(2026-10-26) 참조.
>
> **적응형 주간 운동 계획 v2 및 휴식 요일 자유 다중 지정 (`restDays`, 2026-10-27):** 주간 운동 계획이 `/v2/weekly-plan` 계약과 `planMode`(`gym` / `home`) 필수 스키마, 그리고 프로필 다중 휴식 요일 `restDays`(`INTEGER[]`, 주 1~6일 자유 선택, Zero-Legacy 전환; alembic `d7f4a2c91b10`)로 전환되었다. 사용자는 매주 월요일(KST) 첫 진입 시 `헬스장`(ExerciseDB 기반) 또는 `홈트레이닝`(앱 내장 18종 무장비 카탈로그 `local:home:<slug>:v2.0`, `prescriptionUnit = REPS | SECONDS`) 환경을 직접 선택해 계획을 생성하거나, 프로필 허브의 `기본 운동 환경`(`PUT /v2/profile/default-workout-plan-mode`)을 설정해 주간 자동 생성을 사용할 수 있다. 비휴식일(1~6일)에는 대근육 우선순위 큐(`[PUSH → LEGS → PULL → CARDIO → MIXED → CARDIO]`)가 순환 배치되며, 주중 계획은 불변(동일 주 재생성 시 409 `weekly_plan_exists`)이고 손상된 계획 복구만 명시적 `DELETE /v2/weekly-plan`으로 허용한다. 상세 설계는 `docs/reports/2026-10-26-adaptive-weekly-workout-proposal.md` 및 `docs/plans/2026-10-27-flexible-rest-days-design.md` 참조.

## 프로젝트 개요

**은둔헬스(eundunHealth)** 는 사용자의 신체 정보와 운동 환경(`헬스장` / `홈트레이닝`)을 기반으로 맞춤 주간 운동 계획과 AI 주간 식단 가이드를 생성하고, 운동 달성을 추적하는 Android 앱입니다.

- **패키지**: `com.gunnys.eundunhealth`
- **최소 SDK**: 26 (Android 8.0)
- **대상 SDK**: 37 (Android 17)
- **백엔드**: FastAPI(Python 3.14) — 본 문서 v1.0 작성 당시 Ktor였으나 v0.1.0에서 전환됨.
- **운동 데이터**: OSS ExerciseDB(`oss.exercisedb.dev`, 인증 불필요) + 앱 내 46종 한국어 현지화 사전(`ExerciseLocalization`) + 홈트레이닝 내장 18종 무장비 카탈로그(`HomeWeeklyPlanGenerator`).
- **AI 식단**: Azure AI Foundry Serverless MaaS(`DeepSeek-V3.2`, GlobalStandard).
- **인증**: Microsoft Entra External ID(외부 테넌트 `eundunhealthciam`, Asia Pacific, JWKS RS256).

---

## 기술 스택

> 버전은 여기에 적지 않는다 — SSoT: Android = `gradle/libs.versions.toml`, Backend = `backend/requirements.txt` (요약: `CLAUDE.md` Key Technical Details / `README.md` 기술 스택). 본 표는 반쯤-갱신된 버전 핀으로 만성 드리프트를 겪어(백엔드가 레거시 Ktor 표로 남아있던 이력) 2026-07-10 버전 열을 제거하고 역할 명세만 남김.

### Android (클라이언트)

| 기술 | 용도 |
| ------ | ------ |
| Kotlin | 언어 |
| Jetpack Compose (BOM) + Material 3 | UI 프레임워크 |
| Hilt | 의존성 주입 |
| Room + WorkManager | 로컬 데이터베이스 (오프라인 캐시) 및 백그라운드 동기화 |
| CameraX + ML Kit Face Detection | 온디바이스 전면 카메라 미소 검출 (무저장·무전송) |
| Retrofit + OkHttp | 백엔드 API 통신 |
| Coil 3 | 이미지/GIF 로딩 |
| DataStore | 설정 영속화 (다크모드) |
| MSAL Android | 인증 (Entra External ID) |
| Health Connect | 활동 자동 추적 |
| Sentry Android SDK | 크래시/에러 모니터링 |
| Navigation Compose | 화면 전환 |
| Vico (compose-m3) | 통계·목표 차트 |

### Backend (서버)

| 기술 | 용도 |
| ------ | ------ |
| FastAPI (Python 3.14) + uvicorn | HTTP 서버 |
| SQLAlchemy 2.0 async + asyncpg | ORM / 데이터베이스 쿼리 |
| Alembic | 스키마 마이그레이션 |
| PyJWT (JWKS, RS256) | 인증 토큰 검증 |
| OpenAI Python SDK (`AsyncOpenAI`) + Tenacity | AI 식단 생성 (Azure AI Foundry DeepSeek-V3.2) |
| Sentry Python SDK | 서버 에러 모니터링 |

### 빌드 도구

AGP · Gradle · KSP · Sentry Gradle Plugin · Detekt · Spotless — 버전은 `gradle/libs.versions.toml` 참조.

### 인프라

| 기술 | 용도 |
| ------ | ------ |
| Azure Container Apps | 백엔드 호스팅 |
| Azure Container Registry | Docker 이미지 저장 (RBAC `AcrPull`) |
| Azure PostgreSQL Flexible Server | 데이터베이스 |
| Azure AI Foundry (Serverless MaaS) | DeepSeek-V3.2 식단 생성 모델 |
| Azure Key Vault | 운영 시크릿 단일 출처 (Managed Identity 참조) |
| Sentry.io | 에러 모니터링 대시보드 |
| Docker | 컨테이너화 배포 |

---

## 화면 구성

### 1. Splash 화면 (`SplashScreen`)

- 앱 시작 시 표시
- 저장된 세션 확인 후 자동 라우팅
  - 세션 있음 + 프로필 있음 → Home
  - 세션 있음 + 프로필 없음 → Onboarding
  - 세션 없음 → Login

> ⚠️ **아래 §2·§3 은 v1.0(초기 설계) 시점 기록이며 현행이 아니다.** 원문을 보존하고 현행을
> 병기한다 — 머리말의 "본문은 그대로 보존" 계약을 지키기 위해서다(2026-09-02 정정: 한 번은
> 이 두 절을 현행 내용으로 덮어써 초기 설계 기록이 사라졌었다).
>
> **현행 (2026-09, Entra 브라우저 위임)**: 두 화면 모두 **삭제**됐다. `LoginScreen` ·
> `SignupScreen` · `ForgotPasswordScreen` + ViewModel 3종이 폐기되고 **`AuthGateScreen` 하나**만
> 남았다(ui/auth 959→283줄). 이메일/비밀번호 입력란이 앱에 없다 — CTA 를 누르면 브라우저
> (Custom Tab)로 나가 Entra 호스팅 페이지에서 로그인·가입·비밀번호 재설정을 모두 처리하고
> 돌아온다(Authorization Code + PKCE). 실패 표시는 룰 8 준수(`AuthErrorBanner` inline +
> persistent + `liveRegion` + Sentry breadcrumb). 상태는 `Authenticating` 하나로 합쳤다 —
> MSAL 이 "Custom Tab 표시됨" 콜백을 주지 않아 Launching/AwaitingReturn 을 나눌 근거가
> 런타임에 없고 두 상태의 UI 도 같다(나누면 죽은 상태가 생긴다).
>
> 현행 화면 목록의 정본은 `CLAUDE.md` 의 UI 절이다.

### 2. 로그인 화면 (`LoginScreen`)

- 이메일/비밀번호 입력
- 로그인 버튼
- 회원가입 화면 이동 링크
- 에러 메시지 표시 (SnackBar)

### 3. 회원가입 화면 (`SignupScreen`)

- 이메일/비밀번호 입력
- 가입 요청 성공 시 Supabase가 확인 메일을 발송하며, 화면은 `AwaitingEmailConfirmation` 안내 상태로 전환됨
- 안내 상태에서 60초 쿨다운으로 확인 메일을 재전송 가능 (남은 시간을 버튼에 노출)
- 메일 인증 완료 후 사용자는 직접 Login 화면으로 이동해 로그인하며, 이메일 입력란은 이전 가입 입력값으로 자동 채워짐

### 4. 온보딩 화면 (`OnboardingScreen`)

- 신체 정보 입력 (키, 몸무게, 체지방률, 골격근량)
- Slider + 키보드 하이브리드 입력
- 범위 초과 시 빨간색 에러 표시 + 안내 메시지
- 입력 요약 카드 (등록 전 확인)
- "신체정보 저장하고 시작하기" 버튼 → 프로필 저장 → Home 이동 (현재 주 운동 환경 선택 후 계획 생성)

### 5. 홈 화면 (`HomeScreen`)

- **현재 주 계획 없음(`NoPlan`)**: 운동 환경 선택 카드(`헬스장` / `홈트레이닝`, `Role.RadioButton` + `selectableGroup`) 및 `"이번 주 계획 만들기"` CTA 표시. 프로필에 기본 운동 환경이 설정되어 있으면 주당 1회 자동 생성(`AutoCreating`) 수행
- **주간 진행률 카드**: 완료율 프로그레스 바 + 퍼센트 표시 + 운동 환경 배지(`헬스장` / `홈트레이닝`)
- **Health Connect 연동 배너**: 미연동 시 표시, "연동" 버튼
- **요일별 운동 카드 (DayPlanCard)**:
  - 탭하여 운동 완료/미완료 토글 (Optimistic update)
  - 완료 시 primaryContainer 배경색 + CheckCircle 아이콘
  - 운동명 탭 시 운동 상세 화면 이동 (`REPS`는 `세트×횟수`, `SECONDS`는 `세트×초` 표기)
  - 휴식일 표시
- **스켈레톤 UI**: 로딩 중 shimmer 애니메이션 카드
- **TopAppBar 액션**:
  - 테마 토글 (SYSTEM → DARK → LIGHT 순환)
  - 새로고침
  - 히스토리
  - 배지
  - 로그아웃

### 6. 운동 상세 화면 (`WorkoutDetailScreen`)

- 운동 GIF 애니메이션 (SubcomposeAsyncImage, 로딩/에러 상태; `gifUrl`이 비어 있는 홈트레이닝 내장 운동은 깨진 이미지 아이콘 대신 텍스트/단계 안내 카드를 표시)
- 운동명, 부위, 장비
- 세트 x 횟수 (또는 세트 x 유지 시간(초))
- 운동 방법 (순서 목록)

### 7. 운동 기록 히스토리 (`HistoryScreen`)

- 과거 주간 계획 목록 (무한 스크롤 페이지네이션)
- 주별 카드: 날짜 범위, 운동 환경 배지(`헬스장` / `홈트레이닝`), 완료율 프로그레스 바, 요일별 체크 아이콘

### 8. 배지 화면 (`BadgeScreen`)

- 전체 배지 목록 (카탈로그 기반)
- 획득 배지: 트로피 아이콘, primaryContainer 배경, 획득 날짜
- 미획득 배지: 잠금 아이콘, surfaceVariant 배경

---

## 핵심 기능 상세

### 인증 (2026-09 전환 반영)

- **Microsoft Entra External ID** — 브라우저 위임(Authorization Code + PKCE, MSAL Custom Tab)
- 앱에는 인증 화면이 `AuthGateScreen` **하나**뿐이다. 이메일/비밀번호 입력·가입·이메일 검증·비밀번호 재설정은 전부 Entra 호스팅 페이지에서 처리된다
- 세션 저장/복원·토큰 갱신은 MSAL 계정 캐시가 담당 (`acquireTokenSilent`)
- `AuthRepository.restoreSession()`: 무음 갱신 성공 시 userId 반환 + tokenHolder 설정
- OkHttp TokenAuthenticator 로 401 응답 시 토큰 자동 갱신 후 재시도 (**전환 시 무수정** — `SessionRefresher` seam 이 IdP 교체를 흡수)
- ViewModel 은 `AuthRepository` 인터페이스만 의존 (MSAL 클라이언트 직접 참조 없음)
- userId = 액세스 토큰의 **`oid` claim** (`sub` 아님 — `sub` 는 앱마다 다른 pairwise 값이라 Graph 계정 삭제가 매칭되지 않는다)

> 아래 "회원가입 이메일 확인 흐름"·"App Links 자동 로그인"·"Supabase 사용 범위" 는 **전환 이전 설계 기록**이다. 현재는 검증 코드를 브라우저에서 입력받으므로 App Links·`/auth/confirm` 경로가 존재하지 않는다.

#### 회원가입 이메일 확인 흐름

- 회원가입 요청이 성공하면 Supabase가 확인 메일을 발송하고, 사용자의 세션은 메일 인증을 마친 후에만 활성화된다. 가입 직후에는 인증 토큰을 발급하지 않으며, 앱은 자동 로그인 상태로 진입하지 않는다.
- 가입 직후 SignupScreen은 `AwaitingEmailConfirmation` 상태로 전환되어 메일 발송 안내와 재전송 버튼을 노출한다. 재전송은 60초 쿨다운으로 제한하며, 쿨다운 동안 버튼은 비활성화되고 남은 시간을 함께 표시한다.
- 메일 인증을 마친 사용자는 Login 화면으로 이동해 수동으로 로그인한다. 이때 이메일 입력란은 직전 회원가입에서 입력한 이메일로 자동 채워져 재입력 부담을 줄인다.
- 미인증 상태에서 로그인을 시도하면 `EmailNotConfirmed` 에러를 한국어 메시지로 표시하고, 동일한 화면에 inline "메일 재전송" 액션을 노출해 즉시 확인 메일을 다시 받을 수 있게 한다.

##### App Links 자동 로그인 (v0.1.3+)

- Supabase Confirm Email 메일 링크가 Android App Links 로 verify 된 도메인을 사용하므로, 같은 디바이스에서 클릭 시 앱이 자동으로 열리고 PKCE code 교환을 거쳐 자동 로그인까지 완료된다 (Onboarding/Home 직진).
- App Links 검증은 백엔드(FastAPI) 가 `https://<APP_LINKS_HOST>/.well-known/assetlinks.json` 으로 제공한다. assetlinks.json 에는 debug + release 빌드의 SHA256 fingerprint 가 둘 다 포함된다.
- 앱 미설치 디바이스에서는 `https://<APP_LINKS_HOST>/auth/confirm` 가 안내 + Google Play 링크 정적 HTML 을 반환한다.
- deep link 처리 실패(만료/재사용/네트워크) 시 사용자는 Login 화면으로 이동하며 한국어 스낵바로 에러 메시지를 안내받는다. 이미 로그인된 사용자가 옛 링크를 클릭하면 현재 세션을 유지하고 deep link 를 무시한다.

### 주간 운동 계획 생성

- **헬스장(`GYM`) 모드**: ExerciseDB API에서 부위별 운동 조회 (chest, back, upper legs, shoulders, upper arms, lower legs, cardio) + 주 시작일(월요일) 기준 결정론적 셔플 및 직전 주 중복 후순위 배치
- **홈트레이닝(`HOME`) 모드**: 외부 네트워크 호출 없이 앱 내장 18종 무장비 카탈로그(`HomeWeeklyPlanGenerator`, `local:home:<slug>:v2.0`)에서 PUSH/PULL/LEGS/CARDIO/MIXED 7일 계획을 결정론적으로 생성 (`forearm-plank`는 `SECONDS` 단위 45초 유지)
- 근력 운동: 월/수/금 (4종목)
- 유산소 운동: 화/목 (2종목)
- 혼합 운동: 토 (근력 2 + 유산소 1)
- 휴식: 사용자 지정 휴식일 (기본 일요일)

### 운동 완료 추적

- **수동 체크**: DayPlanCard 탭 → PATCH API → Optimistic update
- **자동 감지**: Health Connect ExerciseSessionRecord 읽기 → 해당 날짜 완료 처리
- 서버 동기화: Health Connect 감지 결과를 백엔드에 PATCH

### 배지 시스템

- `week_1_complete`: 첫 주 목표 달성
- `week_2_complete`: 2주 연속 달성
- `streak_3weeks`: 3주 연속 달성
- 운동 계획 로드 시 자동 체크 및 배지 수여

### 다크모드

- DataStore로 SYSTEM/DARK/LIGHT 설정 영속화
- Material3 Dynamic Color 지원 (Android 12+)
- 커스텀 Light/Dark ColorScheme 폴백

---

## Backend API

> 아래 표는 v1.0 초기 설계 기록이다. 현행 엔드포인트(총 27개 = JWT 필요 22 + 공개 5, `/v2/weekly-plan` 및 `/app-compatibility` 포함)의 정본은 `CLAUDE.md` 및 `docs/PRD.md` §5 참조.

| Method | Path | 설명 | 인증 |
| -------- | ------ | ------ | ------ |
| `GET` | `/health` | 헬스 체크 (DB 연결 검증) | X |
| `GET` | `/profile` | 사용자 프로필 조회 | O |
| `PUT` | `/profile` | 프로필 생성/수정 | O |
| `GET` | `/v2/weekly-plan?weekStart=` | 주간 계획 조회 | O |
| `POST` | `/v2/weekly-plan` | 주간 계획 생성(중복 시 409) | O |
| `DELETE` | `/v2/weekly-plan?weekStart=` | 손상 계획 초기화 | O |
| `PATCH` | `/v2/weekly-plan/complete` | 운동 완료 토글 | O |
| `GET` | `/v2/weekly-plan/history?page=&size=` | 히스토리 페이지네이션 | O |
| `GET` | `/badges` | 획득 배지 목록 | O |
| `POST` | `/badges/{key}` | 배지 수여 | O |

---

## 프로젝트 구조

```
app/src/main/java/com/gunnys/eundunhealth/
├── EundunHealthApplication.kt    # Application (Hilt + Sentry 초기화)
├── MainActivity.kt               # Activity (테마 + Health Connect 권한)
├── data/
│   ├── auth/AuthRepositoryImpl.kt
│   ├── healthconnect/HealthConnectDataSource.kt
│   ├── local/                     # Room (dao, entity, database)
│   ├── preferences/ThemePreferences.kt
│   ├── remote/
│   │   ├── api/                   # EundunApi, ApiDtos, PlanJsonModels
│   │   ├── exercisedb/            # ExerciseDB API
│   │   └── interceptor/           # RetryInterceptor, TokenAuthenticator
│   └── repository/                # *RepositoryImpl
├── di/                            # Hilt Modules
├── domain/
│   ├── model/                     # WeeklyPlan, DayPlan, Exercise, Badge, UserProfile
│   ├── repository/                # Interfaces
│   └── usecase/                   # LoadCurrentWeekPlan, CreateWeeklyPlan, HomeWeeklyPlanGenerator, SyncHealth, CheckBadges
└── ui/
    ├── auth/                      # Login, Signup, AuthViewModel
    ├── badge/                     # BadgeScreen, BadgeViewModel
    ├── components/SkeletonUi.kt
    ├── history/                   # HistoryScreen, HistoryViewModel
    ├── home/                      # HomeScreen, HomeViewModel
    ├── navigation/                # AppNavigation, Screen
    ├── onboarding/                # OnboardingScreen, OnboardingViewModel
    ├── splash/SplashScreen.kt
    ├── theme/                     # Color, Type, Theme
    └── workout/                   # WorkoutDetailScreen, WorkoutDetailViewModel

backend/src/main/kotlin/com/gunnys/eundunhealth/
├── Application.kt                 # Ktor 진입점 (Sentry 초기화)
├── config/AppConfig.kt            # 환경변수 중앙화
├── db/
│   ├── DatabaseFactory.kt         # HikariCP + Exposed
│   └── tables/                    # UserProfilesTable, WeeklyPlansTable, BadgesTable
├── models/Dtos.kt                 # 요청/응답 DTO
├── plugins/                       # Routing, Security, Serialization
└── routes/                        # ProfileRoutes, WeeklyPlanRoutes, BadgeRoutes
```

---

## 네트워크 보안

- `network_security_config.xml`로 네트워크 정책 관리
- `base-config cleartextTrafficPermitted="false"` — HTTP cleartext 기본 차단
- localhost/10.0.2.2 cleartext는 개발용으로만 허용
- Release 빌드에서 HTTP 로깅 비활성화 (`HttpLoggingInterceptor.Level.NONE`)
- OkHttp RetryInterceptor: 최대 3회 재시도, exponential backoff (500ms/1s/2s) — Backend + ExerciseDB 양쪽 적용
- OkHttp TokenAuthenticator: 401 응답 시 토큰 자동 갱신 후 재시도 (IdP 무관 — `SessionRefresher` seam)
- 연결/읽기 타임아웃: 15초 (Backend + ExerciseDB 양쪽 적용)
- 에러 추적: `android.util.Log` 대신 `Sentry.captureException()` 사용 (프로덕션 모니터링)

---

## 모니터링

### Sentry

- **Android**: 크래시, ANR, 네트워크 에러 자동 캡처
- **Backend**: 500 에러, unhandled exception 자동 캡처
- **OkHttp 트레이싱**: sentry-okhttp 모듈로 API 호출 성능 모니터링
- **ProGuard 매핑**: Release 빌드 시 자동 업로드 → 난독화 스택 트레이스 복원
- **환경 구분**: development (debug) / production (release)
- **샘플 레이트**: dev 100%, prod 20%
- **Sentry org/project**: gunnys / eundunhealth

---

## 외부 서비스

| 서비스 | 용도 | 키 위치 |
| -------- | ------ | --------- |
| Microsoft Entra External ID | 인증 | `app/src/{debug,release}/res/raw/auth_config_ciam.json` (client_id·authority) + `local.properties` → ENTRA_API_SCOPE |
| ExerciseDB (RapidAPI) | 운동 데이터 조회 | `local.properties` → EXERCISEDB_API_KEY |
| Sentry.io | 에러 모니터링 | `local.properties` → SENTRY_DSN, SENTRY_AUTH_TOKEN |
| Azure Container Apps | 백엔드 호스팅 | Azure CLI 인증 |
| Azure PostgreSQL | 데이터 저장 | 환경변수 → AZURE_DB_URL/USER/PASSWORD |
