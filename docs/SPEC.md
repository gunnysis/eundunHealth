# eundunHealth 기능 명세서

> **문서 버전:** v1.0 (초기 설계) — 본문은 그대로 보존.
> **현재 제품 상태(2026-05-26):** v0.1.1 (versionCode 15 — v0.1.0(versionCode 14) 출시 후속, 가입 이메일 확인 흐름 + 인증 상태 모델 리팩터). v0.2(통계·운동 알고리즘)·v0.3(휴식일·목표·배지 확장) spec 모두 구현 완료 + Phase 1~6A 출시 직전 안정화 + 가입 흐름 보강. 자세한 차이는 [CHANGELOG.md](./CHANGELOG.md) / [TRD.md "구현 후 변경 사항"](./TRD.md) 참조.
>
> **Supabase 사용 범위 (v0.1.1 명시):** Authentication 한정. Database(테이블)/Storage/Realtime/Edge Functions는 사용하지 않음 — 모든 비즈니스 데이터는 Azure PostgreSQL(`healthapp`)에 저장. Supabase는 JWT 발급(ES256/JWKS) + 회원가입 이메일 확인 + 비밀번호 재설정 메일만 담당.

## 프로젝트 개요

**은둔헬스(eundunHealth)** 는 사용자의 신체 정보를 기반으로 맞춤 주간 운동 계획을 자동 생성하고, 운동 달성을 추적하는 Android 앱입니다.

- **패키지**: `com.gunnys.eundunhealth`
- **최소 SDK**: 26 (Android 8.0)
- **대상 SDK**: 37 (Android 17)
- **백엔드**: FastAPI(Python 3.12) — 본 문서 v1.0 작성 당시 Ktor였으나 v0.1.0에서 전환됨.
- **운동 데이터**: OSS ExerciseDB(`oss.exercisedb.dev`, 인증 불필요).
- **인증**: Supabase Auth(한국 리전, JWKS ES256).

---

## 기술 스택

### Android (클라이언트)
| 기술 | 버전 | 용도 |
|------|------|------|
| Kotlin | 2.2.10 | 언어 |
| Jetpack Compose (BOM) | 2026.05.01 | UI 프레임워크 |
| Hilt | 2.59.2 | 의존성 주입 |
| Room | 2.8.4 | 로컬 데이터베이스 (오프라인 캐시) |
| Retrofit + OkHttp | 2.11.0 / 4.12.0 | 백엔드 API 통신 |
| Coil | 2.7.0 | 이미지/GIF 로딩 |
| DataStore | 1.1.4 | 설정 영속화 (다크모드) |
| Supabase Kotlin SDK | 3.6.0 | 인증 (Auth) |
| Health Connect | 1.1.0-rc01 | 운동 세션 자동 감지 |
| Sentry Android SDK | 7.14.0 | 크래시/에러 모니터링 |
| Navigation Compose | 2.9.0 | 화면 전환 |
| Activity Compose | 1.13.0 | Activity + Compose 연동 |

### Backend (서버)
| 기술 | 버전 | 용도 |
|------|------|------|
| Ktor (Netty) | 3.4.3 | HTTP 서버 |
| Exposed ORM | 0.61.0 | 데이터베이스 쿼리 |
| PostgreSQL (Azure) | 42.7.7 | 데이터 저장소 |
| HikariCP | 6.2.1 | DB 커넥션 풀 |
| Supabase JWT | java-jwt 4.5.0 | 인증 토큰 검증 |
| Sentry JVM SDK | 7.14.0 | 서버 에러 모니터링 |
| Shadow Plugin | 9.0.0-beta12 | Fat JAR 빌드 |

### 빌드 도구
| 기술 | 버전 |
|------|------|
| AGP | 9.2.1 |
| Gradle | 9.3.1 |
| KSP | 2.3.2 |
| Sentry Gradle Plugin | 4.14.1 |

### 인프라
| 기술 | 용도 |
|------|------|
| Azure Container Apps | 백엔드 호스팅 |
| Azure Container Registry | Docker 이미지 저장 |
| Azure PostgreSQL Flexible Server | 데이터베이스 |
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
- 신체 정보 입력 (키, 몸무게, 체지방률, 근육량)
- Slider + 키보드 하이브리드 입력
- 범위 초과 시 빨간색 에러 표시 + 안내 메시지
- 입력 요약 카드 (등록 전 확인)
- "운동 계획 받기" 버튼 → 프로필 저장 → Home 이동

### 5. 홈 화면 (`HomeScreen`)
- **주간 진행률 카드**: 완료율 프로그레스 바 + 퍼센트 표시
- **Health Connect 연동 배너**: 미연동 시 표시, "연동" 버튼
- **요일별 운동 카드 (DayPlanCard)**:
  - 탭하여 운동 완료/미완료 토글 (Optimistic update)
  - 완료 시 primaryContainer 배경색 + CheckCircle 아이콘
  - 운동명 탭 시 운동 상세 화면 이동
  - 휴식일 표시
- **스켈레톤 UI**: 로딩 중 shimmer 애니메이션 카드
- **TopAppBar 액션**:
  - 테마 토글 (SYSTEM → DARK → LIGHT 순환)
  - 새로고침
  - 히스토리
  - 배지
  - 로그아웃

### 6. 운동 상세 화면 (`WorkoutDetailScreen`)
- 운동 GIF 애니메이션 (SubcomposeAsyncImage, 로딩/에러 상태)
- 운동명, 부위, 장비
- 세트 x 횟수
- 운동 방법 (순서 목록)

### 7. 운동 기록 히스토리 (`HistoryScreen`)
- 과거 주간 계획 목록 (무한 스크롤 페이지네이션)
- 주별 카드: 날짜 범위, 완료율 프로그레스 바, 요일별 체크 아이콘

### 8. 배지 화면 (`BadgeScreen`)
- 전체 배지 목록 (카탈로그 기반)
- 획득 배지: 트로피 아이콘, primaryContainer 배경, 획득 날짜
- 미획득 배지: 잠금 아이콘, surfaceVariant 배경

---

## 핵심 기능 상세

### 인증
- **Supabase Auth** 이메일/비밀번호 기반
- 세션 자동 저장/복원 (`autoSaveToStorage`, `autoLoadFromStorage`)
- 토큰 자동 갱신 (`alwaysAutoRefresh`)
- `AuthRepository.restoreSession()`: 자동 로그인 시 세션 복원 + tokenHolder 설정
- OkHttp TokenAuthenticator로 401 응답 시 Supabase 토큰 자동 갱신 후 재시도
- AuthViewModel은 AuthRepository 인터페이스만 의존 (SupabaseClient 직접 참조 없음)

#### 회원가입 이메일 확인 흐름
- 회원가입 요청이 성공하면 Supabase가 확인 메일을 발송하고, 사용자의 세션은 메일 인증을 마친 후에만 활성화된다. 가입 직후에는 인증 토큰을 발급하지 않으며, 앱은 자동 로그인 상태로 진입하지 않는다.
- 가입 직후 SignupScreen은 `AwaitingEmailConfirmation` 상태로 전환되어 메일 발송 안내와 재전송 버튼을 노출한다. 재전송은 60초 쿨다운으로 제한하며, 쿨다운 동안 버튼은 비활성화되고 남은 시간을 함께 표시한다.
- 메일 인증을 마친 사용자는 Login 화면으로 이동해 수동으로 로그인한다. 이때 이메일 입력란은 직전 회원가입에서 입력한 이메일로 자동 채워져 재입력 부담을 줄인다.
- 미인증 상태에서 로그인을 시도하면 `EmailNotConfirmed` 에러를 한국어 메시지로 표시하고, 동일한 화면에 inline "메일 재전송" 액션을 노출해 즉시 확인 메일을 다시 받을 수 있게 한다.

#### Supabase 사용 범위
- 본 앱은 Supabase의 **Authentication 서비스만** 사용한다. 사용자 데이터(프로필, 주간 계획, 배지 등)는 모두 자체 FastAPI 백엔드 + Azure PostgreSQL에 저장한다.
- Supabase Database / Storage / Realtime / Edge Functions 등 다른 제품군은 본 명세 범위 밖(out-of-scope)이며, 도입이 필요한 경우 별도 RFC 후 SPEC/TRD를 갱신한다.

### 주간 운동 계획 생성
- ExerciseDB API에서 부위별 운동 조회 (chest, back, upper legs, shoulders, upper arms)
- 근력 운동: 월/수/금 (4종목)
- 유산소 운동: 화/목 (2종목)
- 혼합 운동: 토 (근력 2 + 유산소 1)
- 휴식: 일요일
- 주 시작일(월요일) 기준 seed로 결정론적 셔플

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

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| `GET` | `/health` | 헬스 체크 (DB 연결 검증) | X |
| `GET` | `/profile` | 사용자 프로필 조회 | O |
| `PUT` | `/profile` | 프로필 생성/수정 | O |
| `GET` | `/weekly-plan?weekStart=` | 주간 계획 조회 | O |
| `POST` | `/weekly-plan` | 주간 계획 생성/수정 | O |
| `PATCH` | `/weekly-plan/complete` | 운동 완료 토글 | O |
| `GET` | `/weekly-plan/history?page=&size=` | 히스토리 페이지네이션 | O |
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
│   └── usecase/                   # GetOrCreateWeeklyPlan, SyncHealth, CheckBadges
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
- OkHttp TokenAuthenticator: 401 응답 시 Supabase 토큰 자동 갱신 후 재시도
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
|--------|------|---------|
| Supabase | 인증 (Auth) | `local.properties` → SUPABASE_URL, SUPABASE_ANON_KEY |
| ExerciseDB (RapidAPI) | 운동 데이터 조회 | `local.properties` → EXERCISEDB_API_KEY |
| Sentry.io | 에러 모니터링 | `local.properties` → SENTRY_DSN, SENTRY_AUTH_TOKEN |
| Azure Container Apps | 백엔드 호스팅 | Azure CLI 인증 |
| Azure PostgreSQL | 데이터 저장 | 환경변수 → AZURE_DB_URL/USER/PASSWORD |
