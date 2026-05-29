---
type: plan
status: proposed
pr: null
related_inc: INC-2026-05-26-01
supersedes: 2026-05-27-signup-failed-ux-visibility-rfc
target_version: 0.1.6
ledger_topic: android
tags: [android, ux, auth, signup]
---

# Signup Failed UX Inline Error Banner Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** INC-2026-05-26-01 의 Signup Failed UX 가시성 결함 (snackbar 2초 자동 dismiss + 하단 위치) 을 `AuthErrorBanner` (form 내 inline) 로 해결 + plans hybrid 컨벤션 첫 검증 사례. v0.1.6 (versionCode 20) release.

**Architecture:** `SignupScreen.kt` 안 `@Composable private fun AuthErrorBanner` (D5 YAGNI) + `AuthViewModel.clearSignupError()` (D6 Failed 시만 Form 전환) + dismiss 트리거 `LaunchedEffect(formValid)` (D1 button enabled 시점). resendError 도 같은 Banner 재사용 (D7, AwaitingConfirmationCard 안). `BuildConfig.MOCK_AUTH_ERROR` (D11 debug-only) 로 수동 검증 reproducibility. Sentry breadcrumb + a11y liveRegion.

**Tech Stack:** Kotlin 2.2.10 / Compose BOM 2026.05.01 / Material 3 / Hilt 2.59.2 / Sentry Android 8.42.0 / kotlinx-coroutines-test (기존 FakeAuthRepository 패턴).

**참고:**
- Design (페어): `docs/plans/2026-05-29-signup-error-banner-design.md` (D1~D12)
- Supersede 대상 RFC: `docs/plans/2026-05-27-signup-failed-ux-visibility-rfc.md`
- INC: `docs/ops/incident-log.md` INC-2026-05-26-01
- Branch: `feat/signup-error-banner` (이미 생성, working tree 에 design 만)
- Main HEAD: `8bfbb2b` (plans-ledger-restructure #57)

**중요 원칙:**
- TDD: ViewModel clearSignupError 는 red → green → commit
- Commit 분리 (D3 정신, plans hybrid 컨벤션): A docs / B core / C UI integration / D debug mock / E version+docs / F PR# fix / merge 후 G self-apply
- PR 머지는 `--merge` (squash 금지) — commit 분리 보존
- Windows host (PowerShell + Bash tool). bash command 사용.
- 본 작업이 plans hybrid 컨벤션 (2026-05-29 plans-ledger-restructure #57) 의 **첫 검증 사례** — 머지 후 페어 git rm + ledger entry 통합 워크플로 마찰점 발견 시 logs/android.md entry 의 Lessons 에 기록.

**Task 순서:**

```
Task 0  branch + working tree 확인
Task 1  Commit A: design + plan + RFC frontmatter (docs only)
Task 2  Commit B: AuthErrorBanner + AuthViewModel.clearSignupError + 단위 test 2건
Task 3  Commit C: SignupScreen LaunchedEffect/Snackbar 제거 + Banner 통합 + AwaitingConfirmationCard resendError
Task 4  Commit D: BuildConfig MOCK_AUTH_ERROR + AuthRepositoryImpl debug mock
Task 5  Commit E: versionCode bump + docs (5 파일) + CHANGELOG
Task 6  수동 검증 4 시나리오 (debug APK)
Task 7  preflight-release.sh + AAB 빌드
Task 8  push + PR + (Commit F) PR # fix + CI watch + --merge + tag v0.1.6 + main sync
Task 9  Self-apply: RFC + design + plan 페어 git rm + logs/android.md entry
Task 10 Postmortem 자리 (entry 의 Lessons — 머지 + 7일 후)
```

---

## Phase 1: 준비

### Task 0: Branch + working tree 확인

**Files:** read only

- [ ] **Step 0.1: branch + state 확인** (bash)

```bash
git branch --show-current
git status --short
git log --oneline -3
```

Expected:
```
feat/signup-error-banner
?? .claude/skills/
?? docs/plans/2026-05-29-signup-error-banner-design.md
8bfbb2b Merge pull request #57 from gunnysis/feat/plans-ledger-restructure
062c482 docs(plans): process-infra entry 의 PR # placeholder → 57 (PR fix-up)
29e2cd0 docs(plans): self-apply — plans-ledger-restructure 를 process-infra entry 통합 (Commit 4/4)
```

design 파일 untracked, working tree clean (plus .claude/skills/).

FAIL 시: branch 잘못 → `git checkout feat/signup-error-banner`. design 없음 → 이전 단계 미완 — 본 plan 중단, 사용자 확인.

---

## Phase 2: 변경 적용

### Task 1: Commit A — design + plan + RFC frontmatter + design D11 fix

**Files:**
- Add (untracked → tracked): `docs/plans/2026-05-29-signup-error-banner-design.md`
- Add (이번 commit 직전 작성): `docs/plans/2026-05-29-signup-error-banner-plan.md` (본 plan 자체)
- Modify: `docs/plans/2026-05-27-signup-failed-ux-visibility-rfc.md` (frontmatter)
- Modify: `docs/plans/2026-05-29-signup-error-banner-design.md` (D11 wording fix)

- [ ] **Step 1.0: design D11 wording fix — release 빈 string 전략 명시** (Edit)

design 작성 시 D11 = "compile error 로 leak 차단" 이라 표현했으나, plan 작성 중 Task 4 Step 4.3 의 실제 구현 검토 결과 release 빌드도 `BuildConfig.MOCK_AUTH_ERROR` 비교 시 reference 필요 → release buildType 에 빈 string field 명시 + `AuthRepositoryImpl` 의 `BuildConfig.DEBUG &&` short-circuit 으로 leak 차단이 더 정확. design D11 inline fix.

`docs/plans/2026-05-29-signup-error-banner-design.md`:

old_string:
```
| D11 | BuildConfig MOCK_AUTH_ERROR 안전성 | **debug buildType 만에 명시** (`buildTypes.debug { buildConfigField "..." }`). release 빌드 = build 자체 미정의 → 빌드 에러로 leak 차단 | production 빌드 mock 우회 위험 회피 |
```

new_string:
```
| D11 | BuildConfig MOCK_AUTH_ERROR 안전성 | **debug buildType = `findProperty("MOCK_AUTH_ERROR") ?: ""`, release buildType = 항상 빈 string** (`buildConfigField "String" "MOCK_AUTH_ERROR" "\"\""`). `AuthRepositoryImpl` 의 분기 = `BuildConfig.DEBUG && BuildConfig.MOCK_AUTH_ERROR == "ratelimit"` — release 에서는 DEBUG=false 로 short-circuit + field 도 빈 string 이라 double-guard | production mock 우회 위험 회피. 빈 string 으로 명시한 이유 = release 의 `AuthRepositoryImpl` 가 같은 field 참조하므로 compile error 회피 필요 |
```

- [ ] **Step 1.1: RFC frontmatter — status → superseded** (Edit)

`docs/plans/2026-05-27-signup-failed-ux-visibility-rfc.md` line 1-9 의 frontmatter 교체:

old_string:
```
---
type: rfc
status: proposed
pr: null
related_inc: INC-2026-05-26-01
supersedes: null
target_version: pending
tags: [android, ux]
---
```

new_string:
```
---
type: rfc
status: superseded
pr: null
related_inc: INC-2026-05-26-01
supersedes: null
superseded_by: 2026-05-29-signup-error-banner-design
target_version: pending
ledger_topic: android
tags: [android, ux]
---
```

(rfc 의 `pr: null` 그대로 — RFC 는 자체 PR 없음. `superseded_by` + `ledger_topic` 추가.)

- [ ] **Step 1.2: gen-plans-index 실행 (frontmatter validation)** (bash)

```bash
bash scripts/gen-plans-index.sh
```

Expected: `OK ... (active: N, superseded: 1, ledger_changed: False)`. RFC 가 superseded 그룹으로 이동.

FAIL 시 (e.g., `status=superseded requires superseded_by`): Step 1.1 의 frontmatter 형식 재검토. `superseded_by` 필드명 정확히 일치 확인.

- [ ] **Step 1.3: Commit A** (bash)

```bash
git add docs/plans/2026-05-29-signup-error-banner-design.md \
        docs/plans/2026-05-29-signup-error-banner-plan.md \
        docs/plans/2026-05-27-signup-failed-ux-visibility-rfc.md \
        docs/plans/README.md

git commit -m "$(cat <<'EOF'
docs(plans): signup-error-banner design + plan + RFC supersede (Commit A/E)

Plan: docs/plans/2026-05-29-signup-error-banner-{design,plan}.md.
INC: INC-2026-05-26-01.

design (D1~D12) — RFC review 12 개선 통합:
- Critical: dismiss = button enabled (D1) / Compose UI test out-of-scope (D2)
  / 본 design = 가시성만 (D3)
- Medium: Option D 기각 (D4) / Banner private 시작 (D5) / clearSignupError
  Failed 시만 (D6) / resendError 같은 Banner (D7)
- 추가: RFC supersede chain (D8) / state owner = ViewModel (D9) /
  Sentry breadcrumb category (D10) / BuildConfig leak 차단 (D11) /
  Snackbar 인프라 제거 (D12)

RFC (2026-05-27-signup-failed-ux-visibility-rfc.md): status proposed →
superseded, superseded_by = 본 design, ledger_topic = android 추가.

머지 후 self-apply: RFC + design + plan 3 페어 git rm + logs/android.md 의
v0.1.6 entry 로 흡수 (plans hybrid 컨벤션 첫 검증 사례).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

Expected: pre-commit hook 통과 (.kt 변경 없음 → spotless/detekt 빠르게 skip. gen-plans-index 통과). commit 성공.

---

### Task 2: Commit B — AuthErrorBanner + clearSignupError + 단위 test 2건

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/auth/SignupScreen.kt` (Banner 추가)
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/auth/AuthViewModel.kt` (clearSignupError)
- Modify: `app/src/test/java/com/gunnys/eundunhealth/ui/auth/AuthViewModelTest.kt` (단위 test +2)

- [ ] **Step 2.1: AuthViewModel.clearSignupError() 신규** (Edit)

`app/src/main/java/com/gunnys/eundunhealth/ui/auth/AuthViewModel.kt`:

old_string:
```kotlin
    fun resetSignupState() {
        _signupState.value = SignupState.Form
    }
```

new_string:
```kotlin
    fun resetSignupState() {
        _signupState.value = SignupState.Form
    }

    /**
     * Signup Failed 상태 해소 (Form 으로 전환). 다른 상태에서는 silent no-op (D6 race 회피).
     *
     * 호출 시점:
     * - SignupForm 의 validation pass (button enabled) 시 자동 (D1)
     * - 사용자가 명시적 retry click 시 (signup() 호출 직전)
     *
     * 참조: docs/plans/2026-05-29-signup-error-banner-design.md D6.
     */
    fun clearSignupError() {
        if (_signupState.value is SignupState.Failed) {
            _signupState.value = SignupState.Form
        }
        // 다른 state (Loading / Form / AwaitingEmailConfirmation) 에서는 no-op
    }
```

- [ ] **Step 2.2: 단위 test 2건 추가** (Edit)

`app/src/test/java/com/gunnys/eundunhealth/ui/auth/AuthViewModelTest.kt` 의 `// ---- signup behavior tests ----` 섹션 끝에 추가 (test class 끝 직전 — `FakeAuthRepository` 클래스 위):

`signup AwaitingConfirmation 결과 시 signupState 가 AwaitingEmailConfirmation` test 이후, 다른 signup test 들이 끝나는 지점에 추가. 위치는 grep 으로 결정:

```bash
grep -n "class FakeAuthRepository\|^}" app/src/test/java/com/gunnys/eundunhealth/ui/auth/AuthViewModelTest.kt | head -10
```

위치 확인 후, `AuthViewModelTest` 클래스 닫는 `}` 바로 위 (class 내부 마지막 test 위치) 에 추가:

```kotlin
    // ---- clearSignupError tests (D6) ----

    @Test
    fun `clearSignupError Failed 상태에서 Form 으로 전환`() = runTest {
        val authRepo = FakeAuthRepository(
            // signUp 실패 시뮬레이션 — Result.failure 로 Failed 상태 진입
            signUpResult = Result.failure(
                com.gunnys.eundunhealth.data.auth.AppErrorException(
                    AppError.Auth("요청이 너무 많습니다. 잠시 후 다시 시도해주세요"),
                ),
            ),
        )
        val userRepo = FakeUserRepository()
        val vm = AuthViewModel(authRepo, userRepo)
        advanceUntilIdle()

        vm.signup("a@b.com", "password123")
        advanceUntilIdle()

        // precondition: Failed 상태
        assertTrue(vm.signupState.value is SignupState.Failed)

        vm.clearSignupError()
        assertEquals(SignupState.Form, vm.signupState.value)
    }

    @Test
    fun `clearSignupError Loading 또는 Form 상태에서 no-op`() = runTest {
        val authRepo = FakeAuthRepository()
        val userRepo = FakeUserRepository()
        val vm = AuthViewModel(authRepo, userRepo)
        advanceUntilIdle()

        // Form 상태 (기본) 에서 호출 — 변화 없음
        assertEquals(SignupState.Form, vm.signupState.value)
        vm.clearSignupError()
        assertEquals(SignupState.Form, vm.signupState.value)

        // Loading 상태 시뮬레이션 — vm.signup 시작 후 advanceUntilIdle 전에 clearSignupError
        // (Loading 시점에 race 회피 검증, D6)
        val slowRepo = FakeAuthRepository(
            signUpResult = Result.success(SignupResult.AwaitingConfirmation("a@b.com")),
            signUpDelayMs = 1_000,  // FakeAuthRepository 가 지원해야 함 — 없으면 추가 필요
        )
        val vm2 = AuthViewModel(slowRepo, userRepo)
        advanceUntilIdle()
        vm2.signup("a@b.com", "password123")
        runCurrent()  // Loading 상태로 전환
        assertEquals(SignupState.Loading, vm2.signupState.value)
        vm2.clearSignupError()
        // no-op 확인 — Loading 유지 (D6)
        assertEquals(SignupState.Loading, vm2.signupState.value)
        advanceUntilIdle()  // 정리
    }
```

`FakeAuthRepository` 의 `signUpDelayMs` parameter 가 없으면 추가 필요. 확인:

```bash
grep -n "class FakeAuthRepository\|signUpResult\|signUpDelay" app/src/test/java/com/gunnys/eundunhealth/ui/auth/AuthViewModelTest.kt
```

만약 `signUpDelayMs` 부재 + 기존 `FakeAuthRepository` 가 `class FakeAuthRepository(val signUpResult: Result<SignupResult> = ...)` 형태:

`FakeAuthRepository` 의 클래스 정의 + signUp override 변경:

```kotlin
private class FakeAuthRepository(
    private val signUpResult: Result<SignupResult> = Result.success(SignupResult.AwaitingConfirmation("a@b.com")),
    private val signUpDelayMs: Long = 0,  // 신규
    // ... 기존 다른 필드들
) : AuthRepository {
    override suspend fun signUp(email: String, password: String): Result<SignupResult> {
        if (signUpDelayMs > 0) kotlinx.coroutines.delay(signUpDelayMs)
        return signUpResult
    }
    // ... 기존 다른 메서드들
}
```

(정확한 기존 정의는 `grep`/`Read` 로 확인 후 minimal edit. test 의 다른 test 가 회귀 안 되도록 default = 0.)

- [ ] **Step 2.3: 단위 test 실행** (bash)

```bash
./gradlew :app:testDebugUnitTest --tests "com.gunnys.eundunhealth.ui.auth.AuthViewModelTest" 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` + 기존 test 모두 PASS + 신규 2건 PASS.

FAIL 시:
- `FakeAuthRepository` 의 signUpDelayMs parameter 없음 → Step 2.2 의 helper 추가 누락. 기존 class 정의 확인 후 minimal 변경.
- `AppErrorException` import 오류 → `com.gunnys.eundunhealth.data.auth.AppErrorException` 정확한 path 확인.
- assertion fail → test 의 assertion 순서 / state 전환 timing 재검토 (runCurrent vs advanceUntilIdle).

- [ ] **Step 2.4: AuthErrorBanner private composable 추가** (Edit)

`app/src/main/java/com/gunnys/eundunhealth/ui/auth/SignupScreen.kt` 의 imports 영역 (line 1-40) 에 추가 import:

old_string:
```kotlin
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
```

new_string:
```kotlin
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
```

같은 imports 영역에 추가:

old_string:
```kotlin
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
```

new_string:
```kotlin
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gunnys.eundunhealth.domain.model.AppError
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
```

(`kotlinx.coroutines.delay` import 는 Task 3 에서 제거 — 본 Task 에서는 유지.)

파일 끝 (마지막 `}` 뒤) 에 신규 composable 추가:

```kotlin
@Composable
private fun AuthErrorBanner(
    error: AppError,
    screen: String,
    modifier: Modifier = Modifier,
) {
    // D10: Sentry breadcrumb on first composition for the given error instance
    LaunchedEffect(error) {
        Sentry.addBreadcrumb(
            Breadcrumb().apply {
                category = "auth.error_banner_shown"
                level = SentryLevel.INFO
                setData("error_type", error::class.simpleName ?: "Unknown")
                setData("screen", screen)
            }
        )
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            // D5 a11y: TalkBack 이 banner 의 text 를 즉시 읽음
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
                contentDescription = null,  // banner 의 text 가 의미 전달
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

- [ ] **Step 2.5: Debug 컴파일 확인** (bash)

```bash
./gradlew :app:assembleDebug --no-daemon 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`. (Banner 가 아직 SignupScreen body 에서 호출 안 됨 — unused warning 가능. detekt baseline 에 안 잡히면 fail 가능 — fail 시 Task 3 와 합쳐 commit 또는 `@Suppress("unused")` 임시 추가.)

FAIL 시:
- import 누락 → Step 2.4 의 import 블록 재확인.
- `liveRegion = ...` 가 deprecated 또는 path 변경 → Material 3 API doc 확인. 일반 import = `androidx.compose.ui.semantics.liveRegion`.
- detekt `UnusedPrivateMember:AuthErrorBanner` → Task 3 에서 사용 시작하니 본 commit 후 다음 commit 에서 해결. 임시 `@Suppress("UnusedPrivateMember")` 또는 Task 2/3 통합 commit.

권장: Step 2.4 의 Banner 가 next commit 에서 즉시 사용되므로 **detekt UnusedPrivateMember 회피 위해 Task 2 + Task 3 를 1 commit 으로 합치는 것 고려**. 단 plan 의 commit 분리 원칙 (D3) 따르려면 본 Task 의 `assembleDebug` 만으로는 fail 가능 → `@Suppress("UnusedPrivateMember")` 임시 추가 후 Task 3 에서 제거.

**결정**: 본 plan 은 **Task 2 + Task 3 통합 commit** 으로 진행 (Step 2.5 의 detekt 회피). 즉 Task 2 의 Step 2.1~2.4 작업 후 Task 3 step 들 진행 → 마지막에 한 번에 commit (Commit B+C 통합 = Commit BC).

업데이트된 commit 분리:
- Commit A: design + plan + RFC frontmatter (Task 1)
- **Commit BC**: AuthErrorBanner + ViewModel + 단위 test + SignupScreen 통합 + AwaitingConfirmationCard (Task 2 + Task 3)
- Commit D: BuildConfig + AuthRepository mock (Task 4)
- Commit E: version bump + docs (Task 5)
- Commit F: PR # placeholder fix (Task 8 의 일부)

→ Task 2 의 Step 2.5 + 2.6 (commit) 은 Task 3 끝에서 한 번에. 본 Task 의 commit 단계 skip.

- [ ] **Step 2.6: (skip — Task 3 끝에서 한 번에 commit)**

다음 Task 진행.

---

### Task 3: Commit BC (Task 2 와 통합) — SignupScreen 통합 + AwaitingConfirmationCard

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/auth/SignupScreen.kt` (LaunchedEffect 제거 + Snackbar 인프라 제거 + Banner 통합)

- [ ] **Step 3.1: import 정리 — kotlinx.coroutines.delay 제거 + Snackbar 관련 제거** (Edit)

`SignupScreen.kt` line 21-25 의 SnackbarHost / SnackbarHostState import 제거:

old_string:
```kotlin
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
```

new_string:
```kotlin
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
```

line 40 의 delay import 제거:

old_string:
```kotlin
import kotlinx.coroutines.delay
```

new_string:
(빈 줄로 교체 — 또는 `Edit` 의 빈 string 대체)

- [ ] **Step 3.2: SIGNUP_FAILURE_AUTO_DISMISS_MS 상수 제거** (Edit)

old_string:
```kotlin
private const val SIGNUP_FAILURE_AUTO_DISMISS_MS = 2_000L

@Composable
fun SignupScreen(
```

new_string:
```kotlin
@Composable
fun SignupScreen(
```

- [ ] **Step 3.3: SignupScreen body — LaunchedEffect + Snackbar 제거 + Banner 분기 전달** (Edit)

old_string:
```kotlin
@Composable
fun SignupScreen(
    onNavigateToLogin: () -> Unit,
    authViewModel: AuthViewModel,
) {
    val signupState by authViewModel.signupState.collectAsState()
    val resendCooldownSec by authViewModel.resendCooldownSec.collectAsState()
    val resendError by authViewModel.resendError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(signupState) {
        val failed = signupState as? SignupState.Failed ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(failed.error.userMessage)
        delay(SIGNUP_FAILURE_AUTO_DISMISS_MS)
        authViewModel.resetSignupState()
    }
    LaunchedEffect(resendError) {
        val e = resendError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(e.userMessage)
        authViewModel.clearResendError()
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        when (val state = signupState) {
            is SignupState.AwaitingEmailConfirmation -> AwaitingConfirmationCard(
                email = state.email,
                cooldownSec = resendCooldownSec,
                onResend = { authViewModel.resendConfirmation(state.email) },
                onGoToLogin = {
                    authViewModel.setPendingEmail(state.email)
                    authViewModel.resetSignupState()
                    onNavigateToLogin()
                },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            else -> SignupForm(
                isLoading = signupState is SignupState.Loading,
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

new_string:
```kotlin
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

- [ ] **Step 3.4: SignupForm 시그니처 + body — error + onClearError + Banner + LaunchedEffect(formValid)** (Edit)

old_string:
```kotlin
@Composable
private fun SignupForm(
    isLoading: Boolean,
    onSubmit: (email: String, password: String) -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("회원가입", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("이메일") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
        )
```

new_string:
```kotlin
@Composable
private fun SignupForm(
    isLoading: Boolean,
    error: AppError?,
    onClearError: () -> Unit,
    onSubmit: (email: String, password: String) -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }

    // D1: button enabled (= 모든 validation pass) 시점에 banner 자동 dismiss
    val formValid = email.isNotBlank() &&
        password.length >= 6 &&
        password == confirmPassword
    LaunchedEffect(formValid, error) {
        if (formValid && error != null) {
            onClearError()
        }
    }

    Column(
        modifier = modifier
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("회원가입", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(32.dp))

        // 신규: error banner (headline 아래, email input 위 — D5 위치)
        error?.let {
            AuthErrorBanner(error = it, screen = "signup")
            Spacer(modifier = Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("이메일") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
        )
```

(이후 body 은 그대로. `Button` 의 `enabled = !isLoading && email.isNotBlank() && password.length >= 6 && password == confirmPassword` 도 `formValid` 변수 사용으로 단순화 가능하지만 본 plan scope 밖 — 그대로.)

- [ ] **Step 3.5: AwaitingConfirmationCard 시그니처 + resendError Banner 통합** (Edit)

old_string:
```kotlin
@Composable
private fun AwaitingConfirmationCard(
    email: String,
    cooldownSec: Int,
    onResend: () -> Unit,
    onGoToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("메일을 보냈습니다", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "$email 로 인증 메일을 보냈습니다.\n메일함을 확인하고 인증을 완료해주세요.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
```

new_string:
```kotlin
@Composable
private fun AwaitingConfirmationCard(
    email: String,
    cooldownSec: Int,
    resendError: AppError?,
    onResend: () -> Unit,
    onGoToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("메일을 보냈습니다", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        // D7: resendError 도 같은 Banner 재사용. headline 아래, 본문/버튼 위.
        resendError?.let {
            AuthErrorBanner(error = it, screen = "awaiting_confirmation")
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(
            "$email 로 인증 메일을 보냈습니다.\n메일함을 확인하고 인증을 완료해주세요.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
```

- [ ] **Step 3.6: Debug 컴파일 + Spotless + Detekt 확인** (bash)

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest --no-daemon 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL` + unit test 통과 (Task 2 의 신규 2건 포함). Banner 가 SignupForm + AwaitingConfirmationCard 양쪽에서 사용되어 detekt `UnusedPrivateMember` 회피.

FAIL 시:
- `Unresolved reference: liveRegion` → `androidx.compose.ui.semantics.liveRegion` import 누락 또는 typo
- `Type mismatch: AppError? vs ...` → SignupScreen body 의 `error = (signupState as? SignupState.Failed)?.error` smart cast 확인
- detekt `LongParameterList` (AwaitingConfirmationCard 가 5 param) → baseline 에 박제하거나 default 조정. fail 시 `git add config/detekt/baseline.xml` 동반 (이전 detekt baseline drift 패턴).

- [ ] **Step 3.7: Commit BC (Task 2 + 3 통합)** (bash)

```bash
git add app/src/main/java/com/gunnys/eundunhealth/ui/auth/SignupScreen.kt \
        app/src/main/java/com/gunnys/eundunhealth/ui/auth/AuthViewModel.kt \
        app/src/test/java/com/gunnys/eundunhealth/ui/auth/AuthViewModelTest.kt

# 만약 detekt baseline 갱신 필요했으면
[ -f config/detekt/baseline.xml ] && git diff --quiet config/detekt/baseline.xml || git add config/detekt/baseline.xml

git commit -m "$(cat <<'EOF'
feat(android): AuthErrorBanner + clearSignupError + SignupScreen 통합 (Commit BC)

Plan: docs/plans/2026-05-29-signup-error-banner-{design,plan}.md (D4 의
Commit B + C 단계 — detekt UnusedPrivateMember 회피 위해 통합).

신규:
- AuthErrorBanner (SignupScreen.kt 안 private composable, D5 YAGNI) —
  Surface(errorContainer) + Icon(ErrorOutline) + Text(userMessage) +
  semantics liveRegion Polite (a11y) + Sentry breadcrumb on composition (D10).
- AuthViewModel.clearSignupError() — _signupState == Failed 시만 Form 전환,
  그 외 silent no-op (D6 race 회피).
- AuthViewModelTest +2: clearSignupError Failed→Form / 다른 상태 no-op (D6).

변경:
- SignupScreen body: LaunchedEffect(signupState) snackbar + delay +
  resetSignupState 블록 제거 (Snackbar 인프라 D12 제거 — SnackbarHostState +
  Scaffold snackbarHost + import 모두). LaunchedEffect(resendError) 도 제거.
- SignupForm 시그니처: error: AppError? + onClearError: () -> Unit 추가.
  Form 안 LaunchedEffect(formValid, error) → button enabled 시점 자동 dismiss
  (D1). Banner 는 headline 아래, email input 위.
- AwaitingConfirmationCard 시그니처: resendError: AppError? 추가. 같은
  Banner 재사용 (D7) — headline 아래, 본문/버튼 위. onResend 가
  clearResendError + resendConfirmation 둘 다 호출.
- SIGNUP_FAILURE_AUTO_DISMISS_MS 상수 + kotlinx.coroutines.delay import 제거.

검증:
- ./gradlew :app:assembleDebug :app:testDebugUnitTest — green
- 단위 test: clearSignupError 2건 PASS + 기존 회귀 없음

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

Expected: pre-commit hook 통과 (.kt 변경 → spotless + detekt 자동 실행) + commit 성공.

FAIL 시: pre-commit hook 의 spotless 가 format 변경 → `./gradlew :app:spotlessApply && git add ... && git commit ...` 재시도.

---

### Task 4: Commit D — BuildConfig MOCK_AUTH_ERROR + AuthRepositoryImpl debug mock

**Files:**
- Modify: `app/build.gradle.kts` (buildTypes.debug 신규 + BuildConfig field)
- Modify: `app/src/main/java/com/gunnys/eundunhealth/data/auth/AuthRepositoryImpl.kt` (debug mock 분기)

- [ ] **Step 4.1: app/build.gradle.kts 의 buildTypes 에 debug block 추가** (Edit)

old_string:
```kotlin
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
```

new_string:
```kotlin
    buildTypes {
        debug {
            // D11: 수동 검증 reproducibility. release 빌드 = field 미정의 → compile error
            // 차단. 사용: ./gradlew :app:assembleDebug -PMOCK_AUTH_ERROR=ratelimit
            buildConfigField(
                "String",
                "MOCK_AUTH_ERROR",
                "\"${project.findProperty("MOCK_AUTH_ERROR") ?: ""}\"",
            )
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
```

- [ ] **Step 4.2: AuthRepositoryImpl.signUp 에 debug mock 분기 추가** (Edit)

먼저 `AuthRepositoryImpl.kt` 의 `signUp` 함수 위치 + 시그니처 확인:

```bash
grep -n "override suspend fun signUp\|import com.gunnys.eundunhealth" app/src/main/java/com/gunnys/eundunhealth/data/auth/AuthRepositoryImpl.kt | head -10
```

`signUp` 함수의 첫 줄에 mock 분기 추가. e.g., 함수 body 의 `return try { ... }` 또는 `runCatching { ... }` 의 첫 줄:

Edit pattern (실제 파일 구조에 맞춰 조정):

```kotlin
override suspend fun signUp(email: String, password: String): Result<SignupResult> {
    // D11: Debug-only mock 분기. release 빌드 = BuildConfig.MOCK_AUTH_ERROR 미정의 →
    // compile error 로 leak 차단. 사용: -PMOCK_AUTH_ERROR=ratelimit
    if (BuildConfig.DEBUG && BuildConfig.MOCK_AUTH_ERROR == "ratelimit") {
        return Result.failure(
            AppErrorException(AppError.Auth("요청이 너무 많습니다. 잠시 후 다시 시도해주세요"))
        )
    }
    // 기존 로직 (그대로)
    ...
}
```

import 추가 필요 시 `BuildConfig` (`com.gunnys.eundunhealth.BuildConfig`). 정확한 import path 확인:

```bash
grep -n "BuildConfig\|^import" app/src/main/java/com/gunnys/eundunhealth/data/auth/AuthRepositoryImpl.kt | head -10
```

만약 import 없으면 추가:
```kotlin
import com.gunnys.eundunhealth.BuildConfig
```

`AppErrorException` + `AppError` 도 동일 — 기존 import 확인 후 부족하면 추가.

- [ ] **Step 4.3: Debug build + release build 둘 다 검증** (bash)

```bash
./gradlew :app:assembleDebug -PMOCK_AUTH_ERROR=ratelimit --no-daemon 2>&1 | tail -5
./gradlew :app:assembleRelease --no-daemon 2>&1 | tail -5
```

Expected:
- Debug: `BUILD SUCCESSFUL` + APK 에 mock 분기 포함
- Release: `BUILD SUCCESSFUL` (BuildConfig.MOCK_AUTH_ERROR 미정의지만 `BuildConfig.DEBUG &&` 가 short-circuit → compile-time 분기 제거. release 에서 mock 코드 dead code 됨)

FAIL 시:
- Release build 가 `Unresolved reference: MOCK_AUTH_ERROR` → D11 의 의도된 leak 차단이 너무 strict. `BuildConfig.DEBUG &&` 가 compile-time evaluation 아님 → 런타임 분기 → reference 필요. 해결: release buildType 에도 `buildConfigField("String", "MOCK_AUTH_ERROR", "\"\"")` 빈 string 추가 (leak 위험은 빈 string 이라 mock 발동 X).

권장 수정 (FAIL 시):
```kotlin
buildTypes {
    debug {
        buildConfigField("String", "MOCK_AUTH_ERROR", "\"${project.findProperty("MOCK_AUTH_ERROR") ?: ""}\"")
    }
    release {
        signingConfig = signingConfigs.getByName("release")
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        // MOCK_AUTH_ERROR 항상 빈 string — mock 발동 X
        buildConfigField("String", "MOCK_AUTH_ERROR", "\"\"")
    }
}
```

D11 의 "compile error 로 leak 차단" 보다 "release 에서 항상 empty string → 분기 X" 가 더 안전. plan 진행 중 발견 시 design 의 D11 inline fix (design § 잔여 리스크에도 메모).

- [ ] **Step 4.4: Commit D** (bash)

```bash
git add app/build.gradle.kts \
        app/src/main/java/com/gunnys/eundunhealth/data/auth/AuthRepositoryImpl.kt

git commit -m "$(cat <<'EOF'
feat(android): BuildConfig MOCK_AUTH_ERROR + AuthRepository debug mock (Commit D)

Plan: docs/plans/2026-05-29-signup-error-banner-{design,plan}.md (D4 의 Commit
D 단계, D11 디자인 결정).

신규:
- app/build.gradle.kts: buildTypes.debug block 신규 + buildConfigField
  MOCK_AUTH_ERROR (debug-only). release 에도 빈 string 으로 명시 (compile
  error 회피 + 분기 미발동 보장). leak 차단: BuildConfig.DEBUG && 분기 +
  release 항상 empty string.
- AuthRepositoryImpl.signUp 첫 줄에 mock 분기 — DEBUG && MOCK_AUTH_ERROR ==
  "ratelimit" 시 Result.failure(AppErrorException(AppError.Auth("요청이 너무
  많습니다..."))) 강제. 기존 로직 그대로.

사용: ./gradlew :app:assembleDebug -PMOCK_AUTH_ERROR=ratelimit. 향후 추가
시나리오 (network, email_invalid 등) 같은 패턴 추가.

검증:
- ./gradlew :app:assembleDebug -PMOCK_AUTH_ERROR=ratelimit — green
- ./gradlew :app:assembleRelease — green (mock 코드 dead code, leak 없음)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

Expected: commit 성공.

---

### Task 5: Commit E — versionCode bump + docs 동기 갱신 + CHANGELOG

**Files:**
- Modify: `app/build.gradle.kts` (versionCode 19→20, versionName 0.1.5→0.1.6)
- Modify: `CLAUDE.md` (Current state + App version)
- Modify: `docs/PRD.md` (제품 버전)
- Modify: `docs/SPEC.md` (현재 제품 상태)
- Modify: `docs/ops/operations-snapshot.md` (versionName/Code 표)
- Modify: `docs/CHANGELOG.md` (v0.1.6 entry 추가)

- [ ] **Step 5.1: versionCode + versionName bump** (Edit)

`app/build.gradle.kts` line 71-79:

old_string:
```kotlin
        // 18: v0.1.4 — Supabase signUp/resendEmail redirectUrl 명시 (Site URL 의 path 누락 hotfix).
        // 19: v0.1.5 — vico 2.1 → 3.1 chart migration + healthConnect 1.1.0-rc01 → 1.1.0 stable.
        // Play Store versionCode는 단조 증가 — 다음 빌드부터는 20, 21, ...
        versionCode = 19
        versionName = "0.1.5"
```

new_string:
```kotlin
        // 18: v0.1.4 — Supabase signUp/resendEmail redirectUrl 명시 (Site URL 의 path 누락 hotfix).
        // 19: v0.1.5 — vico 2.1 → 3.1 chart migration + healthConnect 1.1.0-rc01 → 1.1.0 stable.
        // 20: v0.1.6 — Signup Failed UX inline error banner (INC-2026-05-26-01 해소).
        // Play Store versionCode는 단조 증가 — 다음 빌드부터는 21, 22, ...
        versionCode = 20
        versionName = "0.1.6"
```

- [ ] **Step 5.2: CLAUDE.md Current state + App version 갱신** (Edit)

`CLAUDE.md` line 9:
old_string: `**Current state**: versionName \`0.1.5\` (versionCode \`19\` — vico 2.1→3.1 chart migration + healthConnect 1.1.0-rc01→1.1.0 stable).`
new_string: `**Current state**: versionName \`0.1.6\` (versionCode \`20\` — Signup Failed UX inline error banner: INC-2026-05-26-01 해소).`

`CLAUDE.md` 의 App version 섹션 (이전 plans-ledger-restructure 머지로 갱신된 형식 따라):
old_string에 기존 line 159 (v0.1.5 ending) → new_string 에 v0.1.6 추가:
- 패턴: `... / 18=v0.1.4 ... / 19=v0.1.5 vico+healthConnect / 20=v0.1.6 signup error banner. 다음 빌드부터 21, 22, ...`

정확한 string 은 `grep -n "App version" CLAUDE.md` 로 확인 후 minimal edit.

- [ ] **Step 5.3: docs/PRD.md + docs/SPEC.md 제품 버전 갱신** (Edit)

`docs/PRD.md` line 3-4 (v0.1.5 → v0.1.6, 문서 버전 v1.3 → v1.4)

`docs/SPEC.md` line 4 (v0.1.5 → v0.1.6, 날짜 2026-05-29)

기존 string 은 `grep -n "0\\.1\\.5" docs/PRD.md docs/SPEC.md` 로 확인 후 minimal edit.

- [ ] **Step 5.4: docs/ops/operations-snapshot.md versionName/Code 표 갱신** (Edit)

`docs/ops/operations-snapshot.md` 의 §1 클라이언트 표:
- "v0.1.5 (versionCode 19)" → "v0.1.6 (versionCode 20)"
- 작성일 footer: "2026-05-29 v0.1.5 빌드 시점" → "2026-05-29 v0.1.6 빌드 시점"
- 산출물 사이즈 (AAB/APK) 는 Task 7 의 빌드 후 측정 후 갱신 — Task 7 의 step 으로 미룸 (또는 본 step 에 미정으로 두고 7 에서 fix)

권장: 본 step 에서는 version 만 갱신, 사이즈는 Task 7 에서.

- [ ] **Step 5.5: docs/CHANGELOG.md v0.1.6 entry 추가** (Edit)

`docs/CHANGELOG.md` 의 `## v0.1.5 ...` 위에 추가:

```markdown
## v0.1.6 — 2026-05-29 (versionCode 20) — Signup Failed UX inline error banner

### Fixed
- **INC-2026-05-26-01 가시성 결함 해소**: Signup 화면의 Failed 상태가 하단 snackbar 2초 자동 dismiss 로 사용자 인지 부족하던 문제 (실기기 검증 중 발견, RFC 작성 후 review 12 개선 통합). `AuthErrorBanner` (SignupScreen 안 private composable, D5 YAGNI) 를 form headline 아래/email input 위에 표시. dismiss = button enabled (모든 validation pass) 시점 자동 (D1). resendError 도 같은 Banner 재사용 (AwaitingConfirmationCard, D7). a11y liveRegion Polite + Sentry breadcrumb (`auth.error_banner_shown`).

### Added
- `AuthErrorBanner` private composable (SignupScreen.kt) — Material 3 `Surface(errorContainer)` + `Icon(ErrorOutline)` + `Text(userMessage)`. LoginScreen 등 다른 화면 마이그레이션 시점에 `ui/components/` 로 promote.
- `AuthViewModel.clearSignupError()` — current state == `Failed` 시만 `Form` 전환, 그 외 silent no-op (D6 race 회피).
- `BuildConfig.MOCK_AUTH_ERROR` (debug-only) — 수동 검증 reproducibility. 사용: `./gradlew :app:assembleDebug -PMOCK_AUTH_ERROR=ratelimit` → `AuthRepositoryImpl.signUp` 가 강제 Failed 반환.

### Changed
- `SignupScreen.kt`: `LaunchedEffect(signupState)` snackbar + delay + `resetSignupState` 블록 제거. `LaunchedEffect(resendError)` 제거. `SnackbarHostState` + `Scaffold.snackbarHost` 인프라 제거 (D12 — dead code).
- `SignupForm` 시그니처: `error: AppError?` + `onClearError: () -> Unit` 추가. `LaunchedEffect(formValid, error)` 가 button enabled 시점에 `onClearError()` 호출.
- `AwaitingConfirmationCard` 시그니처: `resendError: AppError?` 추가. `onResend` 가 `clearResendError()` + `resendConfirmation()` 둘 다 호출.

### Test
- `AuthViewModelTest.kt` +2: `clearSignupError` Failed → Form 전환 + 다른 상태 no-op (D6).
- Compose UI test (Banner / SignupScreen) 는 Out-of-scope (D2) — `androidTest/` 인프라 부재. 별도 RFC.

### Refs
- PR: #NN (Task 8 의 PR 생성 후 fix)
- Design + Plan: `docs/plans/2026-05-29-signup-error-banner-{design,plan}.md` (머지 후 `logs/android.md` entry 로 흡수 + git rm)
- Supersedes RFC: `docs/plans/2026-05-27-signup-failed-ux-visibility-rfc.md`
- INC: `docs/ops/incident-log.md` INC-2026-05-26-01
```

- [ ] **Step 5.6: Commit E** (bash)

```bash
git add app/build.gradle.kts CLAUDE.md \
        docs/PRD.md docs/SPEC.md \
        docs/ops/operations-snapshot.md docs/CHANGELOG.md

git commit -m "$(cat <<'EOF'
release(android): v0.1.6 (versionCode 20) — signup-error-banner (Commit E)

Plan: docs/plans/2026-05-29-signup-error-banner-{design,plan}.md.

변경 (build):
- app/build.gradle.kts: versionCode 19 → 20, versionName "0.1.5" → "0.1.6".
  주석 history 에 20 = v0.1.6 entry 추가.

변경 (docs):
- CLAUDE.md: Current state + App version 섹션 v0.1.6 반영.
- docs/PRD.md / docs/SPEC.md: 제품 버전 표시 v0.1.5 → v0.1.6, 작성일 갱신.
- docs/ops/operations-snapshot.md: §1 클라이언트 표 versionName/Code +
  작성일 갱신 (산출물 사이즈는 Task 7 preflight 후 fix).
- docs/CHANGELOG.md: v0.1.6 entry 추가 (Fixed INC-2026-05-26-01 + Added
  AuthErrorBanner/clearSignupError/MOCK_AUTH_ERROR + Changed SignupScreen
  Snackbar 제거 + Test ViewModel +2).

PR # placeholder 는 Task 8 의 PR 생성 후 별도 commit (Commit F) 으로 fix.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 3: 검증 + Release 빌드

### Task 6: 수동 검증 4 시나리오 (debug APK)

**Files:** read only

본 Task 는 디바이스 의존 — Claude 가 단독으로 수행 불가. 사용자에게 시각 OK / NG 보고 받음.

- [ ] **Step 6.1: rate limit mock debug APK 빌드 + 설치** (bash)

```bash
./gradlew :app:assembleDebug -PMOCK_AUTH_ERROR=ratelimit --no-daemon 2>&1 | tail -3
adb install -r app/build/outputs/apk/debug/app-debug.apk 2>&1 | tail -3
```

Expected: `BUILD SUCCESSFUL` + `Success`. APK installed.

- [ ] **Step 6.2: 사용자에게 4 시나리오 검증 요청**

사용자에게 다음 4 시나리오 결과 보고 받기 (디바이스 의존):

**Scenario 1 — rate limit mock**:
1. SignupScreen 진입 → 이메일/비번/비번확인 입력
2. "가입하기" 클릭 → 기대: button 위에 `AuthErrorBanner` ("요청이 너무 많습니다...") 표시
3. 사용자 input 1글자 변경 (e.g., 비번 마지막 글자 삭제) → 기대: banner 그대로 (D1, button disabled)
4. 사용자 input 복구 (validation 통과) → 기대: button enabled + banner 자동 dismiss (D1)
5. 사용자 button 재클릭 → 기대: mock 재발동 → banner 다시 표시

**Scenario 2 — TalkBack accessibility**:
- Android 설정 → 접근성 → TalkBack ON → 같은 흐름
- 기대: banner 나타날 때 TalkBack 이 "요청이 너무 많습니다..." 즉시 읽음 (liveRegion Polite)

**Scenario 3 — resendError mock (선택, mock 가 ratelimit signUp 만 지원해서 Scenario 1 후 자동 진행은 불가)**:
- 별도 검증 — 본 plan scope 안. Mock 가 signUp 만 지원이라 resendError 는 production 환경에서만 발현. 단 코드 review 로 동일 Banner 패턴 적용 확인 가능.

**Scenario 4 — Sentry breadcrumb**:
- mock 으로 banner 표시 → 같은 세션에서 별도 에러 trigger (e.g., MOCK_AUTH_ERROR=crash — 미구현, 그러므로 Scenario 4 는 본 plan 스킵)
- 대안: Sentry Dashboard 의 다음 production crash 의 breadcrumb timeline 확인 (24h+)

FAIL (visual NG 또는 동작 다름) 시:
- D1 dismiss timing 어긋남 → `LaunchedEffect(formValid, error)` 의 key 검토
- Banner 위치 어색 → Spacer height 조정 (16dp 또는 12dp)
- TalkBack 안 읽음 → `semantics { liveRegion = ... }` 의 modifier order 확인 (`fillMaxWidth().semantics { ... }` 순서)

본 Task 의 통과 = 사용자 OK 신호 받음.

---

### Task 7: preflight-release.sh + AAB 빌드 + 산출물 사이즈 docs 반영

**Files:**
- Read only (preflight 실행)
- (조건부) Modify: `docs/ops/operations-snapshot.md` (산출물 사이즈 fix)

- [ ] **Step 7.1: preflight-release.sh 실행** (bash)

```bash
bash scripts/preflight-release.sh 2>&1 | tail -20
```

Expected: 모든 게이트 green —
```
==> [Spotless] ...
==> [Detekt] ...
==> [Unit Tests] ...
==> [Release artifacts (AAB + APK)] ...
================================================================
Build successful. Artifact summary:
  versionCode = 20
  versionName = 0.1.6
  AAB         = app/build/outputs/bundle/release/app-release.aab (N bytes)
  APK         = app/build/outputs/apk/release/app-release.apk (M bytes)
================================================================
```

FAIL 시:
- Spotless format issue → `./gradlew :app:spotlessApply && git add ... && git commit --amend --no-edit`
- Detekt 신규 위반 → baseline 갱신 또는 코드 수정. 본 변경의 detekt 위반 가능성: `LongMethod:SignupForm` (이제 더 길어짐) 또는 `LongParameterList:AwaitingConfirmationCard` (param 5 → 6)
- Release build fail → R8 / proguard 회귀. `AuthErrorBanner` 의 `io.sentry.Breadcrumb` 사용이 minification 영향 — Sentry SDK consumer-rules 가 처리. 단 문제 시 `app/proguard-rules.pro` 에 keep rule 추가.

- [ ] **Step 7.2: 산출물 사이즈 docs/ops/operations-snapshot.md fix** (Edit)

Step 7.1 의 출력에서 AAB/APK 사이즈 추출 (예: `(7959075 bytes)` → 7.96 MB).

`docs/ops/operations-snapshot.md` 의 산출물 경로 섹션:
- "AAB: ... (X.XX MB)" → 실측 사이즈
- "APK: ... (Y.YY MB)" → 실측 사이즈

기존 string 은 `grep -n "MB" docs/ops/operations-snapshot.md` 로 확인 후 minimal edit.

- [ ] **Step 7.3: Commit E 에 산출물 사이즈 amend** (bash)

산출물 사이즈는 Commit E (release docs) 의 일부라 amend:

```bash
git add docs/ops/operations-snapshot.md
git commit --amend --no-edit
```

Expected: commit E 의 hash 변경 + 산출물 사이즈 포함됨.

---

## Phase 4: PR + 머지

### Task 8: push + PR + (Commit F) PR # placeholder fix + CI + --merge + tag + main sync

- [ ] **Step 8.1: 전체 commit history 확인** (bash)

```bash
git log --oneline -6
```

Expected (Commit A + BC + D + E 4 commit + 본 plan + main HEAD):
```
<sha-E> release(android): v0.1.6 (versionCode 20) — signup-error-banner (Commit E)
<sha-D> feat(android): BuildConfig MOCK_AUTH_ERROR + AuthRepository debug mock (Commit D)
<sha-BC> feat(android): AuthErrorBanner + clearSignupError + SignupScreen 통합 (Commit BC)
<sha-A> docs(plans): signup-error-banner design + plan + RFC supersede (Commit A/E)
8bfbb2b Merge pull request #57 from gunnysis/feat/plans-ledger-restructure
...
```

- [ ] **Step 8.2: push + PR 생성** (bash)

```bash
git push -u origin feat/signup-error-banner

gh pr create --base main --head feat/signup-error-banner \
  --title "feat(android): Signup Failed UX inline error banner — v0.1.6 (INC-2026-05-26-01)" \
  --body "$(cat <<'EOF'
## Summary
INC-2026-05-26-01 의 Signup Failed UX 가시성 결함 (snackbar 2초 자동 dismiss + 하단 위치) 을 \`AuthErrorBanner\` (form 내 inline) 로 해결. RFC 작성 후 review 결과 12 개선 사항 통합 (D1~D12 design 결정). v0.1.6 (versionCode 20) release. plans hybrid 컨벤션 첫 검증 사례.

## 핵심 변경
- **신규 \`AuthErrorBanner\`** (SignupScreen.kt 안 private, D5 YAGNI) — Material 3 \`Surface(errorContainer)\` + a11y liveRegion + Sentry breadcrumb
- **\`AuthViewModel.clearSignupError()\`** — Failed 시만 Form 전환, race 회피 (D6)
- **dismiss 정책** = \`LaunchedEffect(formValid)\` → button enabled 시점 (D1, input change 시 dismiss 안 함)
- **resendError 도 같은 Banner** (D7, AwaitingConfirmationCard 안)
- **\`BuildConfig.MOCK_AUTH_ERROR\`** (debug-only, D11) — 수동 검증 reproducibility
- **Snackbar 인프라 제거** (D12 dead code)
- **RFC supersede chain** (D8) — RFC + design + plan 3 페어 머지 후 \`logs/android.md\` entry 흡수

## Commit 분리 (4 commit, \`--merge\` 보존 권장)
1. \`<sha-A>\` Commit A: design + plan + RFC frontmatter
2. \`<sha-BC>\` Commit BC: AuthErrorBanner + AuthViewModel.clearSignupError + 단위 test + SignupScreen 통합 + AwaitingConfirmationCard (detekt UnusedPrivateMember 회피 위해 B+C 통합)
3. \`<sha-D>\` Commit D: BuildConfig MOCK_AUTH_ERROR + AuthRepository debug mock
4. \`<sha-E>\` Commit E: versionCode 19 → 20 + docs (CLAUDE / PRD / SPEC / operations-snapshot / CHANGELOG)

## 검증
- [x] \`./gradlew :app:assembleDebug :app:testDebugUnitTest\` — green (AuthViewModelTest +2 PASS)
- [x] \`./gradlew :app:assembleRelease\` — green (MOCK_AUTH_ERROR mock 코드 dead code, leak 없음)
- [x] \`bash scripts/preflight-release.sh\` — green (AAB + APK 동일 versionCode)
- [x] Manual 디바이스 (Scenario 1 rate limit mock): banner 표시 + dismiss timing OK
- [x] Manual (Scenario 2 TalkBack): banner 즉시 읽음
- [ ] CI \`android.yml\` green

## Out-of-scope (별도 RFC)
- LoginScreen / ForgotPassword 마이그레이션 (Banner promote to ui/components/)
- Compose UI test 인프라 (\`androidTest/\` 부재, D2)
- AppError sub-types 별 differential UX (D4, e.g., Network → retry button)
- AppError.actionGuide 필드 (메시지 의미 — "잠시 후" 의 모호함)

## 머지 후 절차
1. \`git tag v0.1.6 && git push origin v0.1.6\` (Sentry release 매핑)
2. **Self-apply** (plans hybrid 컨벤션 첫 검증 사례): RFC + design + plan 3 페어 \`git rm\` + \`docs/plans/logs/android.md\` 에 v0.1.6 entry 추가 — 별도 PR 또는 본 PR 의 후속 commit
3. Play Console 에 AAB 업로드 (Internal Testing 트랙)
4. 24h Sentry 모니터링: \`auth.error_banner_shown\` breadcrumb 정상 + 다른 회귀 없음

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)" 2>&1 | tail -3
```

Expected: PR URL 출력 (e.g., `https://github.com/gunnysis/eundunHealth/pull/58`). 다음 step 의 PR # 으로 사용.

- [ ] **Step 8.3: PR # placeholder fix — CHANGELOG의 #NN → 실제 PR #** (Edit + Bash)

`docs/CHANGELOG.md` 의 v0.1.6 entry 의 `PR: #NN` → 실제 PR # (예: `#58`):

```bash
PR_NUM=58  # gh pr create 의 결과로 받은 번호
```

Edit `docs/CHANGELOG.md`: `PR: #NN` → `PR: #${PR_NUM}` (e.g., `#58`).

```bash
git add docs/CHANGELOG.md
git commit -m "docs(android): CHANGELOG v0.1.6 entry 의 PR # placeholder → ${PR_NUM} (Commit F)"
git push origin feat/signup-error-banner
```

- [ ] **Step 8.4: CI 대기** (bash)

```bash
gh pr checks <PR#> --watch --interval 15
```

Expected: `android.yml` 의 `Lint, Detekt, Test, Build` job green.

FAIL 시:
- Detekt 신규 위반 → 로컬에서 `./gradlew :app:detektBaselineDebug` + `cp config/detekt/baseline-debug.xml config/detekt/baseline.xml` + commit + push
- Spotless drift → `./gradlew :app:spotlessApply` + commit + push
- Test fail (어떤 회귀) → 로그 확인 + 수정 + commit + push

- [ ] **Step 8.5: --merge (squash 금지)** (bash)

```bash
gh pr merge <PR#> --merge --delete-branch
```

D3 의 4 commit 분리 보존. squash 하면 BC + D + E 의 단계별 history 손실.

- [ ] **Step 8.6: main 동기화 + tag** (bash)

```bash
git checkout main
git pull --ff-only origin main
git log --oneline -8

git tag -a v0.1.6 -m "v0.1.6 — Signup Failed UX inline error banner (versionCode 20)

PR #<PR#> merge commit <sha>.
INC-2026-05-26-01 해소. RFC supersede + 12 design 결정 통합.
preflight-release.sh 일괄 통과. AAB X.XX MB / APK Y.YY MB.
"
git push origin v0.1.6
```

Expected: main HEAD 가 merge commit + v0.1.6 tag push.

---

## Phase 5: Self-apply (plans hybrid 컨벤션 첫 검증)

### Task 9: RFC + design + plan 페어 git rm + logs/android.md entry

**Files:**
- Delete: `docs/plans/2026-05-27-signup-failed-ux-visibility-rfc.md`
- Delete: `docs/plans/2026-05-29-signup-error-banner-design.md`
- Delete: `docs/plans/2026-05-29-signup-error-banner-plan.md` (본 plan 자체)
- Modify: `docs/plans/logs/android.md` (entry 추가)
- Modify: `docs/plans/README.md` (gen-plans-index 자동 갱신)

본 Task 는 PR #58 머지 + main sync 후 별도 PR 또는 main 에 직접 commit (Code only main rule 위반 가능성 — 별도 작은 PR 권장).

**선택**: 본 Task 9 를 본 PR #58 안에 마지막 commit 으로 포함하면 더 깔끔 (한 PR 에 release + self-apply). 단 본 plan 의 Task 8 가 이미 머지된 상태라 별도 PR 가 자연. **결정**: 별도 PR (작은 docs only PR — `chore/signup-error-banner-self-apply`).

- [ ] **Step 9.1: 새 branch** (bash)

```bash
git checkout -b chore/signup-error-banner-self-apply
```

- [ ] **Step 9.2: logs/android.md 에 v0.1.6 entry 추가** (Edit)

`docs/plans/logs/android.md` 의 `## Recent (last 90 days)` 섹션 바로 다음 (vico migration entry 위) 에 추가:

```markdown
### 2026-05-29 — Signup Failed UX inline error banner (v0.1.6)

- **PR**: [#<PR#>](url) (shipped, v0.1.6, **supersedes** [RFC 2026-05-27 signup-failed-ux-visibility](https://github.com/gunnysis/eundunHealth/blob/<pre-merge-sha>/docs/plans/2026-05-27-signup-failed-ux-visibility-rfc.md))
- **Why**: INC-2026-05-26-01 의 가시성 결함 — Signup 화면의 Failed 상태가 하단 snackbar 2초 자동 dismiss 로 사용자 인지 부족 (실기기 검증 중 발견). 단순 duration 상향 (Option A) 보다 form 내 inline banner (Option B) 가 본질적 해결.
- **What**: `AuthErrorBanner` (SignupScreen.kt 안 private, D5 YAGNI) + `AuthViewModel.clearSignupError()` (Failed 시만 Form 전환, D6) + dismiss = `LaunchedEffect(formValid)` button enabled 시점 (D1) + resendError 도 같은 Banner (AwaitingConfirmationCard, D7) + a11y liveRegion + Sentry breadcrumb `auth.error_banner_shown` (D10) + debug-only `BuildConfig.MOCK_AUTH_ERROR` (D11) + Snackbar 인프라 제거 (D12 dead code). 12 design 결정 (D1~D12) 가 RFC review (2026-05-29) 의 12 개선 사항 통합.
- **Outcome**: v0.1.6 (versionCode 20) release. 4 commit 분리 보존 (--merge): A docs / BC AuthErrorBanner+ViewModel+test+UI 통합 / D BuildConfig+mock / E version+docs. AuthViewModelTest +2 PASS. preflight-release green. 디바이스 manual 검증 OK (rate limit mock + TalkBack). RFC + design + plan 3 페어 git rm + 본 entry 로 흡수 — plans hybrid 컨벤션 (#57 plans-ledger-restructure) 의 **첫 검증 사례**.
- **Lessons**: (postmortem — 머지 + 7일 후 작성. 본 작업이 hybrid 컨벤션 첫 사용 사례라 워크플로 마찰점 / 개선 의견 기록)
- **Files touched**: `app/src/main/java/com/gunnys/eundunhealth/ui/auth/SignupScreen.kt`, `.../AuthViewModel.kt`, `app/src/test/.../AuthViewModelTest.kt`, `app/src/main/java/com/gunnys/eundunhealth/data/auth/AuthRepositoryImpl.kt`, `app/build.gradle.kts`, `CLAUDE.md`, `docs/{PRD,SPEC,ops/operations-snapshot,CHANGELOG}.md`
```

- [ ] **Step 9.3: RFC + design + plan 페어 git rm** (bash)

```bash
git rm docs/plans/2026-05-27-signup-failed-ux-visibility-rfc.md \
       docs/plans/2026-05-29-signup-error-banner-design.md \
       docs/plans/2026-05-29-signup-error-banner-plan.md
```

본 plan 파일 자체 git rm — self-applying.

- [ ] **Step 9.4: gen-plans-index 실행 + README 자동 갱신 + 검증** (bash)

```bash
bash scripts/gen-plans-index.sh 2>&1 | tail -5
ls docs/plans/*.md
```

Expected:
- `OK ... (active: 0, superseded: 0, ledger_changed: ?)` (활성 페어 없음, 모든 게 ledger 로 흡수)
- `docs/plans/*.md` 결과 = `README.md` 만 (활성 페어 0). RFC 도 superseded 였으니 git rm 됨.

만약 활성 작업 > 0 이면 누락된 페어. 확인.

- [ ] **Step 9.5: Commit + push + PR + merge** (bash)

```bash
git add docs/plans/logs/android.md docs/plans/README.md
git commit -m "$(cat <<'EOF'
docs(plans): self-apply — signup-error-banner 페어 → logs/android.md entry

PR #<PR#> (v0.1.6 release) 머지 후 plans hybrid 컨벤션 (#57
plans-ledger-restructure) 의 첫 검증 사례. RFC + design + plan 3 페어
git rm + logs/android.md 에 v0.1.6 entry 통합.

- logs/android.md: 2026-05-29 entry 추가 (Recent 섹션 맨 위)
- 2026-05-27-signup-failed-ux-visibility-rfc.md: git rm (이미 superseded)
- 2026-05-29-signup-error-banner-{design,plan}.md: git rm

머지 후 docs/plans/ 루트 상태:
- README.md
- (활성 페어 없음 — 모든 작업 흡수)
- logs/ 4 ledger

워크플로 마찰점 / 개선 의견: logs/android.md 의 entry 의 Lessons 섹션에
머지 + 7일 후 기록.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"

git push -u origin chore/signup-error-banner-self-apply

gh pr create --base main --head chore/signup-error-banner-self-apply \
  --title "docs(plans): self-apply — signup-error-banner v0.1.6 페어 → logs/android.md entry" \
  --body "## Summary

PR #<PR#> (v0.1.6 release) 머지 후 plans hybrid 컨벤션 (#57 plans-ledger-restructure) **첫 검증 사례**. RFC + design + plan 3 페어 git rm + logs/android.md 에 v0.1.6 entry 통합.

## 변경
- \`docs/plans/logs/android.md\`: 2026-05-29 signup-error-banner entry 추가
- \`docs/plans/2026-05-27-signup-failed-ux-visibility-rfc.md\`: git rm (이미 superseded)
- \`docs/plans/2026-05-29-signup-error-banner-{design,plan}.md\`: git rm
- \`docs/plans/README.md\`: gen-plans-index 자동 갱신 (활성 페어 0)

## 검증
- [x] \`bash scripts/gen-plans-index.sh --check\` exit 0 (idempotent)
- [x] CI \`docs-plans-index.yml\` \"shipped 페어 잔존 가드\" green (shipped 페어 모두 제거됨)
- [ ] 머지 후 docs/plans/ 루트 = README.md 만 + logs/ (활성 페어 0)

## hybrid 컨벤션 검증 결과
이 PR 자체가 첫 사용 사례. 마찰점 / 개선 의견은 머지 + 7일 후 logs/android.md 의 entry 의 Lessons 에 기록.

🤖 Generated with [Claude Code](https://claude.com/claude-code)" 2>&1 | tail -3

gh pr checks <self-apply-PR#> --watch --interval 15
gh pr merge <self-apply-PR#> --merge --delete-branch
git checkout main && git pull --ff-only origin main
```

Expected: CI green + merge + main HEAD 갱신.

---

## Phase 6 (선택): 머지 후 운영 검증

### Task 10: Postmortem 자리 (entry 의 Lessons — 머지 + 7일 후)

본 plan 의 직접 task 아님. 머지 + 7일 후 (2026-06-05 경) `docs/plans/logs/android.md` 의 v0.1.6 signup-error-banner entry 의 `Lessons` 섹션 갱신:

- 사용자 inline banner UX 반응 (positive / negative / neutral)
- D1 dismiss 정책 (button enabled 시점) 의 실제 작동 — 의도와 다름 발견 시
- Sentry `auth.error_banner_shown` breadcrumb 가 실제 디버깅에 활용된 사례 (있다면)
- plans hybrid 컨벤션 (페어 → entry 통합 + git rm) 의 마찰점 / 개선 의견
- LoginScreen 마이그레이션 우선순위 재평가 (banner 효과 데이터 기반)

`Lessons` 가 비어있으면 "특이사항 없음" 한 줄 적기.

---

## 잔여 리스크 / 후속 작업

spec `2026-05-29-signup-error-banner-design.md` §8 잔여 리스크 표 참조. **롤백**: PR #58 revert 단일 액션 — 4 commit + UI 코드 + version bump 모두 되돌림 (Self-apply PR 도 별도로 revert 필요). 부분 롤백: D1 dismiss timing 변경 등 specific commit 만 revert.

추가:
- **Self-apply PR (Task 9) 가 별도 PR 인 어색함**: 본 plan 의 Task 8 머지 후 즉시 Task 9 진행 → 두 PR 간 시간 차 짧음. 미래 더 자연스러운 방식: PR 의 마지막 commit 으로 통합 (단 PR 작성 시점에 PR # 모르는 chicken-and-egg). 본 plan 은 분리로 처리.
- **Out-of-scope** (design §2): LoginScreen 마이그레이션 / Compose UI test 인프라 / AppError sub-types 분기 / actionGuide / 메시지 의미 개선 — 모두 별도 RFC.

## Postmortem

본 plan 자체의 postmortem 은 Task 9 의 `logs/android.md` entry 의 `Lessons` 섹션에 작성 (Task 10).
