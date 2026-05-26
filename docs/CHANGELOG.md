# eundunHealth 작업 내역서

> 형식: 큰 변화 순서대로 위에서 아래로. 각 릴리스의 세부 커밋은 git log 참조.

---

## v0.1.3 — 2026-05-26 (versionCode 17) — Android App Links 자동 로그인

### Added
- **App Links 자동 로그인**: Supabase Confirm Email 메일 링크를 같은 디바이스에서 클릭하면 앱이 자동으로 열려 PKCE code 교환을 거쳐 자동 로그인까지 완료 (Onboarding/Home 직진). 사용자 액션은 메일 링크 클릭 1회만.
- 백엔드: FastAPI 에 정적 라우트 2개 추가 — `/.well-known/assetlinks.json` (App Links 검증) + `/auth/confirm` (앱 미설치 사용자용 fallback HTML + Google Play 링크).
- Android: AndroidManifest intent-filter (autoVerify=true) + MainActivity 가 supabase-kt 빌트인 `SupabaseClient.handleDeeplinks` extension 으로 deep link 처리.
- `AuthViewModel.onDeepLinkSuccess` / `onDeepLinkError` — 라이브러리 콜백 받아 sessionState / authOpState 갱신.
- `mapAuthError` +3 매핑: `otp_expired` / `flow_state_expired` → 만료 메시지, `bad_code_verifier` / `flow_state_not_found` → 인증 정보 불일치 메시지.

### Changed
- `SupabaseModule` 에 `flowType = FlowType.PKCE`, `scheme = "https"`, `host = BuildConfig.APP_LINKS_HOST` 명시.
- AndroidManifest MainActivity 에 `launchMode="singleTop"` 추가 — deep link 재진입 시 Compose state 보존.

### Infrastructure
- Container App 기본 subdomain (`eundunhealth-api.livelyriver-782a792f.koreacentral.azurecontainerapps.io`) 활용. Custom domain 미보유 — v1.0 출시 전 재검토.
- 단위 테스트: AuthErrorMappingTest +3 (11), AuthViewModelTest +4 (19), backend test_auth_routes +3 (44 total).

### 운영 수동 절차 (이번 릴리스 머지 후 1회)
- Supabase Dashboard → Authentication → URL Configuration → Site URL = `https://<APP_LINKS_HOST>/auth/confirm`, Additional Redirect URLs 에도 추가.
- (선택) `local.properties` 에 `APP_LINKS_HOST=<container-app-fqdn>` 등록 — build.gradle.kts 기본값으로도 동작하지만 override 가능.
- assetlinks.json 의 SHA256 fingerprint 가 release keystore 와 일치하는지 디바이스에서 `adb shell pm get-app-links --user 0 com.gunnys.eundunhealth` 로 확인 (`verified` 표시).

### Refs
- Design: `docs/plans/2026-05-26-applinks-deep-link-design.md`
- Plan: `docs/plans/2026-05-26-applinks-deep-link-plan.md`

---

## v0.1.2 — 2026-05-26 (versionCode 16) — Hotfix

### Fixed
- **가입 무반응 hotfix**: Supabase project Confirm Email 토글 ON 상태에서 사용자는 정상 생성되고 확인 메일도 발송되지만, supabase-kt 3.6.0 의 `Email.decodeResult` 가 GoTrue 응답 JSON 의 일부 필드(특히 `aud`/`id`) 누락으로 `MissingFieldException` → `SupabaseEncodingException` 을 throw 하여 클라이언트가 "회원가입에 실패하였습니다"로 표시하던 문제. `mapSignUpException` 헬퍼에서 `SupabaseEncodingException` 을 `AwaitingConfirmation` 으로 분류하도록 별도 처리.
- 단위 테스트 +2 (`AuthErrorMappingTest`): SupabaseEncodingException → AwaitingConfirmation, 일반 예외 → AppErrorException

### 운영 메모 (이번 릴리스 외 후속 액션)
- Supabase Project → Authentication → URL Configuration → **Site URL을 localhost 외 값으로 변경** 필요. 확인 메일 링크가 localhost로 리다이렉트되어 외부 디바이스에서 "연결 거부" 발생. 임시로 `https://github.com/gunnysis/eundunHealth` 같은 정적 페이지로 지정하거나, 향후 deep link RFC 도입 시 `eundunhealth://confirm` 같은 custom scheme 사용.

---

## v0.1.1 — 2026-05-26 (versionCode 15)

### Added
- 회원가입 이메일 확인 흐름: 안내 카드 (AwaitingEmailConfirmation) + 60초 쿨다운 재전송 버튼
- Login 미인증 사용자(EmailNotConfirmed)에게 inline "인증 메일 다시 보내기" 액션 노출
- Login 이메일 자동 채움 (가입 후 "로그인하러 가기" 흐름에서 pendingEmail 전달)
- SignupScreen 비밀번호 6자 미만 inline 검증

### Changed
- 인증 상태 모델을 `SessionState` (글로벌) / `AuthOpState` (인증 화면 작업) / `SignupState` (가입 흐름) 세 sealed로 분리
- `ResetState`/`AuthState` 레거시 제거. `AppNavigation`은 `sessionState`만 구독
- `AuthRepository.signUp` 반환 타입을 `Result<SignupResult>` (`AutoSignedIn`/`AwaitingConfirmation`)로 변경
- `mapAuthError`를 top-level internal 함수로 분리, `email_not_confirmed` → `AppError.EmailNotConfirmed`

### Fixed
- 가입/로그인 실패 시 화면이 Login으로 튕기며 에러 스낵바가 잘리던 구조적 버그 — `AuthState.Unauthenticated` 전환을 명시적 logout/세션 만료에만 한정

### Infrastructure
- Supabase 사용 범위를 Authentication 한정으로 SPEC.md에 명시 (Database/Storage/Realtime/Edge Functions = out-of-scope)
- 단위 테스트 케이스 추가: AppErrorTest +1, AuthErrorMappingTest +6 (신규), AuthViewModelTest +8

### Refs
- Design: `docs/plans/2026-05-26-signup-confirmation-flow-design.md`
- Plan: `docs/plans/2026-05-26-signup-confirmation-flow-plan.md`

---

## v0.1.0 (2026-05-25) — 첫 의미있는 milestone

versionCode `14`, versionName `0.1.0`. v0.1·v0.2·v0.3 spec 전체 + 인프라 마이그레이션 + 출시 직전 안정화(Phase 1~6A + Dependabot 정리)를 한 릴리스로 묶음. Internal Testing 트랙 배포. 13은 안정화 전 첫 시도(업로드 완료, 14로 대체).

### Backend 전환 (Ktor → FastAPI)

- **Ktor(Kotlin) → FastAPI(Python 3.12)** 백엔드 전면 재작성. 동일 API 계약 유지 (camelCase JSON, 9개 → 12개 엔드포인트로 확장).
- 아키텍처: Router → Service → Repository, SQLAlchemy 2.0 async + asyncpg, Alembic async, JWKS(ES256) JWT 인증.
- 로컬 개발: docker-compose (Postgres + uvicorn hot reload).
- 운영 인프라:
  - **Container App** `eundunhealth-api` (RG `apps`, Korea Central) — 이미지 교체 + 환경변수 일괄 swap으로 무중단 cutover.
  - **Azure PostgreSQL** `healthapp` 동일 인스턴스 재활용. 스키마 무변경 (`alembic stamp head`로 기준점 설정 후 v0.3 마이그레이션 적용 → head `24d0fe2eb397`).
  - 옛 Ktor 이미지와 디렉토리(`backend/`)는 정리. Ktor 코드는 `D:\backup\dev\project\eundunHealth\`로 이관.

### Android 클라이언트 (v0.1)

- **에러 핸들링 통일** — `AppError` sealed class + `Throwable.toAppError()` + `AppError.reportToSentry()`. ViewModel 7개에 `_error: MutableStateFlow<AppError?>` + `clearError()` 패턴 통일. Screen 측은 `error.userMessage`를 SnackBar로 표시.
- **비밀번호 재설정** — `ForgotPasswordScreen` + `AuthViewModel.resetPassword` + Supabase `resetPasswordForEmail`.
- **UX 컴포넌트** — `ErrorContent`/`EmptyContent` 신규 + Home/History `PullToRefreshBox`.
- **회원 탈퇴** — `DELETE /account` + AlertDialog 확인 + Supabase Admin API로 Auth + 앱 DB 일괄 삭제.
- **OSS ExerciseDB 전환** — RapidAPI(`exercisedb.p.rapidapi.com`) → **OSS** `oss.exercisedb.dev`. 인증 불필요. DTO/API/DataSource 재작성 + Gson 회귀 테스트 5건.

### v0.2 — 운동 추천 + 통계

- **PUSH/PULL/LEGS 알고리즘** — 부위 균형(`chest+shoulders` / `back+upper arms` / `upper legs+lower legs`) + 화·목·토 cardio 분배 + 이전 주 운동 후순위(`excludeIds`). `GET /weekly-plan/previous` 신규.
- **통계 대시보드** — Vico 2.1.0 차트로 12주 완료율 + 현재/최장 스트릭. `GET /weekly-plan/statistics` 연동.
- **Detekt + Spotless + pre-commit + Android CI workflow** — `baseline-debug.xml`로 점진적 정리.

### v0.3 — 휴식일 + 목표 + 배지 확장

- **휴식일 커스터마이징** — `UserProfile.restDay` (ISO 1=월~7=일) + `SegmentedButton` + `WorkoutRepositoryImpl` 동적 슬롯 배치. 6요일에 push/cardio_a/pull/cardio_b/legs/mixed 순서.
- **목표 + 진행 차트** — `GoalScreen` 체중·체지방률 입력 + Vico 차트(`/profile/history`). `GET/PUT /goals` 연동. `user_profile_history`·`goals` 테이블 + Alembic 마이그레이션 (`24d0fe2eb397`).
- **배지 9종 확장** — 마일스톤 4 (`first_workout`/`workouts_10/50`/`streak_8weeks`) + 목표 달성 2 (`goal_weight/body_fat_achieved`). `CheckAndAwardBadgesUseCase.FIRST_WORKOUT` 로직.

### Supabase 한국 리전 전환

- 옛 프로젝트(`hcowzkqapzlvrvmawfcd`, US) → **한국 리전** `ttzzbfoksncqazvcsfiu`. Container App env/secret + Android BuildConfig 일괄 갱신. 옛 Auth 사용자 데이터는 출시 전 단계라 모두 무효화. Azure PG의 5개 사용자 테이블 `TRUNCATE`로 정리(`alembic_version`은 보존).

### 인프라 / CI/CD

- **Backend GitHub Actions** — ruff + mypy(strict) + pytest + Codecov → Docker compose runtime smoke (INC-03 차단) → pip-audit + bandit + gitleaks → main push 시 Trivy + ACR push + **secret precheck (INC-18 차단)** → Container App 배포 → /health.
  - `workflow_dispatch` trigger 지원 — paths 필터 우회로 secret rotation/긴급 재배포 가능.
  - 자동 배포 end-to-end 검증: revision `eundunhealth-api--0000007` 활성, /health 200 OK.
- **Android GitHub Actions** — spotlessCheck + detektDebug + testDebugUnitTest + assembleDebug. (`gradlew` + `scripts/*.sh` git exec bit 부여로 Linux runner에서도 실행 가능)
- **Dependabot** — pip + github-actions + gradle 주간 PR.
- **운영 시크릿** (총 5) — `database-url`(asyncpg URL), `supabase-url`, `supabase-service-role-key`, `sentry-dsn-backend`, ACR pull credential. 옛 `db-password`, `jwt-secret`은 미사용으로 제거.
- **GitHub Actions secret** — `AZURE_CREDENTIALS` (service principal + AcrPush role). 만료 갱신 절차: `scripts/register-azure-credentials.ps1 -Verify`.
- **Sentry 분리** — Android `eundunhealth` + Backend `eundunhealth-backend` 두 프로젝트. tracesSampleRate DEBUG=1.0 / PROD=0.2.
- **ACR 정리 후크** — Basic SKU retention 미지원 → `redeploy.sh`가 timestamp 태그 최근 5개만 보존하도록 자동 untag.
- **release 빌드** — `assembleRelease`(R8+서명) + `bundleRelease`(AAB 7.7MB) + ProGuard mapping Sentry 자동 업로드. keystore alias 수정(`eundunhealth_sign_key`). `scripts/preflight-release.sh`로 일괄 검증.

### 인시던트 + 재발 방지 (INC-01~18)

- 운영 사고·회귀 18건을 `docs/ops/incident-log.md`에 정리. 각 인시던트마다 **증상 → 근본 원인 → 복구 → 재발 방지** 4단으로 기록.
- 자동화 정착: `scripts/preflight-release.sh` (INC-04), `scripts/alembic-autogen.sh` (INC-07), `scripts/register-azure-credentials.ps1` (INC-17), `backend.yml` "Verify required Container App secrets exist" step (INC-18), `runtime-smoke` job (INC-03).
- 강제 룰: `CLAUDE.md`에 룰 1~6 영구 명시 (ACR untag-only, releaseArtifacts, alembic-autogen, lifespan middleware 금지, Supabase 교체 금지, secretref 동기화).
- 공통 안전망: `.github/PULL_REQUEST_TEMPLATE.md` destructive-ops 5문항 + AAB/APK 동기 + 마이그레이션 PG 검증 + secret 동기화 체크리스트.

### 버그 수정 (CRITICAL/HIGH/MEDIUM)

- **[CRITICAL]** `WeeklyPlanDao`에 `userId` 필터 누락 → 단말에서 사용자 전환 시 옛 사용자 캐시 노출 가능. EundunDatabase v1→v2.
- **[HIGH]** `TokenAuthenticator` 무한 대기 → 5초 timeout + 실패 시 token 무효화.
- **[HIGH]** release `signingConfig` 미연결.
- **[MEDIUM]** `BadgeRepository.hasBadge`를 `Result<Boolean>`로 통일 + 1분 TTL 캐시 + Repository에서 직접 `Sentry.captureException` 제거 (ViewModel `reportToSentry`로 일원화).
- **[Backend]** starlette 0.49+ `add_middleware`가 lifespan 내부 호출 금지 → 모듈 레벨로 이동. Docker 스모크 검증에서 발견.

### 성능 개선

- `DateTimeFormatter` 싱글톤 (HistoryScreen 카드별 재생성 제거).
- `ProfileSlider`의 `format` 결과를 `remember`로 캐싱.
- Coil `ImageRequest.size(512)`로 GIF 다운스케일.

### 운영 문서

- `docs/ops/migration-runbook.md` — Ktor→FastAPI 절차 + 사후 정리 결과.
- `docs/ops/monitoring-and-cost.md` — Sentry 활성화 가이드 + ACR Basic 한계 + Azure 비용 알림.
- `docs/ops/play-store-release.md` — 첫 출시 8단계 + 데이터 안전 답변표.
- `docs/ops/operations-snapshot.md` — 현재 운영 상태 단일 출처.
- `docs/privacy-policy.md` — 한국 리전 반영 개인정보 처리방침.
- `docs/ops/containerapp-env-ktor-backup.json` — cutover 직전 env 스냅샷(보존).

### 검증 / 품질

- Backend: pytest **41/41 PASS** (12 v0.1 + 8 edge case + 8 v0.2 + 13 v0.3), mypy strict clean, ruff/bandit clean, pip-audit (1 ignored: `PYSEC-2026-161` starlette 1.0 미지원).
- Android: spotlessCheck clean, detektDebug clean, unit test 일체 PASS, assembleRelease + bundleRelease BUILD SUCCESSFUL.

### 출시 직전 안정화 (PR #19–#26, Dependabot 정리)

5월 25일 v0.1.0 첫 milestone 이후 출시 직전까지 진행된 안정화. 운영 silent 버그 6건 정리 + 의존성 정비 + Compose 가독성 개선.

#### Phase 1–4: OpenAPI 자동 생성 + drift detection 인프라 (#19)
- backend FastAPI 스펙(`backend/openapi.json`)을 Android Retrofit 클라이언트의 단일 출처로 만들기 위한 side-by-side 인프라.
- `openapi-generator 7.10.0` Gradle 플러그인 도입 — `:app:openApiGenerate`가 `preBuild`에서 자동 실행, 출력은 `app/build/generated/openapi/com/gunnys/eundunhealth/api/generated/`.
- `scripts/sync-openapi.sh` 신규 — 라우터/스키마 변경 시 spec 재생성.
- `backend.yml`의 "Verify OpenAPI spec is in sync" CI step — spec drift PR 단계에서 fast-fail.
- backend 라우터 15개 endpoint에 `operation_id` 명시 + `weekStart` Query alias로 통일. 숨은 drift 동시 수정: `getWeeklyPlan`이 보낸 `weekStart` 쿼리를 backend가 무시하고 default(today)로 fallback하던 버그.

#### Phase 5A: schema drift 4건 + critical 버그 동시 수정 (#20)
출시 차단 사유 2건 + 부수 drift 2건 발굴:
- 🔴 `updateDayCompletion` body mismatch — 매번 422 silent fail (운동 완료 토글이 서버에 저장 안 됨). day-level (`weekStart, date, completed`) 도메인으로 통일. statistics 일관성을 위해 day의 `isCompleted`와 해당 day의 모든 `exercises[*].completed`를 동시 갱신.
- 🔴 `getWeeklyPlanHistory` envelope/list 불일치 — Gson deserialization 실패로 HistoryScreen 깨짐. `WeeklyPlanHistoryResponse(plans, totalCount, page, size)` envelope 신규.
- 🟡 `UserProfileResponse`/`WeeklyPlanResponse`에 `userId`/`id` 누락 — Android에서 빈 string fallback. 필드 추가.

#### Phase 5B+5C: Repository를 generated client로 + EundunApi 제거 (#21)
- 4 Repository(Badge, Goal, User, Workout) + `AuthRepository.deleteAccount` → generated `BadgesApi`/`GoalsApi`/`ProfileApi`/`WeeklyPlanApi`/`AccountApi` 사용.
- 추가 silent drift 2건 동시 fix:
  - `POST /weekly-plan` 응답을 dict → `WeeklyPlanResponse` (Android Room cache의 `id=""` 저장 문제 해결).
  - `POST /badges/{key}` 응답을 dict → `BadgeResponse` (BadgeCatalog 룩업 실패로 빈 라벨 문제 해결).
- `EundunApi.kt`(70줄)+`ApiDtos.kt`(80줄) 제거, side-by-side 종료. 단일 진실 출처는 `backend/openapi.json`.
- 공통 helper `data/remote/util/ResponseExt.kt`의 `bodyOrThrow()` 도입.

#### Phase 6A: Compose 가독성 정리 (#22)
- `ui/components/ProfileSlider.kt` 신규 — Onboarding/Profile 양쪽 공유.
- `ProfileScreen` 분해: `BodyMetricsSliders` / `RestDaySelector` / `ProfileActionButtons`.
- `HomeScreen` 분해: `HomeTopBarActions` / `HealthConnectPromptCard`.
- ViewModel 패턴 통일은 분석 결과 over-refactoring으로 판단해 스킵 (각 ViewModel은 이미 단일 책임으로 모델링됨).

#### Dependabot 일괄 정리 (#2–#6, #12·#14, #23–#26)
의존성 위생 정리. 일부 dependabot PR이 main 변경 사이 반복 stale로 빠지는 race condition을 직접 결합 PR로 해결하는 패턴 정착.

**GitHub Actions** — `setup-python` 5→6, `upload-artifact` 4→7, `gradle/actions` 4→6

**Android Gradle**
- `gradle-wrapper` 9.4.1→9.5.1
- `sentry` 8.16.0→8.42.0
- `spotless` 7.0.4→8.5.1 (+ 새 룰에 맞춰 3 Kotlin file 자동 reformat)
- `coreKtx` 1.16.0→1.18.0, `lifecycleRuntimeKtx` 2.9.0→2.10.0
- `hiltNavigationCompose` 1.2.0→1.3.0, `navigationCompose` 2.9.0→2.9.8
- `dataStore` 1.1.4→1.2.1

**Python (backend)**
- `pytest` 8.3.0→9.0.3, `pytest-asyncio` 0.24.0→1.3.0, `pytest-cov` 6.0.0→7.1.0
- `uvicorn` 0.34.0→0.48.0, `sqlalchemy` 2.0.36→2.0.50, `alembic` 1.14.0→1.18.4
- `sentry-sdk` 2.19.0→2.60.0, `pydantic-settings` 2.7.0→2.14.1
- `PyJWT` 2.12.0→2.13.0, `asyncpg` 0.30.0→0.31.0
- `ruff` 0.8.0→0.15.14, `bandit` 1.8.0→1.9.4, `pip-audit` 2.7.3→2.10.0
- `httpx` 0.28.0→0.28.1, `aiosqlite` 0.20.0→0.22.1

**보류 (v0.1.0 출시 후 재검토)**
- `kotlin` 2.2.10→2.3.21 — Compose Compiler·Hilt·KSP 호환성 매트릭스 검증 필요.
- `starlette` 0.49.1→1.1.0 — INC-2026-05-24-03(lifespan 회귀) 위험 + fastapi 0.136.3이 starlette 1.x 지원하는지 확인 필요.
- `healthConnect` 1.1.0-rc01→1.2.0-alpha04 — rc → alpha 다운그레이드. 1.2 stable 릴리스 대기.

---

## v0.0.3-2 (2026-05-22)

### Sentry SDK 메이저 업그레이드
- Sentry Android SDK 7.14.0 → 8.16.0 (16KB 페이지 정렬 네이티브 라이브러리 포함)
- Sentry Gradle Plugin 4.14.1 → 5.8.0 (SDK 8.x 호환 필수)
- AndroidManifest에서 SentryInitProvider 자동 초기화 비활성화 (`tools:node="remove"`)
- EundunHealthApplication에서 DSN 빈값 시 `isEnabled = false` 처리 (크래시 방지)
- `isEnableAutoSessionTracking` 제거 (8.x 기본값)
- Sentry Gradle Plugin: 환경 변수 `SENTRY_AUTH_TOKEN` 우선 참조, 토큰 없으면 매핑 업로드 자동 비활성화
- `packaging.jniLibs.useLegacyPackaging = false` 추가 (16KB ZIP 정렬)

### 백엔드 JWT 인증 변경
- Supabase JWT 서명 알고리즘 변경 대응: HMAC256 → JWKS 기반 ES256 공개키 검증
- `com.auth0:jwks-rsa:0.22.1` 의존성 추가
- JwkProviderBuilder로 JWKS 엔드포인트 캐시 (10키, 24시간, 분당 10회 제한)
- `SUPABASE_JWT_SECRET` 환경 변수 더 이상 불필요 (공개키 자동 조회)

### 프로필 편집 기능 추가
- ProfileScreen / ProfileViewModel 신규 생성
- 홈 상단바에 Person 아이콘 추가 → 프로필 편집 화면 진입
- 서버에서 기존 프로필 로드 → 슬라이더 초기값 세팅 → 수정 후 저장
- Screen.kt에 Profile route 추가, AppNavigation에 라우팅 연결

### 인증 에러 UX 개선
- AuthRepositoryImpl에 `mapAuthError()` 추가
- Supabase 에러 코드를 한국어 사용자 메시지로 매핑 (invalid_credential, email_not_confirmed, weak_password 등)

### 시스템 UI 겹침 해결
- LoginScreen, SignupScreen: `imePadding()` + `verticalScroll()` 추가 (키보드가 입력 필드 가리는 문제)
- OnboardingScreen, ProfileScreen: `imePadding()` + `verticalScroll()` 추가, `Spacer(weight)` → `Spacer(height)` (스크롤과 weight 충돌 제거)

### 리팩토링
- OnboardingViewModel, ProfileViewModel: SupabaseClient 직접 의존 제거 → `AuthRepository.getCurrentUserId()` 사용
- ProfileViewModel: stringly-typed `saveResult: String?` → `SaveState` sealed class (Idle/Success/Error)
- ProfileSummaryCard 공통 컴포넌트 추출 (OnboardingScreen, ProfileScreen에서 재사용)
- OnboardingScreen, ProfileScreen에서 Card/CardDefaults 불필요 import 제거

### 문서
- CLAUDE.md 생성 및 업데이트 (배포 명령어, ViewModel 패턴, JWT 알고리즘, 시간대 등)

---

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
