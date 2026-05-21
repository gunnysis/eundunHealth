# eundunHealth 작업 내역서

## v0.0.3 (2026-05-21)

### Android 17 (API 37) 대응
- compileSdk/targetSdk 36 → 37, AndroidManifest tools:targetApi 37
- 사이드 이펙트 분석: 앱 기능(인증, REST API, Health Connect, Room)에 영향 없음 확인

### 의존성 업데이트 (API 37 호환)
- Hilt 2.56.2 → 2.59.2 (AGP 9.x 호환성 개선)
- Compose BOM 2025.05.01 → 2026.05.01 (최신 Compose)
- Activity Compose 1.10.1 → 1.13.0 (edge-to-edge 대응)
- Room 2.7.1 → 2.8.4 (버그 수정)
- Supabase 3.1.4 → 3.6.0 (안정성 개선)
- Ktor 3.1.2 → 3.5.0 (Supabase 호환)

### 네트워크 보안 강화
- network_security_config.xml에 `base-config cleartextTrafficPermitted="false"` 추가
- Release 빌드에서 HTTP cleartext 통신 명시적 차단
- CLEARTEXT communication to 10.0.2.2 에러 방어 처리

### Sentry 설정 수정
- sentry-android-okhttp → sentry-okhttp 모듈 전환 (deprecated 해결)
- Sentry project slug `eundunhealth-android` → `eundunhealth` 수정 (404 에러 해결)

### 리팩토링
- AuthViewModel: SupabaseClient 직접 호출 제거 → AuthRepository 인터페이스 사용으로 전환
- AuthRepository.restoreSession() 추가: 자동 로그인 시 tokenHolder 설정 (401 에러 근본 원인 수정)
- WorkoutRepositoryImpl: `android.util.Log` → `Sentry.captureException()` 전환 (프로덕션 에러 추적)
- DayPlanJson/ExerciseJson → `PlanJsonModels.kt` 별도 파일 분리 (단일 책임 원칙)
- WeeklyPlanDao: 빈 userId 파라미터 제거, weekStart만으로 캐시 조회
- ExerciseDB OkHttpClient에 RetryInterceptor + 15초 타임아웃 추가

### 빌드 개선
- AGP 9.1.1 → 9.2.1 업데이트
- gradle.properties에서 불필요한 deprecated 옵션 정리
- AGP 9.x 호환성 모드 플래그 주석 문서화

---

## v0.0.2 (2026-05-21)

### Sentry 크래시 모니터링 통합
- Sentry Android SDK 7.14.0 통합 (크래시/ANR 자동 캡처, 세션 트래킹)
- Sentry OkHttp Interceptor로 네트워크 요청 트레이싱
- Sentry JVM SDK로 Ktor 백엔드 500 에러 자동 캡처 (StatusPages 연동)
- Release 빌드 시 ProGuard 매핑 자동 업로드 (난독화된 스택 트레이스 복원)

### 네트워크 안정성
- OkHttp RetryInterceptor 추가 (최대 3회, exponential backoff 500ms/1s/2s)
- OkHttp TokenAuthenticator 추가 (401 시 Supabase 토큰 자동 갱신)
- 연결/읽기 타임아웃 15초 설정
- Release 빌드에서 HTTP 로깅 비활성화

### 운동 완료 수동 체크
- Backend: `PATCH /weekly-plan/complete` 엔드포인트 추가
- DayPlanCard 탭으로 운동 완료/미완료 토글
- Optimistic update (즉시 UI 반영, 서버 실패 시 롤백)
- Health Connect 자동 감지 완료를 서버에 동기화

### 주간 진행률 대시보드
- HomeScreen 상단에 주간 완료율 카드 (LinearProgressIndicator)
- 완료/전체 운동일 수 및 퍼센트 표시

### 운동 기록 히스토리
- Backend: `GET /weekly-plan/history?page=0&size=10` 페이지네이션 API 추가
- HistoryScreen 신규 생성 (무한 스크롤, LazyColumn + derivedStateOf)
- 주별 완료율 + 요일별 체크 아이콘 표시
- HomeScreen TopAppBar에 히스토리 아이콘 추가

### 스켈레톤 UI
- ShimmerBox 컴포넌트 (shimmer 애니메이션)
- HomeScreen 로딩 시 스켈레톤 카드 5개 표시 (CircularProgressIndicator 대체)

### 입력 검증 강화
- 온보딩 ProfileSlider에 범위 초과 시 빨간색 에러 표시 + 안내 메시지
- 입력 요약 카드 추가 (등록 버튼 위에 현재 입력값 요약)
- 프로필 저장 실패 시 Sentry 에러 캡처

### 다크모드 수동 토글
- ThemePreferences (DataStore) 생성 — SYSTEM/DARK/LIGHT 3단계 순환
- HomeScreen TopAppBar에 테마 토글 아이콘 (BrightnessAuto/DarkMode/LightMode)
- 앱 재시작 시 설정 유지

### 배지 상세 강화
- BadgeDisplayItem에 earnedAt 필드 추가
- 배지 획득 날짜 표시 (yyyy.M.d 형식)

### GIF 로딩 개선
- Coil ImageLoader에 메모리/디스크 캐시 정책 활성화
- WorkoutDetailScreen AsyncImage → SubcomposeAsyncImage 전환
- 로딩 중 CircularProgressIndicator, 에러 시 안내 메시지 표시

### Health Connect 개선
- HealthConnectDataSource에 SDK 가용성 체크 추가
- 동기화 실패 시 Sentry 에러 캡처

### 인프라
- Sentry Gradle Plugin 4.14.1 추가 (ProGuard 매핑 자동 업로드)
- DataStore Preferences 1.1.4 의존성 추가
- proguard-rules.pro에 Sentry/DataStore keep 규칙 추가

---

## v0.0.1 (2026-05-21)

### MVP 초기 구현
- 이메일/비밀번호 회원가입 및 로그인 (Supabase Auth)
- 자동 로그인 (Supabase 세션 영속성 + Splash 화면)
- 신체정보 입력 온보딩 (키, 몸무게, 체지방률, 근육량 — Slider + 키보드 하이브리드)
- ExerciseDB API 기반 주간 운동 계획 자동 생성 (근력 + 유산소 + 휴식일)
- 운동 상세 화면 (GIF 애니메이션, 세트/횟수, 운동 방법)
- Health Connect 연동 (운동 세션 자동 감지 → 완료 표시)
- 챌린지 배지 시스템 (1주/2주/3주 연속 완료)
- Room 로컬 캐시 (오프라인 플랜 조회)

### 백엔드
- Ktor 3.4.3 + Exposed ORM + PostgreSQL (Azure Flexible Server)
- Supabase JWT 인증 (Bearer 토큰 검증)
- REST API: profile CRUD, weekly-plan CRUD, badges CRUD
- AppConfig 패턴으로 환경변수 중앙화 (System.getenv → dotenv 폴백)
- CORS 동적 설정 (AppConfig.allowedOrigins)
- Health check 엔드포인트 (`GET /health` — DB 연결 검증)

### 배포
- Docker 멀티스테이지 빌드 (gradle:8.14-jdk17 → eclipse-temurin:17-jre-alpine)
- Azure Container Registry + Azure Container Apps 배포
- non-root 유저, HEALTHCHECK, Shadow Fat JAR
- deploy.sh / redeploy.sh 스크립트

### 코드 리뷰 기반 리팩토링
- R8 missing classes 해결 (proguard-rules.pro)
- BuildConfig local.properties 연동 수정
- network_security_config.xml 추가 (실기기 HTTP 허용)
