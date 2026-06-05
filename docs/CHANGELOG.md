# eundunHealth 작업 내역서

> 형식: 큰 변화 순서대로 위에서 아래로. 각 릴리스의 세부 커밋은 git log 참조.

---

## [main] — 2026-06-06 — GitHub Actions Node.js 20 → 24 런타임 업그레이드

### 🎯 Prompts
1. "dependabot Node.js 20 경고 해결해줘"
2. "CI 결과 확인해줘"
3. "changelog 작업해줘"

### ✅ Changes
- **Modified** `.github/workflows/android.yml` — `actions/checkout@v4` → `@v6`
- **Modified** `.github/workflows/backend.yml` — `actions/checkout@v4` → `@v6` (4곳), `gitleaks/gitleaks-action@v2` → `@v3`, `aquasecurity/trivy-action@master` → `@v0.36.0`
- **Modified** `.github/workflows/docs-plans-index.yml` — `actions/checkout@v5` → `@v6`
- **Closed** Dependabot PR #72 (`actions/checkout` v4→v6), PR #73 (`gitleaks-action` v2→v3)

### 📁 Files Modified
- `.github/workflows/android.yml` (+1, -1)
- `.github/workflows/backend.yml` (+6, -6)
- `.github/workflows/docs-plans-index.yml` (+1, -1)

### 🔍 Notes
- Node.js 20 deprecated June 2026, removed September 2026
- 9개 action 중 3개만 업그레이드 필요 — 나머지 6개 (`setup-java@v5`, `setup-gradle@v6`, `upload-artifact@v7`, `setup-python@v6`, `codecov-action@v6`, `azure/login@v3`) 는 이미 Node.js 24 런타임
- `trivy-action`은 Docker 기반이라 Node.js 런타임 경고 대상 아님, `@master` → `@v0.36.0` pin은 supply chain 보안 목적
- 첫 push에서 `@0.36.0` (`v` prefix 누락)으로 deploy 실패 → `@v0.36.0`으로 즉시 수정

---

## [main] — 2026-06-06 — 프론트엔드 UDF-Enhanced 마이그레이션 + 회귀 방지 가드

### 🎯 Prompts
1. "프론트엔드 대규모 개선 종합 설계 (Rev.2) 기반 Phase 1-5 구현"
2. "프론트엔드 회귀 방지 설계 — 실행 계획 구현"
3. "프로젝트의 문서 최신화 작업해줘"

### ✅ Changes

#### Phase 1-5: 12 ViewModel UDF-Enhanced 패턴 마이그레이션
- **Modified** 9 ViewModels → 단일 `_uiState: MutableStateFlow<XxxUiState>` 패턴으로 전환. 분산 StateFlow (`_error`/`_isLoading`) 제거.
  - AuthVM, BadgeVM, GoalVM, HistoryVM, HomeVM, OnboardingVM, ProfileVM, StatisticsVM, WorkoutDetailVM
- **Added** 3 ViewModels (AuthVM 분리): `LoginViewModel` (96L), `SignupViewModel` (102L), `ForgotPasswordViewModel` (61L). AuthVM 은 session lifecycle 전용으로 축소.
- **Modified** 11 Screens → 모든 `collectAsState()` 를 `collectAsStateWithLifecycle()` 로 교체 (lifecycle-aware collection).
- **Added** `@Immutable` annotation 45건 across 17 files — domain models 5개 (Exercise, Goal, Statistics, UserProfile, WeeklyPlan) + 12 ViewModel UiState/SideEffect sealed class.
- **Added** SideEffect Channel 7 VMs — 일회성 이벤트 (navigation, snackbar) 를 `Channel<SideEffect>(Channel.BUFFERED)` + `receiveAsFlow()` 로 전달.
- **Added** 3 test files: `LoginViewModelTest` (202L), `SignupViewModelTest` (192L), `ForgotPasswordViewModelTest` (110L)
- **Modified** `AuthViewModelTest` — AuthVM 축소에 따라 per-screen 테스트를 신규 테스트 파일로 분리. 대폭 감소.

#### 의존성 메이저 업그레이드
- **OkHttp 4.12.0 → 5.3.2**: Kotlin-first API, HTTP/3 지원, 개선된 connection pool.
- **Coil 2.7.0 → 3.4.0**: module group `io.coil-kt` → `io.coil-kt.coil3`. `coil-network-okhttp` 신규 의존성 추가. `CoilModule` Coil 3 API 마이그레이션 (`ImageLoader` → `SingletonImageLoader.Factory` 패턴).
- **gradle.properties**: `android.r8.strictFullModeForKeepRules=false` 제거 (OkHttp 5 / Coil 3 호환).

#### 회귀 방지 3계층 가드
- **Added** CLAUDE.md **룰 11** — ViewModel UDF-Enhanced 패턴 5개 체크리스트 + 허용 예외 + baseline.
- **Added** `.github/workflows/android.yml` "Check collectAsState anti-pattern" CI step — import `$` anchor + 호출부 `grep -v` 필터, false positive 0.
- **Modified** `.githooks/pre-commit` — collectAsState grep check 섹션 추가 (staged `.kt` 파일 한정, 룰 11).
- **Added** `docs/plans/_staging/2026-06-06-frontend-regression-prevention-design.md` — 설계 문서 (D1~D6 결정 테이블 + 잔여 리스크).

#### 문서 동기화
- **Modified** `CLAUDE.md` — Key patterns 섹션 UDF-Enhanced 반영, stale 버전 수정 (Sentry 8.16→8.42, Vico 2.1→3.1, Spotless 7.0→8.5, OkHttp/Coil 추가), pre-commit 설명 갱신.
- **Modified** `docs/ops/operations-snapshot.md` — §8 CI/자동화에 collectAsState 가드 추가, §13 변경 이력 추가.
- **Modified** `docs/CHANGELOG.md` (this entry)

### 📊 Metrics (MEASURED 2026-06-06)
- `@Immutable`: 45 annotations across 17 files
- `collectAsStateWithLifecycle`: 33 occurrences across 13 files
- `collectAsState()`: **0건** (anti-pattern 완전 제거)
- SideEffect Channel: 7 VMs
- Total: 46 files, +752 / -869 lines (net -117)

### 📁 Files Modified
- ViewModels: 9 modified + 3 new
- Screens: 11 modified
- Tests: 1 modified + 3 new
- Domain models: 3 modified (`@Immutable` 추가)
- UI components: 6 modified (`@Immutable` 추가)
- Infra: `CoilModule`, `MainActivity`, `AppNavigation`, `build.gradle.kts`, `libs.versions.toml`, `gradle.properties`
- Guards: `CLAUDE.md`, `android.yml`, `.githooks/pre-commit`
- Docs: `operations-snapshot.md`, `CHANGELOG.md`
- Design: `docs/plans/_staging/2026-06-06-frontend-regression-prevention-design.md` (new)

---

## [main] — 2026-06-05 — docs/plans/ lifecycle 관리 개선

### 🎯 Prompts
1. "Implement the following plan: docs/plans/ 문서 lifecycle 관리 개선 [holding/deferred status + root-only scan + grouped rendering + _staging gitignore]"
2. "CLAUDE.md 업데이트해줘"

### ✅ Changes
- **Modified**: `scripts/gen_plans_index.py` — `holding`/`deferred` status 추가, `rglob`→`glob` root-only 스캔, `render_readme_v2` status 그룹별 하위 섹션 렌더링 (`진행 중`/`대기`/`보류`)
- **Modified**: `scripts/test_gen_plans_index.py` — 4개 테스트 추가 (31 total, all pass)
- **Modified**: `.gitignore` — `docs/plans/_staging/` 추가 (scratch 작업 폴더)
- **Modified**: `docs/plans/_templates/{design,plan}.md` — status lifecycle 주석 추가
- **Moved**: `docs/plans/expected/` → `docs/plans/_staging/` (gitignored)
- **Modified**: `CLAUDE.md` — `gen-plans-index.sh` 항목에 root-only scan, 8개 status 값, 그룹 렌더링, `_staging/` 설명 반영

### 📊 Test Results
- Total: 31/31 passed (100%)
- `gen-plans-index.sh --check` drift 없음

### 📁 Files Modified
- `scripts/gen_plans_index.py` (+27, -10 lines)
- `scripts/test_gen_plans_index.py` (+71, -1 lines)
- `.gitignore` (+3 lines)
- `docs/plans/_templates/design.md` (+1, -1 lines)
- `docs/plans/_templates/plan.md` (+1, -1 lines)
- `CLAUDE.md` (+1, -1 lines)

---

## [main] — 2026-06-04 — Android 프론트엔드 분석 + Plugin 에러 해결

### 🎯 Prompts
1. "`https://developer.android.com/develop/ui/compose/performance` 문서 분석하여 프로젝트에 적용 검토 후 memory에 작업해줘."
2. "아래 내용으로 프로젝트 설계 검토 작업해줘 [Clean Architecture + MVI + Multi-module 아키텍처]"
3. "아래 claude code plugin error 해결 방안 연구 설계 작업해줘 [10개 에러]"
4. "plugin error 해결 작업 진행해줘"

### ✅ Changes
- **Added**: `docs/plans/logs/android.md` — 8개 분석 엔트리 추가 (+1469 lines)
  - Compose 퍼포먼스 공식 문서 기반 성능 점검 (13항목 체크리스트, P1 double padding 버그 발견)
  - Clean Architecture + MVI + Multi-module 아키텍처 설계 검토 (6개 gap 식별, 7-phase 마이그레이션 로드맵)
  - Claude Code Plugin Errors 진단 및 해결 방안 (근본 원인 분석 + 4개 조치 설계)
  - HomeScreen 레이아웃 UX/UI, UDF 디자인 패턴, 프론트엔드 전수 분석, 의존성 LTS, 빌드 환경 검토
- **Fixed**: Claude Code plugin 에러 10건 해결
  - 마켓플레이스 `claude-plugins-official` re-clone (`.git` 누락 → 9개 "not found" 해소)
  - `~/.claude/plugins/blocklist.json` — `code-review` 테스트 항목 제거
  - `.claude/settings.json` — `vtsls@claude-code-lsps` 제거 (Android 프로젝트에 TS LSP 불필요)
  - `~/.claude/settings.json` — `vtsls@claude-code-lsps: false` (글로벌 비활성화)

### 📊 Verification
- 재시작 후 plugin error 0건 확인

### 📁 Files Modified
- `docs/plans/logs/android.md` (+1469 lines)
- `.claude/settings.json` (+1, -2 lines)
- `~/.claude/settings.json` (global, vtsls false)
- `~/.claude/plugins/blocklist.json` (global, code-review 제거)
- `~/.claude/plugins/marketplaces/claude-plugins-official/` (global, re-clone)
- `docs/CHANGELOG.md` (this entry)

---

## [main] — 2026-06-03 — Azure Monitor Alerts 프로비저닝 (P1+P2)

### 🎯 Prompts
1. "Implement the following plan: Azure Monitor Alerts (P1+P2) 설계 및 적용"
2. "push it"
3. "firewall alert 이메일 왔는지 확인해봐"
4. "CLAUDE.md에 setup-azure-alerts.sh 추가해줘"

### ✅ Changes
- **Added**: `scripts/setup-azure-alerts.sh` — idempotent Azure CLI 스크립트 (`--dry-run`, `--delete`, `--help`). MSYS path conversion 방지 포함
  - Action Group `ag-eundunhealth-prod` (email → `qkr133456@gmail.com`)
  - P1 Activity Log alerts 3개: ServiceHealth, ResourceHealth, Deletion (무료)
  - P2 Metric alerts 4개: PG CPU/Storage/Connections, CA 5xx (~$0.40/월)
  - P2 Activity Log alert 1개: PG Firewall 변경 (무료)
- **Added**: `docs/plans/2026-06-03-azure-monitor-alerts-design.md` — 설계 문서 (D1~D8 의사결정, 옵션 비교, 검증 계획, 롤백 절차)
- **Modified**: `docs/ops/monitoring-and-cost.md` — §7 Alert 섹션 신설, §4 비용 갱신 (+~700원), §5 체크리스트에 alert 확인 항목 추가
- **Modified**: `docs/ops/operations-snapshot.md` — §12 Alert 인벤토리 신설 (8개 alert 테이블), §9 비용 갱신, §13 변경 이력 추가
- **Modified**: `CLAUDE.md` — 자동화 스크립트 섹션에 `setup-azure-alerts.sh` 항목 추가

### 📊 Verification
- 스크립트 실행 결과: metric alert 4개 + activity log alert 4개 = 총 8개 정상 생성
- PG Firewall alert 실측 테스트: temp rule 생성/삭제 → email 3통 수신 확인 (Gmail MCP 검증)
- Action Group 파이프라인 end-to-end 정상 동작

### 📁 Files Modified
- `scripts/setup-azure-alerts.sh` (+385 lines, new)
- `docs/plans/2026-06-03-azure-monitor-alerts-design.md` (+149 lines, new)
- `docs/ops/monitoring-and-cost.md` (+65, -2 lines)
- `docs/ops/operations-snapshot.md` (+33, -2 lines)
- `docs/plans/README.md` (+4, -2 lines, auto-generated)
- `CLAUDE.md` (+1 line)
- `docs/CHANGELOG.md` (this entry)

---

## [main] — 2026-06-03 — PowerShell 7.6 LTS 전환 + atomic commit 워크플로우

### 🎯 Prompts
1. "Implement the following plan: PowerShell 7 전환: 프로젝트 설정 적용 설계"
2. "지금까지 한 두 커밋 squash 해줘" → main force push 불가 확인 → 추천 방식 연구 요청
3. "atomic commit 워크플로우: changelog amend 패턴 설계" (plan → 구현)

### ✅ Changes
- **Modified**: CLAUDE.md PowerShell 섹션을 7.6 LTS 기준으로 리팩토링 (`CLAUDE.md:283-325`)
  - 버전 명시: "PowerShell 7(`pwsh`)" → "**PowerShell 7.6 LTS** (`pwsh.exe`, .NET 10)"
  - `pwsh.exe` vs `powershell.exe` 실행 파일 구분, UTF-8 인코딩, ConciseView, Profile 경로
  - 7.x 신규 연산자 표 (ternary, `??`, `??=`, null-conditional, `-Parallel`)
  - WMI cmdlet 제거 (`Get-WmiObject` → `Get-CimInstance`)
- **Modified**: 자동화 스크립트 섹션에 bash 유지 사유 추가 (`CLAUDE.md:345`)
- **Added**: Commit / Push 워크플로우 컨벤션 (`CLAUDE.md:276-281`)
  - main 직접 작업 시 changelog 포함 후 1회 push, amend 패턴, fallback squash
- **Added**: `/changelog` 스킬 amend 패턴 전환 (`.claude/skills/changelog/SKILL.md`)
  - push 상태 판별 (`@{push}..HEAD`) → 미push 시 amend 기본, push 후 별도 커밋
  - `docs/CHANGELOG.md` 경로 수정, 프로젝트 맞춤 템플릿, 불필요 Helper Script 섹션 제거

### 📁 Files Modified
- `CLAUDE.md` (+30, -3 lines)
- `.claude/skills/changelog/SKILL.md` (+280 lines, new)
- `docs/CHANGELOG.md` (this entry)

---

## v0.1.7 — 2026-05-30 (versionCode 21) — LoginScreen + ForgotPasswordScreen 룰 8 적용

### Changed
- **LoginScreen 룰 8 적용**: Snackbar 단독 → inline `AuthErrorBanner` (persistent + a11y liveRegion + Sentry breadcrumb). password input 아래 / "로그인" 버튼 위 (D3). `LaunchedEffect(formValid, lastError)` 가 input 보완 시점 자동 dismiss (D4). EmailNotConfirmed 만 기존 inline 재전송 UI 보존 (Option A, D2). resendError 도 EmailNotConfirmed 영역 아래 같은 Banner 재사용 (D10). Sentry breadcrumb `screen=login` / `login_resend`.
- **ForgotPasswordScreen opError Banner 통합**: `LaunchedEffect(opError)` snackbar 제거 → Banner (`screen=forgot_password`). `LaunchedEffect(formValid, opError)` dismiss (D5). `passwordResetSent` 성공 snackbar 는 그대로 유지 (룰 8 예외 — 비-critical 성공).

### Refactored
- **`AuthErrorBanner` promote**: `ui/auth/SignupScreen.kt` 내 `private @Composable` → `ui/components/AuthErrorBanner.kt` public composable. 3 Auth 화면 (Signup, Login, ForgotPassword) 공유. 룰 8 의 "두 번째 화면 마이그레이션 시점에 promote" 트리거 (CLAUDE.md). SignupScreen 호출부는 동일 시그니처 → 동작 변경 없음.

### Why
INC-2026-05-26-01 의 가시성 결함을 SignupScreen 외 Login + ForgotPassword 에도 일관 적용. CLAUDE.md 룰 8 (PR #60, 2026-05-30) 등재 후 **첫 다중 화면 마이그레이션 사례**. 룰 8 의 4 요소 (inline + persistent + a11y + Sentry) 모두 만족.

---

## v0.1.6 — 2026-05-29 (versionCode 20) — Signup Failed UX inline error banner

### Fixed
- **INC-2026-05-26-01 가시성 결함 해소**: Signup 화면의 Failed 상태가 하단 snackbar 2초 자동 dismiss 로 사용자 인지 부족하던 문제 (실기기 검증 중 발견). RFC 작성 후 review 12 개선 통합 (D1~D12). `AuthErrorBanner` 를 form headline 아래/email input 위에 표시. dismiss = button enabled (모든 validation pass) 시점 자동 (D1). resendError 도 같은 Banner 재사용 (D7). a11y liveRegion Polite + Sentry breadcrumb (`auth.error_banner_shown`).

### Added
- `AuthErrorBanner` private composable (SignupScreen.kt) — Material 3 `Surface(errorContainer)` + `Icon(ErrorOutline)` + `Text(userMessage)`. LoginScreen 등 다른 화면 마이그레이션 시점에 `ui/components/` 로 promote (D5 YAGNI).
- `AuthViewModel.clearSignupError()` — current state == `Failed` 시만 `Form` 전환, 그 외 silent no-op (D6 race 회피).
- `BuildConfig.MOCK_AUTH_ERROR` (debug-only) — 수동 검증 reproducibility. 사용: `./gradlew :app:assembleDebug -PMOCK_AUTH_ERROR=ratelimit` → `AuthRepositoryImpl.signUp` 가 강제 `AppError.Auth("요청이 너무 많습니다...")` 반환. release 빈 string + DEBUG short-circuit 으로 double-guard (D11).

### Changed
- `SignupScreen.kt`: `LaunchedEffect(signupState)` snackbar + delay + `resetSignupState` 블록 제거. `LaunchedEffect(resendError)` 제거. `SnackbarHostState` + `Scaffold.snackbarHost` 인프라 제거 (D12 — dead code).
- `SignupForm` 시그니처: `error: AppError?` + `onClearError: () -> Unit` 추가. `LaunchedEffect(formValid, error)` 가 button enabled 시점에 `onClearError()` 호출.
- `AwaitingConfirmationCard` 시그니처: `resendError: AppError?` 추가. `onResend` 가 `clearResendError()` + `resendConfirmation()` 둘 다 호출.
- `AuthRepositoryImpl.signUp`: expression body 의 if-else 분기로 mock 통합 (return 사용 불가, if-then-else try-catch 구조).

### Test
- `AuthViewModelTest.kt` +2: `clearSignupError` Failed → Form 전환 + Form 상태 no-op (D6).
- Compose UI test (Banner / SignupScreen) 는 Out-of-scope (D2) — `androidTest/` 인프라 부재. 별도 RFC.

### Refs
- PR: #58
- Design + Plan: `docs/plans/2026-05-29-signup-error-banner-{design,plan}.md` (머지 후 `logs/android.md` entry 로 흡수 + git rm)
- Supersedes RFC: `docs/plans/2026-05-27-signup-failed-ux-visibility-rfc.md`
- INC: `docs/ops/incident-log.md` INC-2026-05-26-01

---

## v0.1.5 — 2026-05-29 (versionCode 19) — Vico 3.1 + healthConnect stable + starlette 1.1.0

### Changed
- **Vico 차트 라이브러리 2.1.0 → 3.1.0** (#52): v3 의 변경은 import 경로 (`com.patrykandpatrick.vico.core.cartesian.*` → `compose.cartesian.*`) 만, builder DSL 은 v2.1 과 동일. dependabot #39 (close) follow-up 정식 마이그레이션. 2 commit 분리 보존 — Commit 1 minimal import migration, Commit 2 opportunistic 개선 (`LineCartesianLayer.Interpolator.catmullRom` 으로 line 부드럽게 + `VerticalAxis.rememberStart(tickPosition = BaseAxis.TickPosition.Inside)` 로 chart 영역 활용성 ↑). 실기기 시각 검증 통과.
- **Health Connect 1.1.0-rc01 → 1.1.0 stable** (#53): 2025-10-08 stable 출시 (rc03 → stable 승격, API 변경 없음). `dependency-deferred.md §3` 보류 종료. dependabot #35 follow-up.
- **starlette 0.49.1 → 1.1.0** (#54): fastapi 0.136.1 의 starlette 의존성 범위가 `>=0.46.0` (상한 없음) 이라 1.x 허용 확인. starlette 1.1.0 이 `PYSEC-2026-161` fix 포함 → `backend.yml` 의 `--ignore-vuln PYSEC-2026-161` 옵션 제거 (직접 fix 됨). `app/main.py` 의 `add_middleware` 는 INC-2026-05-24-03 fix 후 모듈 레벨 등록이라 starlette 1.x lifespan 정책 무관. 로컬 pytest 44 PASS + docker compose runtime-smoke `/health` 200 + CI runtime-smoke 통과. `dependency-deferred.md §2` 보류 종료. dependabot #9 follow-up.

### Infrastructure
- **detekt baseline drift 1차 해소** (#52 동반): `config/detekt/baseline.xml` (git tracked) vs `config/detekt/baseline-debug.xml` (.gitignore) 의 task 갱신 비대칭으로 PR #42 부터 main 의 detekt 가 chronic failure 였던 것을 baseline 재생성으로 해소. drift 자체의 구조적 fix 는 TODO (기록: `~/.claude/.../memory/detekt-baseline-drift.md`).
- **dependency-deferred 정리** (#55): `docs/ops/dependency-deferred.md` 의 §2 (starlette) + §3 (healthConnect) 삭제, §1 (kotlin 2.3) 에 상태 점검 메모 추가. 남은 블로커: Hilt 2.59.3+ 출시 대기 (최신 2.59.2 from 2026-02-20).

### Refs
- PRs: #52 vico migration / #53 healthConnect 1.1.0 stable / #54 starlette 1.1.0 / #55 kotlin docs
- Design+plan: `docs/plans/2026-05-28-vico-3-migration-design.md` + `-plan.md`
- 트리거: 2026-05-28 dependabot 8 PR triage 세션 (PR #50 plan)
- 보류 정책: `docs/ops/dependency-deferred.md`

---

## v0.1.4 — 2026-05-26 (versionCode 18) — Hotfix: redirect URL path

### Fixed
- **App Links 자동 로그인 미작동 hotfix**: v0.1.3 실기기 검증 결과, Supabase Console 의 Site URL 에 `/auth/confirm` path 를 포함시켜도 redirect_to 파라미터에는 origin(host) 만 사용됨이 확인됨. 메일 링크가 `https://host/?code=...` (root path) 로 와서 Android intent-filter (`pathPrefix=/auth/confirm`) 가 매칭되지 않아 App Links autoVerify 가 작동 못 함.
- `AuthRepositoryImpl.signUp` 의 `signUpWith(Email)` 호출에 `redirectUrl = "https://${BuildConfig.APP_LINKS_HOST}/auth/confirm"` 명시 전달 — 클라이언트가 path 까지 포함된 redirect URL 을 지정.
- `AuthRepositoryImpl.resendConfirmation` 의 `resendEmail` 호출에도 동일한 `redirectUrl` 전달 (재전송 메일에도 같은 형식 보장).

### 운영 수동 절차 (v0.1.4 머지 후 1회)
- Supabase Dashboard → Authentication → URL Configuration → **Additional Redirect URLs** allowlist 에 다음 URL 추가:
  ```
  https://eundunhealth-api.livelyriver-782a792f.koreacentral.azurecontainerapps.io/auth/confirm
  ```
  명시 redirectUrl 이 honor 되려면 allowlist 에 등록 필수 (Supabase 보안 정책).

### Refs
- 진단: v0.1.3 Phase 5 Step 4 디바이스 검증 중 발견
- 원본 design: `docs/plans/2026-05-26-applinks-deep-link-design.md`

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
