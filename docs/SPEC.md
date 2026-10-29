# eundunHealth 기능 명세서

> **문서 버전:** v2.1 (현행 아키텍처 정본, 2026-10-29 최신화)  
> **현재 제품 상태:** 최신 버전·구현 상태의 단일 출처(SSoT)는 [ops/operations-snapshot.md](./ops/operations-snapshot.md) + [CHANGELOG.md](./CHANGELOG.md) — 본 명세 작성 이후 v0.1.x 다수 릴리스를 거쳐 **v0.7.0 (스마일 트레이닝 v2 및 PostgreSQL 18.4 하드 리셋)**, **v0.7.1 (콜드 스타트 크래시 핫픽스)** 및 **v0.7.2 (진행·프로필 허브 비주얼 대시보드 개편 및 AI 식단 `gpt-5.4-mini` 100K TPM 전환)** 프로덕션 배포 완료. 자세한 기술 아키텍처는 [TRD.md](./TRD.md) 참조.
>
> **인증 제공자 (2026-09 전환):** Supabase Auth → **Microsoft Entra External ID**(외부 테넌트 `eundunhealthciam`, 브라우저 위임). IdP는 JWT 발급(RS256/JWKS) + 가입·이메일 검증·비밀번호 재설정 페이지만 담당하며, 모든 비즈니스 데이터는 Azure PostgreSQL(`healthapp`)에 저장한다(범위는 전환 전후 동일). **아래 인증 관련 본문(로그인/회원가입 화면, 이메일 확인 흐름, App Links)은 전환 이전 설계 기록이며** — 현행은 `AuthGateScreen` 단일화 및 Microsoft Graph API `DELETE /users/{oid}` 계정 탈퇴 파이프라인(`DELETE /account`)으로 완전 일원화되었다.
> 
> **AI 식단 생성 및 운동 한국어 현지화 (2026-09~10, v0.3.0~v0.3.1 / v0.7.2):** 주간 식단 자동 생성 기능은 Azure AI Foundry(`gpt-5.4-mini` `GlobalStandard` 100K TPM — v0.3.0 초기 `DeepSeek-V3.2`에서 v0.7.2 업그레이드)를 통해 구현되었다. 프론트엔드는 `MealPlanScreen` Compose UI와 Hilt+Retrofit(전용 130s read timeout) 통신을 사용하고, 백엔드는 FastAPI에서 `AsyncOpenAI` + OpenAI Strict JSON Schema(`json_schema`, `strict: True`)·Pydantic JSON 검증·`X-AI-Data-Processing-Consent: true` 국외 이전 사전 동의 헤더 강제 및 `MEAL_AI_ENABLED` 킬스위치를 채택했다(v0.3.1 / v0.7.2). 운동 카탈로그 46종(이름·수행 방법·팁) 및 근육/부위/장비 라벨도 한국어로 전면 현지화되었다(`ExerciseLocalization`).
>
> **적응형 메뉴트리 및 온디바이스 스마일 트레이닝 v2 (2026-10, v0.4.0~v0.5.0 / v0.7.0):** 로그인 후 탐색은 `오늘·식단·진행·프로필` 4개 최상위 목적지(`MainAppShell`, `ProgressHubScreen`, `ProfileHubScreen`)로 개편되었다(v0.4.0). `오늘` 탭(상단 퀵 액션 칩·스마트 코칭 카드), `운동 가이드` 완료 요약 화면, `진행` 허브 최상단에서 진입하는 **스마일 트레이닝 v2**(`SmileTrainingScreen`, v0.5.0 / v0.7.0)는 전면 카메라(`CameraX` 1.6.2)와 온디바이스 `ML Kit Face Detection`(17.1.0)으로 영상·사진을 저장하거나 전송하지 않고 기기 메모리에서만 단일 표준 규격(`2.0초 미소 유지 × 3회 반복`, 1.0초 얼굴 정렬 자동 시작)의 실시간 미소 근육 운동을 코칭하며, 오프라인 우선 Room(`SmileTrainingDatabase` v2) + `WorkManager`(`SmileSyncWorker`)와 백엔드 `smile_training_sessions` 테이블(`/v2/smile-training/*` 4개 API)로 세션 요약 및 연속일 통계를 동기화한다.
>
> **적응형 주간 운동 계획 v2 및 휴식 요일 자유 다중 지정 (`restDays`, 2026-10-27, v0.6.0):** 주간 운동 계획이 `/v2/weekly-plan` 계약과 `planMode`(`gym` / `home`) 필수 스키마, 그리고 프로필 다중 휴식 요일 `restDays`(`INTEGER[]`, 주 1~6일 자유 선택, Zero-Legacy 전환)로 전환되었다. 사용자는 매주 월요일(KST) 첫 진입 시 `헬스장`(ExerciseDB 기반) 또는 `홈트레이닝`(앱 내장 18종 무장비 카탈로그 `local:home:<slug>:v2.0`, `prescriptionUnit = REPS | SECONDS`) 환경을 직접 선택해 계획을 생성하거나, 프로필 허브의 `기본 운동 환경`(`PUT /v2/profile/default-workout-plan-mode`)을 설정해 주간 자동 생성을 사용할 수 있다. 비휴식일(1~6일)에는 대근육 우선순위 큐(`[PUSH → LEGS → PULL → CARDIO → MIXED → CARDIO]`)가 순환 배치되며, 주중 계획은 불변(동일 주 재생성 시 409 `weekly_plan_exists`)이고 손상된 계획 복구만 명시적 `DELETE /v2/weekly-plan`으로 허용한다.
>
> **UI/UX 전면 리뉴얼 및 브랜드 시스템 (2026-10-28, v0.6.1, `PROP-05`):** Material 3 테마를 기반으로 한 **Modern Forest & Mint (36슬롯)** 시맨틱 컬러 팔레트(`EundunExtendedColors`), Pretendard Variable 가변 폰트, `TodayHeroCard`, 단계별 운동 가이드, 하이브리드 바디 피커, 12개 UDF ViewModel 및 DayNight 테마 전환 깜빡임 방지가 적용되었다.
>
> **PostgreSQL 18 하드 리셋 & 스키마 라이프사이클 현대화 (2026-10-29, v0.7.0 / v0.7.1):** Azure PostgreSQL 18.4 Flexible Server 도입과 함께 과거 Alembic 마이그레이션 체계를 완전 폐기하고 **Drop & Recreate 전용 하드 리셋 파이프라인(`02_schema_create_all.sql`, `DEFAULT uuidv7()` PK)** 및 `python -m app.schema_bootstrap` 멱등 부트스트랩으로 전환하여 Zero-Legacy 스키마 거버넌스를 확립했다.

## 프로젝트 개요

**은둔헬스(eundunHealth)** 는 사용자의 신체 정보와 운동 환경(`헬스장` / `홈트레이닝`)을 기반으로 맞춤 주간 운동 계획과 AI 주간 식단 가이드를 생성하고, 운동 달성을 추적하는 Android 앱입니다.

- **패키지**: `com.gunnys.eundunhealth`
- **최소 SDK**: 26 (Android 8.0 Oreo)
- **대상 SDK**: 37 (Android 17)
- **백엔드**: FastAPI (Python 3.14) — 비동기 고성능 REST API
- **운동 데이터**: OSS ExerciseDB(`oss.exercisedb.dev`, 무인증) + 앱 내 46종 한국어 현지화 사전(`ExerciseLocalization`) + 홈트레이닝 내장 18종 무장비 카탈로그(`HomeWeeklyPlanGenerator`).
- **AI 식단**: Azure AI Foundry (`gpt-5.4-mini`, Korea Central `hub-eundunhealth-prod`, `GlobalStandard` 100K TPM).
- **인증**: Microsoft Entra External ID(외부 테넌트 `eundunhealthciam`, Asia Pacific, JWKS RS256).

---

## 기술 스택

> 버전 단일 진실 공급원(SSoT): Android = `gradle/libs.versions.toml`, Backend = `backend/requirements.txt` (요약: `CLAUDE.md` Key Technical Details / `README.md` 기술 스택).

### Android (클라이언트)

| 기술 | 용도 |
| ------ | ------ |
| Kotlin (2.4.21 내장) | 언어 |
| Jetpack Compose (BOM) + Material 3 | UI 프레임워크 (Modern Forest & Mint 36슬롯 + Pretendard Variable) |
| Hilt | 의존성 주입 |
| Room + WorkManager | 로컬 데이터베이스 (오프라인 캐시) 및 백그라운드 동기화 |
| CameraX + ML Kit Face Detection | 온디바이스 전면 카메라 미소 검출 (무저장·무전송) |
| Retrofit + OkHttp | 백엔드 API 통신 |
| Coil 3 | 이미지/GIF 로딩 |
| DataStore | 설정 영속화 (테마/리셋 플래그) |
| MSAL Android | 인증 (Entra External ID OIDC/PKCE 브라우저 위임) |
| Health Connect (1.1.0) | 활동 자동 추적 (운동 세션 읽기 단독) |
| Sentry Android SDK | 크래시/ANR/네트워크 분산 추적 모니터링 |
| Navigation Compose | 최상위 4대 목적지 라우팅 |
| Vico (compose-m3) | 통계·목표 차트 시각화 |

### Backend (서버)

| 기술 | 용도 |
| ------ | ------ |
| FastAPI 0.161.0 (Python 3.14) + uvicorn | 비동기 고성능 HTTP 서버 |
| SQLAlchemy 2.0 async + asyncpg 0.32.0 | 비동기 ORM 및 연결 풀 관리 |
| PostgreSQL 18 DDL (`02_schema_create_all.sql`) | 엄격한 3NF 9개 도메인 테이블 생성 (Zero-Legacy Drop & Recreate) |
| `python -m app.schema_bootstrap` | 기동 시 멱등 스키마 테이블 검증 |
| PyJWT (JWKS, RS256) | Entra CIAM 액세스 토큰 서명/클레임(`oid`, `scp`) 검증 |
| OpenAI Python SDK (`AsyncOpenAI`) + Tenacity | AI 식단 생성 (Azure AI Foundry gpt-5.4-mini (GlobalStandard)) |
| Sentry Python SDK (2.73.0) | 서버 예외 및 분산 추적 모니터링 |

### 빌드 도구

AGP 9.4.1 · Gradle 9.8.2 · KSP 2.0.21-1.0.28 · Sentry Gradle Plugin · Detekt 1.23.8 · Spotless 8.10.4 — 버전은 `gradle/libs.versions.toml` 참조.

### 인프라

| 기술 | 용도 |
| ------ | ------ |
| Azure Container Apps | 백엔드 API 컨테이너 호스팅 (`eundunhealth-api`) |
| Azure Container Apps Job | 고아 계정 청소 주간 안전망 크론 (`eundunhealth-reaper`) |
| Azure Container Registry | Docker 이미지 저장소 (RBAC `AcrPull`) |
| Azure PostgreSQL Flexible Server | 데이터베이스 엔진 (PostgreSQL 18.4, Korea Central) |
| Azure AI Foundry (Serverless MaaS) | gpt-5.4-mini 식단 생성 모델 (GlobalStandard 100K TPM) |
| Azure Key Vault | 운영 시크릿 8종 중앙 집중 관리 (Managed Identity 참조) |
| Sentry.io | 풀스택 에러 모니터링 대시보드 (`eundunhealth` / `eundunhealth-backend`) |
| Docker | 컨테이너화 빌드 및 배포 |

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

> **현재 백엔드 엔드포인트 SSoT:** 총 27개 (인증 필요 22개 + 공개 5개).  
> 모든 인증 API는 `Authorization: Bearer <Entra_CIAM_JWT>` 헤더와 `scp`=`access_as_user` 클레임을 필수로 검증합니다.

| 분류 | Method | Path | 인증 | 설명 및 주요 파라미터 |
| :--- | :--- | :--- | :---: | :--- |
| **System** | `GET` | `/health` | X | 라이브니스 프로브 (DB Ping 검증, `status: healthy`) |
| | `GET` | `/health/ready` | X | 레디니스 프로브 (DB 및 필수 환경 설정 점검) |
| | `GET` | `/app-compatibility?version_code=` | X | 앱 버전 호환성 체크 (`minimumSupportedVersionCode: 41`) |
| **Profile** | `GET` | `/profile` | **O** | 현재 사용자 프로필 및 다중 휴식 요일(`rest_days`) 조회 |
| | `PUT` | `/profile` | **O** | 신체 정보 갱신 (키·몸무게·체지방률·골격근량, 시계열 이력 자동 기록) |
| | `GET` | `/profile/history?limit=` | **O** | 신체 스펙 변경 시계열 히스토리 조회 (최대 50건) |
| | `PUT` | `/v2/profile/default-workout-plan-mode` | **O** | 기본 주간 생성 환경 설정 (`gym` 또는 `home`) |
| **Weekly Plan** | `GET` | `/v2/weekly-plan?week_start=` | **O** | 특정 주간 계획 조회 (`gym`/`home` 모드, 다중 휴식일 반영) |
| | `POST` | `/v2/weekly-plan` | **O** | 주간 계획 생성 (동일 주 중복 시 `409 weekly_plan_exists` 불변성 강제) |
| | `DELETE` | `/v2/weekly-plan?week_start=` | **O** | 손상된 계획 명시적 삭제 (`204 No Content`, 복구 지원) |
| | `PATCH` | `/v2/weekly-plan/complete` | **O** | 개별 운동 항목 완료 상태 토글 (Optimistic UI 반영) |
| | `GET` | `/v2/weekly-plan/history?page=&size=` | **O** | 과거 주간 계획 히스토리 페이지네이션 조회 |
| | `GET` | `/v2/weekly-plan/previous` | **O** | 직전 주 계획 조회 (운동 편중 방지 및 후순위 배치용) |
| | `GET` | `/v2/weekly-plan/statistics` | **O** | 주간 운동 달성률 및 연속 스트릭(Streak) 통계 |
| **Smile Training** | `POST` | `/v2/smile-training/sessions` | **O** | 온디바이스 스마일 세션 단일/배치 등록 (2초×3회 표준 규격, 멱등 보장) |
| | `GET` | `/v2/smile-training/sessions?limit=` | **O** | 최근 스마일 트레이닝 세션 이력 조회 |
| | `GET` | `/v2/smile-training/statistics` | **O** | 스마일 트레이닝 연속 훈련일(Streak) 및 누적 통계 |
| | `DELETE` | `/v2/smile-training/sessions` | **O** | 스마일 트레이닝 세션 전체 이력 초기화 (`204 No Content`) |
| **Meal Plan** | `POST` | `/meals/generate` | **O** | Azure AI Foundry `gpt-5.4-mini` 주간 맞춤 식단 생성 (사전 동의 필수) |
| | `GET` | `/meals/current` | **O** | 현재 주의 AI 생성 식단 조회 |
| **Badges & Goals** | `GET` | `/badges` | **O** | 획득 배지 목록 및 해금 일시 조회 |
| | `POST` | `/badges/{key}` | **O** | 배지 수여 (9종 정규 배지 키 화이트리스트 검증, 중복 수여 방지) |
| | `GET` | `/goals` | **O** | 목표 체중 및 체지방률 조회 |
| | `PUT` | `/goals` | **O** | 목표 체중/체지방률 생성 및 갱신 |
| **Account** | `DELETE` | `/account` | **O** | **회원 탈퇴**: DB 도메인 데이터 즉시 CASCADE 영구 삭제 + Graph API 계정 영구 파기 (`204 No Content`) |
| **Legal** | `GET` | `/privacy` | X | 개인정보 처리방침 공개 웹페이지 (HTML 렌더링) |
| | `GET` | `/account-deletion` | X | 구글 플레이 요구 계정 삭제 안내 페이지 |

<details>
<summary>📜 초기 v1.0 API 설계 기록 (아카이브)</summary>

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

</details>

---

## 프로젝트 구조

### Android (`com.gunnys.eundunhealth`)

```text
app/src/main/java/com/gunnys/eundunhealth/
├── EundunHealthApplication.kt         # Application (Hilt + Sentry 초기화)
├── MainActivity.kt                    # Single Activity (Modern Forest & Mint 테마, Health Connect 권한)
├── data/
│   ├── auth/AuthRepositoryImpl.kt     # MSAL Android OIDC 브라우저 위임 세션 및 토큰 관리
│   ├── healthconnect/                 # Health Connect 1.1.0 운동 세션 감지 및 데이터 소스
│   ├── local/                         # Room DB (eundunhealth.db v3, smile_training.db v2)
│   ├── preferences/                   # DataStore Preferences (테마, 리셋 플래그)
│   ├── remote/                        # Retrofit2 + OkHttp3 (EundunApi, Interceptor, DTO)
│   ├── sync/SmileSyncWorker.kt        # WorkManager 기반 스마일 세션 백그라운드 동기화
│   └── repository/                    # Clean Architecture 도메인 Repository 구현체
├── di/                                # Hilt Modules (NetworkModule, DatabaseModule, RepositoryModule)
├── domain/
│   ├── model/                         # WeeklyPlan, DayPlan, Exercise, SmileTraining, UserProfile
│   ├── repository/                    # 도메인 인터페이스 계약
│   └── usecase/                       # HomeWeeklyPlanGenerator, CheckBadges, SyncSmileSessions
└── ui/
    ├── auth/AuthGateScreen.kt         # 단일 인증 관문 (브라우저 위임, 인라인 에러 배너)
    ├── main/MainAppShell.kt           # 최상위 4대 탭 바텀 네비게이션 셸 (오늘/식단/진행/프로필)
    ├── today/                         # 오늘 탭 (TodayHeroCard, 퀵 액션, 스마트 코칭)
    ├── workout/                       # 운동 상세 및 단계별 운동 가이드 모드
    ├── meal/                          # 식단 탭 (AI 맞춤 주간 식단 생성 및 조회)
    ├── progress/                      # 진행 탭 (12주 통계, Vico 차트, 배지 컬렉션, 스마일 진입)
    ├── smile/SmileTrainingScreen.kt   # 온디바이스 스마일 트레이닝 v2 (CameraX + ML Kit)
    ├── profile/                       # 프로필 탭 (신체 정보 수정, 휴식 요일, 환경 설정)
    ├── onboarding/                    # 온보딩 신체 정보 하이브리드 입력 피커
    ├── theme/                         # Modern Forest & Mint 36슬롯, Pretendard Variable 가변 폰트
    └── navigation/                    # Compose Navigation 4대 목적지 라우트 계약
```

### Backend (`backend/app`)

```text
backend/
├── Dockerfile                         # Python 3.14-slim 기반 경량 컨테이너
├── requirements.txt                   # pip-compile SHA-256 해시 락 무결성 보장
├── schema/
│   ├── 01_cleanup_drop_all.sql        # 하드 리셋 전용 스키마 정리 스크립트
│   └── 02_schema_create_all.sql       # 3NF 정규화 9개 테이블 (DEFAULT uuidv7() PK)
└── app/
    ├── main.py                        # FastAPI 앱 진입점 (Lifespan, CORS, Sentry 미들웨어)
    ├── config.py                      # Pydantic BaseSettings 환경변수 단일 진실 공급원
    ├── database.py                    # SQLAlchemy 2.0 비동기 엔진 및 asyncpg 세션 풀 (3+2)
    ├── dependencies.py                # Entra CIAM JWT 서명/클레임(oid, scp) 검증 의존성
    ├── schema_bootstrap.py            # 기동 시 멱등 스키마 테이블 검증 모듈
    ├── models/                        # SQLAlchemy 2.0 9개 도메인 ORM 모델
    ├── schemas/                       # Pydantic v2 직렬화 및 엄격한 입력값 검증 DTO
    ├── services/                      # 비즈니스 서비스 (Account, Plan, Meal, Smile, Badge)
    └── routers/                       # 8개 도메인 APIRouter (총 27개 엔드포인트)
        ├── account.py                 # DELETE /account (회원 탈퇴 & Graph 영구 파기)
        ├── system.py                  # /health, /health/ready, /app-compatibility
        ├── profile.py                 # /profile, /profile/history, default-workout-plan-mode
        ├── weekly_plan_v2.py          # /v2/weekly-plan/* (생성, 조회, 토글, 통계, 삭제)
        ├── smile_training.py          # /v2/smile-training/* (세션 등록, 조회, 통계, 초기화)
        ├── meals.py                   # /meals/* (AI 식단 생성 및 조회)
        ├── badges.py                  # /badges/* (배지 조회 및 수여)
        ├── goals.py                   # /goals (목표 체중/체지방률 관리)
        └── legal.py                   # /privacy, /account-deletion
```

---

## 네트워크 보안

- `network_security_config.xml`로 네트워크 정책 관리
- `base-config cleartextTrafficPermitted="false"` — HTTP cleartext 기본 차단
- localhost/10.0.2.2 cleartext는 개발용으로만 허용
- Release 빌드에서 HTTP 로깅 비활성화 (`HttpLoggingInterceptor.Level.NONE`)
- OkHttp RetryInterceptor: 최대 3회 재시도, exponential backoff (500ms/1s/2s) — Backend + ExerciseDB 양쪽 적용
- OkHttp TokenAuthenticator: 401 응답 시 직렬화 락 기반 토큰 자동 무음 갱신 후 재시도
- 연결/읽기 타임아웃: 일반 API 15초 / AI 식단 생성 API 전용 130초
- 에러 추적: `android.util.Log` 대신 `Sentry.captureException()` 사용 (프로덕션 모니터링)

---

## 모니터링

### Sentry 풀스택 분산 추적

- **Android 프로젝트 (`gunnys / eundunhealth`)**:
  - 크래시, ANR(5초 블로킹), 네트워크 예외 자동 캡처
  - `sentry-android-okhttp`: 최외곽 네트워크 인터셉터로 엔드투엔드 분산 트레이싱 헤더(`sentry-trace`, `baggage`) 주입
  - ProGuard/R8 릴리스 빌드 난독화 매핑 파일 자동 업로드 (`sentry-android-gradle-plugin`)
  - 환경 구분: `development` (debug, tracesSampleRate 1.0) / `production` (release, tracesSampleRate 0.2)
- **Backend 프로젝트 (`gunnys / eundunhealth-backend`)**:
  - FastAPI 미들웨어 통합, 500 에러 및 비동기 unhandled exception 자동 캡처
  - 클라이언트-서버 간 상관관계 ID 추적을 통한 병목/오류 원인 심층 분석
  - 환경변수 `SENTRY_DSN` (Azure Key Vault `sentry-dsn-backend`) 기반 안전한 초기화

---

## 외부 서비스

| 서비스 | 용도 | 키 및 설정 위치 |
| :--- | :--- | :--- |
| **Microsoft Entra External ID** | OIDC/PKCE 브라우저 위임 인증 | `app/src/{debug,release}/res/raw/auth_config_ciam.json` (client_id, authority) + `ENTRA_API_SCOPE` |
| **Microsoft Graph API (v1.0)** | 회원 탈퇴 시 계정 영구 삭제 (`DELETE /users/{oid}`) | Azure Key Vault (`entra-client-id`, `entra-client-secret`, `entra-tenant-id`) |
| **OSS ExerciseDB** | 헬스장 모드 7개 부위 운동 데이터 카탈로그 | `https://oss.exercisedb.dev/api/v1/` (무인증 오픈 API, 키 불필요) |
| **Azure AI Foundry** | AI 맞춤 주간 식단 생성 (`gpt-5.4-mini` `GlobalStandard` 100K TPM) | Azure Key Vault (`deepseek-endpoint`, `deepseek-key`) |
| **Sentry.io** | 풀스택 에러 모니터링 및 APM 트레이싱 | Android: `local.properties` or CI 시크릿 / Backend: Key Vault `sentry-dsn-backend` |
| **Azure Container Apps** | 백엔드 API 호스팅 (`rg-eundunhealth-prod-krc`) | GitHub Actions Entra OIDC 무암호 CD (`AZURE_CLIENT_ID` 등) |
| **Azure PostgreSQL** | 비즈니스 데이터 저장소 (PostgreSQL 18.4) | Azure Key Vault (`database-url`) |

