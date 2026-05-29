---
type: design
status: proposed
pr: null
related_inc: INC-2026-05-26-01
supersedes: 2026-05-27-signup-failed-ux-visibility-rfc
target_version: 0.1.6
ledger_topic: android
tags: [android, ux, auth, signup]
---

# Signup Failed UX 가시성 — Inline Error Banner 설계

- **작성일**: 2026-05-29
- **상태**: 작성 중 (RFC review 결과 12 개선 사항 통합 후 사용자 승인 대기)
- **트리거 인시던트**: INC-2026-05-26-01 (Supabase 무료 등급 rate limit + Failed UX 가시성 부족)
- **연관 RFC**: `docs/plans/2026-05-27-signup-failed-ux-visibility-rfc.md` — 본 design 머지 시점에 status → `superseded`, `superseded_by` → 본 design
- **대상 버전**: **v0.1.6 (versionCode 20)**
- **선행 작업**: 없음 (독립). v0.1.5 (#56) 머지 완료된 main HEAD 위에서 시작.

## 1. 배경

### 1.1 INC-2026-05-26-01 요약
v0.1.4 실기기 검증 중 사용자가 "가입 버튼 누름 → 무반응" 으로 인식. 실제 백엔드는 `over_email_send_rate_limit` 응답 + `mapAuthError` 가 한국어 메시지로 매핑 + 하단 스낵바 표시했지만 사용자는 인지 못함.

### 1.2 가시성 결함 3축 (RFC §1)
1. **노출 시간 부족** — `SnackbarDuration.Short` (4초) 기본이지만 `SIGNUP_FAILURE_AUTO_DISMISS_MS = 2_000L` 후 `resetSignupState()` 가 state 전환 → `LaunchedEffect` 재시작 → snackbar 실제 표시 시간 단축
2. **위치** — 화면 하단 스낵바, 사용자 시선은 CTA 버튼/form input 근처
3. **자동 소멸** — 사용자가 다른 곳 보면 흔적 없음. 재시도 시 메시지 다시 확인 불가

### 1.3 RFC review (2026-05-29) 결과
본 design 은 RFC 의 Option B 채택 위에서 review 결과 12 개선 사항 inline 통합:
- **Critical 3**: dismiss 정책 정밀화 / Compose UI test 인프라 부재 명시 / 본 design 의 scope = 가시성 만 (메시지 의미는 별도)
- **Medium 4**: Option D 비교 추가 후 기각 / Banner private vs ui/components/ 결정 / clearSignupError race 회피 / resendError 일관성
- **Low 5**: 수동 검증 reproducibility / AppError sub-types 분기 v2 deferred / plans hybrid 컨벤션 적용 / RFC lifecycle 명시 / ROI 데이터

### 1.4 본 design 의 한계 (Out-of-scope 명시)
본 design 은 **가시성** 만 해결. 다음은 별도 RFC:
- **메시지 의미 / 액션 가이드** — "잠시 후" 의 모호함, retry 시점 가이드 부재
- **AppError sub-types 별 differential UX** — `AppError.Network` 의 retry button 등
- **LoginScreen / ForgotPasswordScreen 일관성** — 같은 패턴 마이그레이션
- **Compose UI test 인프라 도입** — `androidTest/` 폴더 부재, 별도 설치 PR

본 design 의 ROI: production 사용자 0 (internal testing 단계), 정식 출시 시 cognitive overload 영향 ↑ 예상.

## 2. Scope

### In-scope
1. **`AuthErrorBanner`** — `SignupScreen.kt` 안 `@Composable private fun` (Medium #5, YAGNI). LoginScreen 마이그레이션 시점에 `ui/components/` promote
2. **`AuthViewModel.clearSignupError()`** — current state == `Failed` 일 때만 `Form` 전환, 그 외 silent no-op (Medium #6, race 회피)
3. **`SignupScreen` 리팩터** — `LaunchedEffect(signupState)` 의 snackbar+delay+reset 제거. Banner 가 `SignupForm` 안에 통합. dismiss = button enabled (모든 validation 통과) 시점까지 유지 (Critical #1)
4. **`resendError` 처리** — 같은 `AuthErrorBanner` 재사용. 위치 = `AwaitingConfirmationCard` 의 headline 아래, "메일 다시 보내기" 버튼 위 (Medium #7)
5. **accessibility** — `Modifier.semantics { liveRegion = LiveRegionMode.Polite }` (TalkBack 알림)
6. **Sentry instrumentation** — Banner 노출 시점 `Sentry.addBreadcrumb` (디버깅 추적)
7. **수동 검증 reproducibility** — `app/build.gradle.kts` debug buildType 에 `BuildConfig.MOCK_AUTH_ERROR` flag (Low #8). debug 빌드만 `AuthRepositoryImpl` 가 강제 Failed 주입
8. **versionCode 19 → 20 / versionName 0.1.5 → 0.1.6**
9. **docs 동기 갱신**: `CLAUDE.md` / `docs/PRD.md` / `docs/SPEC.md` / `docs/ops/operations-snapshot.md` / `docs/CHANGELOG.md` (v0.1.6 entry)
10. **단위 test** — `AuthViewModelTest.kt` 의 `clearSignupError` 테스트 +2 (Failed → Form / 다른 state → no-op)
11. **plans hybrid 적용** — design+plan 페어 → 머지 후 `logs/android.md` entry 통합 + 페어 git rm (Low #10). hybrid 컨벤션 첫 검증 사례
12. **RFC lifecycle** — 본 design 머지 시점에 RFC frontmatter: status → `superseded`, `superseded_by` → 본 design (Low #11). 단 RFC 도 페어 git rm 대상 (logs/android.md entry 의 supersede chain 으로 history 보존)

### Out-of-scope (별도 작업)
- **Compose UI test 인프라** (Critical #2): `androidTest/` 폴더 + Hilt instrumented runner + Compose Test 의존성 — separate setup PR. 본 design 은 ViewModel 단위 test 만
- **LoginScreen / ForgotPasswordScreen 마이그레이션** (Low #11): `AuthErrorBanner` promote to `ui/components/` + 각 화면 Banner 통합 — 별도 RFC
- **AppError sub-types 별 differential UX** (Low #9): v2. 본 design 은 모든 AppError → 단일 banner
- **AppError 의 `actionGuide: String?` 필드** (Low #13): 메시지 의미 개선 — 별도 RFC
- **메시지 의미 / 액션 가이드 개선** (Critical #3 의 본 design 한계): "잠시 후" → "정확히 N분 후" 등 — 별도 RFC

## 3. 의사결정 요약

| # | 결정 | 채택 | 근거 |
|---|---|---|---|
| D1 | Critical #1 dismiss 정책 | **button enabled (모든 validation 통과) 시점까지 banner 유지**. input 변경 시 dismiss 안 함 | 1글자 typo 수정으로 메시지 사라지는 UX 회귀 회피. 사용자 의도 = "재시도 준비됨" 시점이 button enabled 상태와 일치 |
| D2 | Critical #2 Compose UI test 인프라 | **본 design 도입 X**. ViewModel 단위 test 만. UI test 는 후속 별도 RFC | `androidTest/` 인프라 도입 = significant overhead (Compose Test 의존성 + Hilt instrumented runner + 첫 device test). YAGNI + v0.1.6 scope 작게 |
| D3 | Critical #3 원인 가정 | **본 design = 가시성 만 해결**. 메시지 의미 / 액션 가이드 / sub-types 분기 / 다른 화면 = 모두 별도 RFC | 한 design 의 단일 책임. 부분 해결 ≠ 완전 해결, 단 명시적 한계로 두면 ROI 추적 가능 |
| D4 | Medium #4 Option 비교 | **Option B inline banner 채택** (RFC §4 유지). Option D (TextField isError) 는 §4 표에 추가 비교 후 기각 | Option D 는 어느 field 의 error 인지 매핑 모호 (rate limit = email? password?). Banner 가 명확. Option A (snackbar Long+dismissAction) 는 위치 문제 그대로 |
| D5 | Medium #5 Banner 위치 | **`SignupScreen.kt` 안 `@Composable private fun AuthErrorBanner(...)`** 시작. LoginScreen 마이그레이션 시점에 `ui/components/` promote | YAGNI. 추후 이동 비용 (import 변경뿐) 낮음. premature generic 회피. RFC 의 "재사용 가능 generic" 은 LoginScreen RFC 가 trigger |
| D6 | Medium #6 `clearSignupError()` 명세 | **current state == `Failed` 일 때만 `Form` 전환, 그 외 silent no-op** | Loading 중 호출 시 race 회피 (Loading → Form 잘못 전환 방지). `resetSignupState()` 와 책임 분리: 전자 = AwaitingEmailConfirmation 의 onGoToLogin 경로, 후자 = Failed 해소 |
| D7 | Medium #7 `resendError` 처리 | **Yes**, 같은 `AuthErrorBanner` 재사용. 위치 = `AwaitingConfirmationCard` 의 headline ("메일을 보냈습니다") 아래, "메일 다시 보내기" 버튼 위 | RFC §5 본문 ("검토") vs §8 #4 ("Yes") 모순 해소. 같은 컴포넌트 = 일관 UX |
| D8 | Low #11 RFC lifecycle | **본 design 머지 시점에 RFC frontmatter**: status → `superseded`, `superseded_by` → 본 design. RFC 도 페어 git rm + ledger entry 흡수 | plans hybrid 컨벤션 일관. supersede chain 은 logs/android.md entry 의 "supersedes RFC" link 로 history 보존 |
| D9 | dismiss 후 상태 전환 책임 | **ViewModel 이 owns**. UI 는 user action (button click, validation pass) 발생 시 ViewModel call → ViewModel 이 state 전환 | Compose state hoisting 원칙. UI 는 stateless |
| D10 | Sentry breadcrumb category | **`auth.error_banner_shown`** + level=info, data={`error_type`: AppError sub-type, `screen`: signup/awaiting} | 통일 prefix 로 Sentry 검색 용이성. error 자체는 별도 issue 로 capture (기존 동작) |
| D11 | BuildConfig MOCK_AUTH_ERROR 안전성 | **debug buildType = `findProperty("MOCK_AUTH_ERROR") ?: ""`, release buildType = 항상 빈 string** (`buildConfigField "String" "MOCK_AUTH_ERROR" "\"\""`). `AuthRepositoryImpl` 의 분기 = `BuildConfig.DEBUG && BuildConfig.MOCK_AUTH_ERROR == "ratelimit"` — release 에서는 DEBUG=false 로 short-circuit + field 도 빈 string 이라 double-guard | production mock 우회 위험 회피. 빈 string 으로 명시한 이유 = release 의 `AuthRepositoryImpl` 가 같은 field 참조하므로 compile error 회피 필요 |
| D12 | Snackbar 인프라 제거 | **`SignupScreen` 의 `SnackbarHostState` + `Scaffold` 의 `snackbarHost` 모두 제거** (본 design 에서 dead code) | YAGNI — 향후 다른 알림 필요해질 때 그 시점 명확한 의도로 재도입. Banner 가 본 화면의 모든 에러 처리 transport. import `material3.SnackbarHost` / `SnackbarHostState` 도 삭제 |

## 4. 옵션 비교

| 옵션 | A. snackbar Long + dismissAction | **B. inline banner ⭐** | C. 하이브리드 (A+B) | D. TextField isError + supportingText |
|---|---|---|---|---|
| 가시성 | 중 (10초 + 사용자 dismiss) | **고** (form 안, button 위) | 고 (중복) | 중 (input 옆이지만 작음) |
| 인지 보장 | X (시선 외 위치 그대로) | **O** (CTA 위) | O (중복으로) | X (어느 field 인지 불명) |
| UI 비용 | 1줄 변경 | ~30줄 (Banner + ViewModel) | ~40줄 | 10줄 (field 매핑 추가) |
| dismiss 명확성 | 사용자 명시 또는 timeout | **button enabled / validation** | 두 정책 혼란 | input change 자동 |
| 일관성 (resendError) | snackbar 동일 | **같은 Banner 재사용** | 둘 다 | TextField 매핑 모호 (resend 는 textfield 없음) |
| LoginScreen 재사용 | 동일 (1줄) | **promote 시 가능** | 둘 다 | 어려움 |
| Material 3 가이드 | 표준 | 표준 (`Surface` + `errorContainer`) | 위반 위험 (alert 2개) | 표준 |

→ **B 채택** (D2~D5, D7). D 의 field 매핑 모호성 + LoginScreen 재사용 어려움이 결정적.

## 5. 구성 요소별 변경

### 5.1 NEW: `SignupScreen.kt` 안 `AuthErrorBanner` private composable

```kotlin
@Composable
private fun AuthErrorBanner(
    error: AppError,
    modifier: Modifier = Modifier,
) {
    // Sentry breadcrumb on first composition
    LaunchedEffect(error) {
        Sentry.addBreadcrumb(
            io.sentry.Breadcrumb().apply {
                category = "auth.error_banner_shown"
                level = io.sentry.SentryLevel.INFO
                setData("error_type", error::class.simpleName ?: "Unknown")
                setData("screen", "signup")
            }
        )
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,  // Banner 의 text 가 이미 의미 전달
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = error.userMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}
```

### 5.2 MODIFY: `SignupScreen.kt` 의 `LaunchedEffect` + `SignupForm` 시그니처

**제거:**
- Line 42: `private const val SIGNUP_FAILURE_AUTO_DISMISS_MS = 2_000L`
- Line 54-59: `LaunchedEffect(signupState)` 의 snackbar + delay + resetSignupState 블록 전체
- Line 60-64: `LaunchedEffect(resendError)` (resendError 처리도 Banner 통합)
- import `kotlinx.coroutines.delay`

**제거 (D12):**
- `SnackbarHostState` 자체 + import `material3.SnackbarHost` / `SnackbarHostState` — 본 design 의 Banner 가 모든 에러 transport, snackbar 호출 없음 → dead code
- `Scaffold` 의 `snackbarHost = { SnackbarHost(snackbarHostState) }` 인자 제거

**변경:**
```kotlin
// SignupScreen.kt body
@Composable
fun SignupScreen(
    onNavigateToLogin: () -> Unit,
    authViewModel: AuthViewModel,
) {
    val signupState by authViewModel.signupState.collectAsState()
    val resendCooldownSec by authViewModel.resendCooldownSec.collectAsState()
    val resendError by authViewModel.resendError.collectAsState()

    Scaffold { padding ->
        when (val state = signupState) {
            is SignupState.AwaitingEmailConfirmation -> AwaitingConfirmationCard(
                email = state.email,
                cooldownSec = resendCooldownSec,
                resendError = resendError,
                onResend = {
                    authViewModel.clearResendError()
                    authViewModel.resendConfirmation(state.email)
                },
                onGoToLogin = {
                    authViewModel.setPendingEmail(state.email)
                    authViewModel.resetSignupState()
                    onNavigateToLogin()
                },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            else -> SignupForm(
                isLoading = signupState is SignupState.Loading,
                error = (signupState as? SignupState.Failed)?.error,
                onClearError = authViewModel::clearSignupError,
                onSubmit = { email, password ->
                    authViewModel.signup(email.trim(), password)
                },
                onNavigateToLogin = onNavigateToLogin,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}
```

**`SignupForm` 시그니처 변경:**
```kotlin
@Composable
private fun SignupForm(
    isLoading: Boolean,
    error: AppError?,                       // 신규
    onClearError: () -> Unit,               // 신규
    onSubmit: (email: String, password: String) -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }

    val formValid = email.isNotBlank() &&
        password.length >= 6 &&
        password == confirmPassword

    // D1: button enabled (= validation pass) 시점 자동 dismiss
    LaunchedEffect(formValid) {
        if (formValid && error != null) {
            onClearError()
        }
    }

    Column(...) {
        Text("회원가입", ...)
        Spacer(modifier = Modifier.height(32.dp))

        // 신규: error banner (headline 아래, email input 위)
        error?.let {
            AuthErrorBanner(error = it)
            Spacer(modifier = Modifier.height(16.dp))
        }

        OutlinedTextField( /* email */ )
        // ... 나머지 그대로
    }
}
```

### 5.3 MODIFY: `AwaitingConfirmationCard` — resendError Banner 통합

```kotlin
@Composable
private fun AwaitingConfirmationCard(
    email: String,
    cooldownSec: Int,
    resendError: AppError?,                 // 신규
    onResend: () -> Unit,                   // 기존 (단 ViewModel.clearResendError() 호출 추가)
    onGoToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(24.dp), ...) {
        Text("메일을 보냈습니다", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        // 신규: resend error banner (headline 아래, "메일 다시 보내기" 위)
        resendError?.let {
            AuthErrorBanner(error = it)
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text("$email 로 인증 메일을 보냈습니다.\n...", ...)
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedButton(onClick = onResend, ...) { Text(...) }
        // ...
    }
}
```

### 5.4 MODIFY: `AuthViewModel.kt`

**신규 함수 (line 73 `resetSignupState()` 근처):**
```kotlin
/**
 * Signup Failed 상태를 Form 으로 전환. 다른 상태에서는 no-op (D6 race 회피).
 *
 * 호출 시점:
 * - SignupForm 의 validation pass (button enabled) 시 자동 (D1)
 * - 사용자가 명시적 retry click 시 (signup() 호출 직전)
 */
fun clearSignupError() {
    if (_signupState.value is SignupState.Failed) {
        _signupState.value = SignupState.Form
    }
    // 다른 state 에서 silent no-op (Loading 중 호출 시 race 회피)
}
```

`resetSignupState()` 는 그대로 (AwaitingEmailConfirmation 의 onGoToLogin 경로용).

### 5.5 MODIFY: `app/build.gradle.kts` — versionCode bump + BuildConfig MOCK_AUTH_ERROR

**versionCode + versionName (line 77-78):**
```kotlin
versionCode = 20
versionName = "0.1.6"
// 주석 history 에 추가:
// 20: v0.1.6 — Signup Failed UX inline error banner (INC-2026-05-26-01 후속).
```

**buildTypes.debug 안 BuildConfig field (debug-only mock, D11):**
```kotlin
buildTypes {
    debug {
        // ... 기존 설정
        buildConfigField(
            "String",
            "MOCK_AUTH_ERROR",
            "\"${project.findProperty("MOCK_AUTH_ERROR") ?: ""}\""
        )
    }
    release {
        // MOCK_AUTH_ERROR 미정의 → release 빌드에서 사용 시 compile error → leak 차단
    }
}
```

사용: `./gradlew :app:assembleDebug -PMOCK_AUTH_ERROR=ratelimit` → debug 빌드의 `AuthRepositoryImpl.signup` 가 강제 `Result.failure(AppErrorException(AppError.Auth("요청이 너무 많습니다. 잠시 후 다시 시도해주세요")))` 반환.

### 5.6 MODIFY: `AuthRepositoryImpl.kt` — debug mock 분기 (5.5 의 BuildConfig 활용)

```kotlin
override suspend fun signUp(...): Result<SignupResult> {
    // Debug-only mock 분기 (production 빌드 = BuildConfig.MOCK_AUTH_ERROR 미정의 → compile error)
    if (BuildConfig.DEBUG && BuildConfig.MOCK_AUTH_ERROR == "ratelimit") {
        return Result.failure(
            AppErrorException(AppError.Auth("요청이 너무 많습니다. 잠시 후 다시 시도해주세요"))
        )
    }
    // 기존 로직 그대로
    ...
}
```

(다른 mock variant 도 동일 패턴 추가 가능: `network`, `email_invalid` 등 — 본 design 은 `ratelimit` 만 명시, 나머지는 후속)

### 5.7 MODIFY: `AuthViewModelTest.kt` — `clearSignupError` 단위 test (+2)

```kotlin
@Test
fun `clearSignupError transitions Failed to Form`() = runTest {
    // given: _signupState.value = SignupState.Failed(AppError.Auth("test"))
    viewModel.simulateFailed(AppError.Auth("test"))  // test helper 가 직접 _signupState set
    // when
    viewModel.clearSignupError()
    // then
    assertThat(viewModel.signupState.value).isEqualTo(SignupState.Form)
}

@Test
fun `clearSignupError is no-op when state is not Failed`() = runTest {
    // Loading 상태에서 호출 시 state 유지 (D6)
    viewModel.simulateLoading()
    viewModel.clearSignupError()
    assertThat(viewModel.signupState.value).isEqualTo(SignupState.Loading)

    // Form 상태에서도 마찬가지
    viewModel.simulateForm()
    viewModel.clearSignupError()
    assertThat(viewModel.signupState.value).isEqualTo(SignupState.Form)
}
```

test helper (`simulateFailed` / `simulateLoading` / `simulateForm`): `@VisibleForTesting internal fun` 으로 `_signupState.value =` setter — 또는 reflection. 본 design 은 `@VisibleForTesting` 선호.

### 5.8 MODIFY: docs 동기 갱신

- **`CLAUDE.md`**: Current state (line 9) + App version (line 159) — v0.1.6 (versionCode 20) 반영
- **`docs/PRD.md`** (line 3-4): 문서 버전 v1.3 → v1.4, 제품 버전 v0.1.5 → v0.1.6
- **`docs/SPEC.md`** (line 4): 현재 제품 상태 v0.1.5 → v0.1.6
- **`docs/ops/operations-snapshot.md`** (line 14): versionName/Code 표 갱신
- **`docs/CHANGELOG.md`**: v0.1.6 entry 추가 — Added (AuthErrorBanner, BuildConfig MOCK_AUTH_ERROR), Changed (SignupScreen 의 LaunchedEffect 제거), Fixed (INC-2026-05-26-01 가시성 결함 — banner 통합)

### 5.9 MODIFY: RFC frontmatter (PR 머지 직전)

`docs/plans/2026-05-27-signup-failed-ux-visibility-rfc.md`:
```yaml
status: superseded   # was: proposed
superseded_by: 2026-05-29-signup-error-banner-design
```

단 본 design 머지 시 RFC + 본 design+plan 페어 3 파일 모두 `logs/android.md` 의 v0.1.6 entry 로 흡수 + git rm. supersede chain 은 entry 의 본문에 명시.

### 5.10 NEW (PR 머지 후, plans hybrid 컨벤션 — D8): `logs/android.md` entry 통합

머지 후 별도 commit (또는 PR 의 마지막 commit):
```markdown
### 2026-05-29 — Signup Failed UX inline error banner (v0.1.6)

- **PR**: [#NN](url) (shipped, v0.1.6, **supersedes** [RFC 2026-05-27 signup-failed-ux-visibility](git history))
- **Why**: INC-2026-05-26-01 의 가시성 결함 (snackbar 2초 자동 dismiss + 하단 위치). RFC 작성 후 review 12 개선 사항 통합.
- **What**: `AuthErrorBanner` (SignupScreen 안 private) + `AuthViewModel.clearSignupError()` (Failed 시만 Form 전환) + dismiss 정책 (validation pass 시점) + `BuildConfig.MOCK_AUTH_ERROR` (debug-only reproducibility) + accessibility liveRegion + Sentry breadcrumb.
- **Outcome**: ...
- **Lessons**: (postmortem, 머지 + 7일 후)
- **Files touched**: ...
```

페어 (RFC + design + plan) 3 파일 git rm.

## 6. 검증 계획

### 6.1 자동
- `./gradlew :app:testDebugUnitTest --tests "*.AuthViewModelTest"` — clearSignupError 신규 test +2 PASS
- `./gradlew :app:assembleDebug` — compile + Spotless + Detekt
- `bash scripts/preflight-release.sh` — Spotless + Detekt + Tests + releaseArtifacts (AAB + APK)
- CI `android.yml` — Lint + Detekt + Test + Build green

### 6.2 수동 (반복 가능)
**Scenario 1: rate limit mock**
```bash
./gradlew :app:assembleDebug -PMOCK_AUTH_ERROR=ratelimit
adb install -r app/build/outputs/apk/debug/app-debug.apk
# SignupScreen 진입 → 이메일/비번 입력 → "가입하기" 클릭
# 기대: button 위에 AuthErrorBanner ("요청이 너무 많습니다...") 표시. 사라지지 않음.
# 사용자 input 변경 (e.g., 비번 1자 추가) → banner 그대로 (D1).
# 사용자가 validation 통과 (모든 input + confirm 일치) → button enabled → banner 자동 dismiss.
# 사용자가 button 재클릭 → 다시 mock 발동 → 다시 banner 표시.
```

**Scenario 2: TalkBack accessibility**
- Android 설정 → 접근성 → TalkBack ON
- 같은 SignupScreen 흐름
- 기대: banner 나타날 때 TalkBack 이 "요청이 너무 많습니다..." 즉시 읽음 (liveRegion Polite)

**Scenario 3: resendError 일관성**
- mock 으로 `signup` 성공 → AwaitingConfirmationCard 진입 → "메일 다시 보내기" 클릭 (cooldown 0초 후) → mock 으로 resendError 강제 → 같은 형식 banner 가 headline 아래 표시

**Scenario 4: Sentry breadcrumb**
- mock 으로 banner 표시 → 같은 세션에서 별도 에러 trigger (e.g., crash) → Sentry issue 의 breadcrumb timeline 에 `auth.error_banner_shown` 항목 확인

### 6.3 회귀
- v0.1.5 의 다른 흐름 정상 (Login / Onboarding / Home 등)
- AwaitingConfirmationCard 의 "메일 다시 보내기" 기존 동작 그대로 (cooldown 60초)

## 7. 롤백 절차

본 design 이 일으킨 변화:
- UI 코드 변경 (SignupScreen + AuthViewModel + Banner 신규)
- versionCode 19 → 20, versionName 0.1.5 → 0.1.6
- BuildConfig.MOCK_AUTH_ERROR field 추가 (debug)
- AuthRepositoryImpl debug mock 분기
- docs (5 파일) 갱신
- RFC + design + plan 페어 git rm + logs/android.md entry 추가

**전체 롤백** = `git revert <merge-commit>` 단일 액션. 모든 변경 일괄 되돌림 (페어 파일 복원 + ledger entry 제거).

**부분 롤백** = 특정 commit 만 revert. e.g., D1 의 "button enabled 시 dismiss" 가 UX 문제 발견 → 해당 commit 만 revert (input change 시 dismiss 로 대안 적용). commit 분리 권장.

**v0.1.6 → v0.1.5 시점 hotfix** (release 후): 만약 banner 가 visual regression 일으키면 versionCode 21 + 22 의 revert + 재배포. Play Console 의 동일 versionCode rollback 은 불가.

## 8. 잔여 리스크

| 리스크 | 영향 | 완화 |
|---|---|---|
| `LaunchedEffect(formValid)` 의 `onClearError()` 가 first composition 시 호출 → form 진입 직후 error 없는데 호출 (no-op, 안전) | 미미 | D6 의 no-op 보장 |
| Banner 가 form layout 첫 진입 사용자에게 jarring | 낮음 | animation 미적용 — 후속 RFC 에 `AnimatedVisibility` 추가 검토 |
| BuildConfig.MOCK_AUTH_ERROR 의 production leak | **중간** | D11 — release buildType 미정의 → compile error 차단. `AuthRepositoryImpl` 의 분기는 `BuildConfig.DEBUG &&` guard 추가 |
| `AuthViewModel.simulateFailed` 같은 test helper 가 production code 에 노출 | 낮음 | `@VisibleForTesting internal` 사용. ProGuard rule 영향 X (test 만 사용) |
| Compose UI test 부재 → manual 검증 의존 | **중간** | 본 design Out-of-scope. 별도 인프라 PR 후 followup RFC 로 Banner UI test 추가 |
| RFC + design + plan 3 페어 git rm 시 history 손실 우려 | 낮음 | git log + ledger entry 의 "supersedes" link 로 복원 가능. plans-ledger-restructure 의 hybrid 컨벤션 검증 |
| Sentry SDK 추가 import (`io.sentry.Breadcrumb`, `SentryLevel`) 의 obfuscation 회귀 | 낮음 | Sentry SDK 8.x 가 R8 호환 — v0.1.5 의 bundleRelease 가 이미 검증. 본 design 의 사용 패턴 동일 |
| dismiss 정책 (D1) 이 production 사용자에게 의도와 다를 가능성 | 미정 | 정식 출시 후 Sentry breadcrumb + telemetry 로 dismiss 시점 분포 추적 → 별도 RFC 로 조정 |
| LoginScreen 등 다른 화면이 같은 가시성 결함 가진 채 남음 | 중간 | Out-of-scope (별도 RFC). 본 design 의 Banner 가 promote 시 generic 컴포넌트로 재사용. ROI 가 명확해지면 우선순위 ↑ |
| RFC 가 본 design 의 작성 시점 (2026-05-29) 까지 status `proposed` 였으나 본 design 머지 시점에 superseded 됨 — pr: null 인 채 superseded | 낮음 | superseded 의 `pr` 은 본 design 의 PR # 으로 set (RFC 의 lifecycle 명세 추가 — D8) |

## 9. 참고 자료

- **연관 RFC (supersede 대상)**: `docs/plans/2026-05-27-signup-failed-ux-visibility-rfc.md`
- **트리거 INC**: `docs/ops/incident-log.md` INC-2026-05-26-01
- **페어링 plan (다음 단계)**: `docs/plans/2026-05-29-signup-error-banner-plan.md`
- **plans hybrid 컨벤션**: `docs/plans/README.md` 워크플로 + `docs/plans/logs/process-infra.md` 의 plans-ledger-restructure entry (2026-05-29)
- **이전 release 참조**: `docs/plans/logs/android.md` (vico 3.1 v0.1.5 entry — 마지막 Android UI 변경 사례)
- **테스트 패턴**: `app/src/test/java/com/gunnys/eundunhealth/ui/auth/AuthViewModelTest.kt` (기존 — 본 design 에서 +2)
- **Material 3 errorContainer**: https://m3.material.io/components/banners 및 https://m3.material.io/styles/color/roles
- **Compose accessibility liveRegion**: https://developer.android.com/jetpack/compose/accessibility#live-regions
- **Sentry breadcrumb (Android)**: https://docs.sentry.io/platforms/android/enriching-events/breadcrumbs/
