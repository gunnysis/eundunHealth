# 회원가입 이메일 확인 흐름 + 인증 상태 리팩터 구현 Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Supabase email confirmation 켜진 상태에서 사용자가 가입 → 메일 인증 → 로그인까지 자연스럽게 완료하도록 흐름을 갖추고, 가입/로그인 실패가 Login 화면으로 튕겨 스낵바를 잘리게 만드는 구조적 버그를 제거한다.

**Architecture:** `AuthViewModel`의 단일 `AuthState`를 `SessionState`(글로벌)/`AuthOpState`(인증 화면 작업)/`SignupState`(가입 흐름)로 분리. `AppNavigation`은 `SessionState`만 구독해 가입/로그인 실패 시 화면 전환을 일으키지 않게 함. `AuthRepository.signUp`은 `SignupResult` sealed (`AutoSignedIn` vs `AwaitingConfirmation`)를 반환하고 `resendConfirmation` 메서드를 추가. `AppError`에 `EmailNotConfirmed(email)` 타입 추가.

**Tech Stack:** Kotlin 2.2.10, Compose, Hilt, Supabase-kt 3.x (Auth만), kotlinx-coroutines-test 1.10.2, JUnit4, MockK.

**참고 문서:**
- Design: `docs/plans/2026-05-26-signup-confirmation-flow-design.md` (commit 256cc7c)
- Supabase Auth: https://supabase.com/docs/guides/auth
- 기존 테스트 컨벤션: `app/src/test/java/com/gunnys/eundunhealth/domain/model/AppErrorTest.kt`

**중요 원칙:**
- TDD: 각 동작 변경 task는 red(테스트 실패) → green(최소 구현) → refactor → commit 순.
- 코드만 변경하고 테스트가 없는 task(UI 등)도 빌드 통과 + 수동 검증을 통과 기준으로 둠.
- `AuthRepositoryImpl`의 Supabase 호출 부분은 단위 테스트가 어려움 — `mapAuthError`를 top-level 함수로 분리해 그 부분만 unit test, signUp/signIn의 결과 분기는 런타임/수동 검증.
- 매 task 끝에 commit. 메시지는 `<type>(<scope>): ...` 형식 (`feat(auth)`, `refactor(auth)`, `test(auth)` 등).

---

## 사전 준비

### Task 0: feature branch 생성

**Files:**
- 변경 없음 (git 작업만)

**Step 1: design doc commit이 main에 있음을 확인**

Run: `git log --oneline -3`
Expected: `256cc7c docs(plans): 회원가입 이메일 확인 흐름...` 가 main에 보임.

**Step 2: feature branch 생성**

Run:
```bash
git checkout -b feat/signup-email-confirmation
```
Expected: `Switched to a new branch 'feat/signup-email-confirmation'`.

**Step 3: 이후 모든 task는 이 branch 위에서 작업하고 PR로 머지함**

커밋 없음.

---

## Phase 1: SDD Step 1 — SPEC 갱신

### Task 1: docs/SPEC.md 인증 섹션 갱신

**Files:**
- Modify: `docs/SPEC.md` (인증/회원가입 섹션 — 정확한 위치는 파일 열어 확인)

**Step 1: SPEC.md 인증 섹션을 찾고 다음 자연어 사양 추가/대체**

추가할 핵심 사양 항목:
- 회원가입 시 Supabase가 확인 메일을 발송하고, 세션은 사용자가 메일 인증을 마친 후에만 활성화된다.
- 가입 직후 안내 화면(SignupScreen 안의 AwaitingEmailConfirmation 상태)에서 60초 쿨다운으로 메일을 재전송할 수 있다.
- 메일 인증 후 사용자는 수동으로 Login 화면에서 로그인하며, 이메일 칸은 이전 가입 입력으로 자동 채워진다.
- 미인증 상태로 Login 시도 시 `EmailNotConfirmed` 에러를 표시하고 inline "메일 재전송" 액션을 노출한다.
- `Supabase 사용 범위`: Authentication 서비스만 사용한다. Database / Storage / Realtime / Edge Functions 등은 out-of-scope.

**Step 2: 빌드/문서 lint이 있다면 통과 확인**

이 프로젝트에는 SPEC 전용 검증은 없음 — markdown 형식 깨지지 않게만.

**Step 3: Commit**

```bash
git add docs/SPEC.md
git commit -m "docs(spec): 회원가입 이메일 확인 흐름 + Supabase 범위 명시 (SDD Step 1)"
```

---

## Phase 2: Domain 계층

### Task 2: AppError.EmailNotConfirmed 추가 (TDD)

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/domain/model/AppError.kt`
- Modify: `app/src/test/java/com/gunnys/eundunhealth/domain/model/AppErrorTest.kt`

**Step 1: 실패 테스트 작성**

`AppErrorTest.kt`에 추가:
```kotlin
@Test
fun `EmailNotConfirmed carries email and Korean message`() {
    val err = AppError.EmailNotConfirmed(email = "a@b.com")
    assertEquals("a@b.com", err.email)
    assertEquals("이메일 인증이 완료되지 않았습니다", err.userMessage)
}
```

**Step 2: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.gunnys.eundunhealth.domain.model.AppErrorTest"`
Expected: Compilation error — `EmailNotConfirmed` is not a member of `AppError`.

**Step 3: AppError.kt 에 EmailNotConfirmed 추가**

`AppError.kt`의 sealed class 본문에 추가:
```kotlin
data class EmailNotConfirmed(
    val email: String,
    override val userMessage: String = "이메일 인증이 완료되지 않았습니다",
) : AppError(userMessage)
```

`reportToSentry()` 정책은 변경하지 않음 (Unknown만 전송).

**Step 4: 테스트 통과 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.gunnys.eundunhealth.domain.model.AppErrorTest"`
Expected: All AppErrorTest tests pass (기존 + 신규 1개).

**Step 5: Commit**

```bash
git add app/src/main/java/com/gunnys/eundunhealth/domain/model/AppError.kt \
        app/src/test/java/com/gunnys/eundunhealth/domain/model/AppErrorTest.kt
git commit -m "feat(domain): AppError.EmailNotConfirmed 타입 추가"
```

---

### Task 3: AuthRepository 인터페이스 — SignupResult + resendConfirmation

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/domain/repository/AuthRepository.kt`

**Step 1: AuthRepository 인터페이스 수정**

`SignupResult` sealed 타입 추가 (같은 파일 내):
```kotlin
sealed class SignupResult {
    data class AutoSignedIn(val userId: String) : SignupResult()
    data class AwaitingConfirmation(val email: String) : SignupResult()
}
```

기존 `signUp(email, password): Result<String>` 시그니처를:
```kotlin
suspend fun signUp(email: String, password: String): Result<SignupResult>
```

`resendConfirmation` 메서드 추가:
```kotlin
suspend fun resendConfirmation(email: String): Result<Unit>
```

**Step 2: 컴파일 확인 (구현체는 다음 task에서 수정 — 일시적으로 컴파일 깨짐)**

이 단계에서는 인터페이스만 바꾸는 것이라 `AuthRepositoryImpl`이 컴파일 실패함. 의도된 상태 — 다음 task에서 즉시 수정.

**Step 3: Commit 보류**

Task 4 완료 시 함께 commit (인터페이스+구현체를 같은 commit으로 묶어야 빌드 깨지지 않음).

---

## Phase 3: Data 계층

### Task 4: AuthRepositoryImpl — mapAuthError 분리 + signUp 반환 + resendConfirmation 구현

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/data/auth/AuthRepositoryImpl.kt`
- Create: `app/src/test/java/com/gunnys/eundunhealth/data/auth/AuthErrorMappingTest.kt`

**Step 1: 실패 테스트 작성 (mapAuthError 분리 + EmailNotConfirmed 분기)**

`mapAuthError`를 top-level internal 함수로 분리할 예정. 테스트 파일 생성:

```kotlin
package com.gunnys.eundunhealth.data.auth

import com.gunnys.eundunhealth.domain.model.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthErrorMappingTest {

    @Test
    fun `email_not_confirmed 메시지는 EmailNotConfirmed(email)으로 매핑`() {
        val err = mapAuthError("email_not_confirmed", email = "a@b.com", isLogin = true)
        assertTrue(err is AppError.EmailNotConfirmed)
        assertEquals("a@b.com", (err as AppError.EmailNotConfirmed).email)
    }

    @Test
    fun `Email not confirmed 영문 메시지도 EmailNotConfirmed로 매핑`() {
        val err = mapAuthError("Email not confirmed", email = "a@b.com", isLogin = true)
        assertTrue(err is AppError.EmailNotConfirmed)
    }

    @Test
    fun `already registered는 Auth(이미 가입된 이메일)로 매핑`() {
        val err = mapAuthError("user already registered", email = "a@b.com", isLogin = false)
        assertTrue(err is AppError.Auth)
        assertTrue(err.userMessage.contains("이미 가입된 이메일"))
    }

    @Test
    fun `invalid_credentials는 Auth(이메일 또는 비밀번호)로 매핑`() {
        val err = mapAuthError("invalid_credentials", email = "a@b.com", isLogin = true)
        assertTrue(err is AppError.Auth)
        assertTrue(err.userMessage.contains("이메일 또는 비밀번호"))
    }

    @Test
    fun `weak_password는 Auth(6자 이상)로 매핑`() {
        val err = mapAuthError("weak_password", email = "a@b.com", isLogin = false)
        assertTrue(err is AppError.Auth)
        assertTrue(err.userMessage.contains("6자 이상"))
    }

    @Test
    fun `매칭되지 않는 메시지는 일반 회원가입 실패로 매핑(isLogin=false)`() {
        val err = mapAuthError("strange backend error", email = "a@b.com", isLogin = false)
        assertTrue(err is AppError.Auth)
        assertTrue(err.userMessage.contains("회원가입에 실패"))
    }
}
```

**Step 2: 컴파일 실패 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.gunnys.eundunhealth.data.auth.AuthErrorMappingTest"`
Expected: Compile error — `mapAuthError` not found at package level.

**Step 3: AuthRepositoryImpl.kt 수정**

(a) `mapAuthError`를 top-level internal 함수로 분리. 시그니처:
```kotlin
internal fun mapAuthError(rawMessage: String, email: String, isLogin: Boolean): AppError {
    val msg = rawMessage.lowercase()
    return when {
        msg.contains("email_not_confirmed") || msg.contains("email not confirmed") ->
            AppError.EmailNotConfirmed(email)
        msg.contains("invalid_credentials") || msg.contains("invalid_credential") ->
            AppError.Auth("이메일 또는 비밀번호가 올바르지 않습니다")
        msg.contains("user_already_exists") || msg.contains("already registered") ->
            AppError.Auth("이미 가입된 이메일입니다. 인증을 완료하지 않으셨다면 로그인 화면에서 메일을 다시 받으실 수 있습니다")
        msg.contains("weak_password") || msg.contains("least 6") ->
            AppError.Auth("비밀번호는 6자 이상이어야 합니다")
        msg.contains("rate_limit") || msg.contains("too many") ->
            AppError.Auth("요청이 너무 많습니다. 잠시 후 다시 시도해주세요")
        msg.contains("network") || msg.contains("timeout") || msg.contains("connect") ->
            AppError.Network()
        msg.contains("email") && msg.contains("invalid") ->
            AppError.Auth("올바른 이메일 형식을 입력해주세요")
        else -> AppError.Auth(if (isLogin) "로그인에 실패했습니다" else "회원가입에 실패했습니다")
    }
}
```

(b) 클래스 내부의 `mapAuthError` 메서드 제거.

(c) `signUp` 시그니처 변경 + 두 결과 분기:
```kotlin
override suspend fun signUp(email: String, password: String): Result<SignupResult> = try {
    supabaseClient.auth.signUpWith(Email) {
        this.email = email
        this.password = password
    }
    val user = supabaseClient.auth.currentUserOrNull()
    if (user != null) {
        tokenHolder.set(supabaseClient.auth.currentSessionOrNull()?.accessToken)
        Result.success(SignupResult.AutoSignedIn(user.id))
    } else {
        Result.success(SignupResult.AwaitingConfirmation(email))
    }
} catch (e: Exception) {
    Result.failure(AppErrorException(mapAuthError(e.message ?: "", email, isLogin = false)))
}
```

(d) `signIn`도 동일한 mapAuthError 호출 패턴으로 수정. 단, `AppErrorException`은 `AppError`를 포함하는 보조 예외 — 같은 파일에 추가:
```kotlin
internal class AppErrorException(val appError: AppError) : Exception(appError.userMessage)
```

(e) `resendConfirmation` 신규 구현:
```kotlin
override suspend fun resendConfirmation(email: String): Result<Unit> = try {
    supabaseClient.auth.resendEmail(io.github.jan.supabase.auth.OtpType.Email.SIGNUP, email)
    Result.success(Unit)
} catch (e: Exception) {
    Result.failure(AppErrorException(mapAuthError(e.message ?: "", email, isLogin = false)))
}
```

> **참고:** `OtpType.Email.SIGNUP`의 정확한 import 경로는 supabase-kt 3.x 버전에 따라 다를 수 있음 — IDE auto-import로 해결. 컴파일 실패 시 source 직접 확인.

(f) `resetPassword`도 mapAuthError 호출 패턴으로 일관화.

**Step 4: AuthRepository 인터페이스 import 갱신**

`SignupResult` import 추가, 컴파일 통과 확인.

**Step 5: 단위 테스트 통과 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.gunnys.eundunhealth.data.auth.AuthErrorMappingTest"`
Expected: All 6 tests pass.

**Step 6: 전체 단위 테스트 + 빌드 확인**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: All tests pass, debug APK 빌드 성공.

**Step 7: Commit (Task 3 변경분 포함)**

```bash
git add app/src/main/java/com/gunnys/eundunhealth/domain/repository/AuthRepository.kt \
        app/src/main/java/com/gunnys/eundunhealth/data/auth/AuthRepositoryImpl.kt \
        app/src/test/java/com/gunnys/eundunhealth/data/auth/AuthErrorMappingTest.kt
git commit -m "feat(auth): SignupResult 반환 + EmailNotConfirmed 매핑 + resendConfirmation"
```

---

## Phase 4: ViewModel

### Task 5: 새 sealed 상태 정의 (SessionState / AuthOpState / SignupState)

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/auth/AuthViewModel.kt`
- Modify: `app/src/test/java/com/gunnys/eundunhealth/ui/auth/AuthViewModelTest.kt`

**Step 1: AuthViewModelTest에 sealed 정의 테스트 추가**

기존 4개 테스트는 보존하되, 새 상태에 대한 테스트 추가:
```kotlin
@Test
fun `SessionState Authenticated carries userId and onboarding flag`() {
    val state = SessionState.Authenticated(userId = "u-1", needsOnboarding = true)
    assertEquals("u-1", state.userId)
    assertEquals(true, state.needsOnboarding)
}

@Test
fun `AuthOpState Idle and Loading singletons`() {
    assertEquals(AuthOpState.Idle, AuthOpState.Idle)
    assertEquals(AuthOpState.Loading, AuthOpState.Loading)
}

@Test
fun `SignupState AwaitingEmailConfirmation carries email`() {
    val state = SignupState.AwaitingEmailConfirmation("a@b.com")
    assertEquals("a@b.com", state.email)
}
```

기존 `AuthState` 테스트는 일단 보존 (다음 task에서 AuthState 제거할 때 함께 삭제).

**Step 2: 컴파일 실패 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.gunnys.eundunhealth.ui.auth.AuthViewModelTest"`
Expected: `SessionState`, `AuthOpState`, `SignupState` not found.

**Step 3: AuthViewModel.kt 에 새 sealed class 추가**

기존 `AuthState`와 `ResetState`는 일단 남겨두고, 새 sealed 3개를 같은 파일 상단에 추가:
```kotlin
sealed class SessionState {
    @Immutable data object Unknown : SessionState()
    @Immutable data class Authenticated(val userId: String, val needsOnboarding: Boolean = false) : SessionState()
    @Immutable data object Unauthenticated : SessionState()
}

sealed class AuthOpState {
    @Immutable data object Idle : AuthOpState()
    @Immutable data object Loading : AuthOpState()
    @Immutable data class Failed(val error: AppError) : AuthOpState()
}

sealed class SignupState {
    @Immutable data object Form : SignupState()
    @Immutable data object Loading : SignupState()
    @Immutable data class AwaitingEmailConfirmation(val email: String) : SignupState()
    @Immutable data class Failed(val error: AppError) : SignupState()
}
```

**Step 4: 테스트 통과 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.gunnys.eundunhealth.ui.auth.AuthViewModelTest"`
Expected: All tests pass (기존 + 신규 3개).

**Step 5: Commit**

```bash
git add app/src/main/java/com/gunnys/eundunhealth/ui/auth/AuthViewModel.kt \
        app/src/test/java/com/gunnys/eundunhealth/ui/auth/AuthViewModelTest.kt
git commit -m "feat(auth): SessionState/AuthOpState/SignupState sealed class 추가"
```

---

### Task 6: AuthViewModel.signup — TDD

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/auth/AuthViewModel.kt`
- Modify: `app/src/test/java/com/gunnys/eundunhealth/ui/auth/AuthViewModelTest.kt`

**Step 1: 동작 테스트 작성 (mockk + coroutines-test)**

`AuthViewModelTest`에 추가 import:
```kotlin
import com.gunnys.eundunhealth.domain.repository.AuthRepository
import com.gunnys.eundunhealth.domain.repository.SignupResult
import com.gunnys.eundunhealth.domain.repository.UserRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
```

테스트 클래스에 `Main` dispatcher rule 추가:
```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before fun setup() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    // ...
}
```

새 테스트:
```kotlin
@Test
fun `signup AwaitingConfirmation 결과 시 signupState 가 AwaitingEmailConfirmation`() = runTest {
    val authRepo = mockk<AuthRepository>(relaxed = true)
    val userRepo = mockk<UserRepository>(relaxed = true)
    coEvery { authRepo.restoreSession() } returns null
    coEvery { authRepo.signUp("a@b.com", "password123") } returns
        Result.success(SignupResult.AwaitingConfirmation("a@b.com"))

    val vm = AuthViewModel(authRepo, userRepo)
    advanceUntilIdle()

    vm.signup("a@b.com", "password123")
    advanceUntilIdle()

    val state = vm.signupState.value
    assertTrue(state is SignupState.AwaitingEmailConfirmation)
    assertEquals("a@b.com", (state as SignupState.AwaitingEmailConfirmation).email)
}

@Test
fun `signup AutoSignedIn 결과 시 sessionState 가 Authenticated(needsOnboarding=true)`() = runTest {
    val authRepo = mockk<AuthRepository>(relaxed = true)
    val userRepo = mockk<UserRepository>(relaxed = true)
    coEvery { authRepo.restoreSession() } returns null
    coEvery { authRepo.signUp("a@b.com", "pw") } returns
        Result.success(SignupResult.AutoSignedIn("user-1"))

    val vm = AuthViewModel(authRepo, userRepo)
    advanceUntilIdle()
    vm.signup("a@b.com", "pw")
    advanceUntilIdle()

    val session = vm.sessionState.value
    assertTrue(session is SessionState.Authenticated)
    assertEquals("user-1", (session as SessionState.Authenticated).userId)
    assertEquals(true, session.needsOnboarding)
}

@Test
fun `signup 실패 시 signupState 가 Failed`() = runTest {
    val authRepo = mockk<AuthRepository>(relaxed = true)
    val userRepo = mockk<UserRepository>(relaxed = true)
    coEvery { authRepo.restoreSession() } returns null
    coEvery { authRepo.signUp("a@b.com", "pw") } returns
        Result.failure(com.gunnys.eundunhealth.data.auth.AppErrorException(AppError.Auth("이미 가입된 이메일입니다")))

    val vm = AuthViewModel(authRepo, userRepo)
    advanceUntilIdle()
    vm.signup("a@b.com", "pw")
    advanceUntilIdle()

    val state = vm.signupState.value
    assertTrue(state is SignupState.Failed)
}
```

**Step 2: 테스트 실행 — 컴파일 실패 확인 (signupState 등 미존재)**

Run: `./gradlew :app:testDebugUnitTest --tests "com.gunnys.eundunhealth.ui.auth.AuthViewModelTest"`

**Step 3: AuthViewModel.kt 에 StateFlow + signup 로직 추가**

기존 `_authState`/`authState` 를 유지한 채로 **신규 StateFlow 추가** (점진 마이그레이션):
```kotlin
private val _sessionState = MutableStateFlow<SessionState>(SessionState.Unknown)
val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

private val _signupState = MutableStateFlow<SignupState>(SignupState.Form)
val signupState: StateFlow<SignupState> = _signupState.asStateFlow()

private val _authOpState = MutableStateFlow<AuthOpState>(AuthOpState.Idle)
val authOpState: StateFlow<AuthOpState> = _authOpState.asStateFlow()
```

기존 `signup`을 다음 시그니처로 교체:
```kotlin
fun signup(email: String, password: String) = viewModelScope.launch {
    _signupState.value = SignupState.Loading
    authRepo.signUp(email, password)
        .onSuccess { result ->
            when (result) {
                is SignupResult.AutoSignedIn -> {
                    _sessionState.value = SessionState.Authenticated(result.userId, needsOnboarding = true)
                    _signupState.value = SignupState.Form
                }
                is SignupResult.AwaitingConfirmation -> {
                    _signupState.value = SignupState.AwaitingEmailConfirmation(result.email)
                }
            }
        }
        .onFailure { e ->
            val appErr = (e as? com.gunnys.eundunhealth.data.auth.AppErrorException)?.appError
                ?: e.toAppError().also { it.reportToSentry() }
            _signupState.value = SignupState.Failed(appErr)
        }
}
```

기존 `checkSession` 도 `_sessionState`를 갱신하도록 수정 (병렬 — 기존 `_authState`는 일단 유지):
```kotlin
private fun checkSession() = viewModelScope.launch {
    runCatching {
        val userId = authRepo.restoreSession()
        if (userId != null) {
            val hasProfile = userRepo.getProfile().getOrNull() != null
            SessionState.Authenticated(userId, needsOnboarding = !hasProfile)
        } else {
            SessionState.Unauthenticated
        }
    }.onSuccess { _sessionState.value = it }
     .onFailure { _sessionState.value = SessionState.Unauthenticated }
}
```

**Step 4: 테스트 통과 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.gunnys.eundunhealth.ui.auth.AuthViewModelTest"`
Expected: All tests pass.

**Step 5: Commit**

```bash
git add app/src/main/java/com/gunnys/eundunhealth/ui/auth/AuthViewModel.kt \
        app/src/test/java/com/gunnys/eundunhealth/ui/auth/AuthViewModelTest.kt
git commit -m "feat(auth): signup 흐름을 SignupState/SessionState로 분리"
```

---

### Task 7: AuthViewModel.login — EmailNotConfirmed 분기 + authOpState (TDD)

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/auth/AuthViewModel.kt`
- Modify: `app/src/test/java/com/gunnys/eundunhealth/ui/auth/AuthViewModelTest.kt`

**Step 1: 동작 테스트 추가**

```kotlin
@Test
fun `login 성공 시 sessionState=Authenticated, authOpState 가 Idle 로 복귀`() = runTest {
    val authRepo = mockk<AuthRepository>(relaxed = true)
    val userRepo = mockk<UserRepository>(relaxed = true)
    coEvery { authRepo.restoreSession() } returns null
    coEvery { authRepo.signIn("a@b.com", "pw") } returns Result.success("user-1")
    coEvery { userRepo.getProfile() } returns Result.success(mockk(relaxed = true))

    val vm = AuthViewModel(authRepo, userRepo)
    advanceUntilIdle()
    vm.login("a@b.com", "pw")
    advanceUntilIdle()

    assertTrue(vm.sessionState.value is SessionState.Authenticated)
    assertEquals(AuthOpState.Idle, vm.authOpState.value)
}

@Test
fun `login EmailNotConfirmed 에러 시 authOpState=Failed(EmailNotConfirmed)`() = runTest {
    val authRepo = mockk<AuthRepository>(relaxed = true)
    val userRepo = mockk<UserRepository>(relaxed = true)
    coEvery { authRepo.restoreSession() } returns null
    coEvery { authRepo.signIn("a@b.com", "pw") } returns
        Result.failure(com.gunnys.eundunhealth.data.auth.AppErrorException(AppError.EmailNotConfirmed("a@b.com")))

    val vm = AuthViewModel(authRepo, userRepo)
    advanceUntilIdle()
    vm.login("a@b.com", "pw")
    advanceUntilIdle()

    val state = vm.authOpState.value
    assertTrue(state is AuthOpState.Failed)
    assertTrue((state as AuthOpState.Failed).error is AppError.EmailNotConfirmed)
}
```

**Step 2: 테스트 실행 — 실패 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.gunnys.eundunhealth.ui.auth.AuthViewModelTest"`

**Step 3: login 메서드 수정**

```kotlin
fun login(email: String, password: String) = viewModelScope.launch {
    _authOpState.value = AuthOpState.Loading
    authRepo.signIn(email, password)
        .onSuccess { userId ->
            val hasProfile = userRepo.getProfile().getOrNull() != null
            _sessionState.value = SessionState.Authenticated(userId, needsOnboarding = !hasProfile)
            _authOpState.value = AuthOpState.Idle
        }
        .onFailure { e ->
            val appErr = (e as? com.gunnys.eundunhealth.data.auth.AppErrorException)?.appError
                ?: e.toAppError().also { it.reportToSentry() }
            _authOpState.value = AuthOpState.Failed(appErr)
        }
}
```

**Step 4: 테스트 통과 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.gunnys.eundunhealth.ui.auth.AuthViewModelTest"`

**Step 5: Commit**

```bash
git add app/src/main/java/com/gunnys/eundunhealth/ui/auth/AuthViewModel.kt \
        app/src/test/java/com/gunnys/eundunhealth/ui/auth/AuthViewModelTest.kt
git commit -m "feat(auth): login 흐름을 authOpState로 분리 + EmailNotConfirmed 분기"
```

---

### Task 8: AuthViewModel.resendConfirmation + 60초 쿨다운 (TDD)

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/auth/AuthViewModel.kt`
- Modify: `app/src/test/java/com/gunnys/eundunhealth/ui/auth/AuthViewModelTest.kt`

**Step 1: 테스트 추가**

```kotlin
@Test
fun `resendConfirmation 성공 시 60초 쿨다운 시작 후 0으로 감소`() = runTest {
    val authRepo = mockk<AuthRepository>(relaxed = true)
    val userRepo = mockk<UserRepository>(relaxed = true)
    coEvery { authRepo.restoreSession() } returns null
    coEvery { authRepo.resendConfirmation("a@b.com") } returns Result.success(Unit)

    val vm = AuthViewModel(authRepo, userRepo)
    advanceUntilIdle()
    vm.resendConfirmation("a@b.com")
    advanceTimeBy(1) // 첫 tick
    runCurrent()
    assertEquals(60, vm.resendCooldownSec.value)

    advanceTimeBy(60_000)
    assertEquals(0, vm.resendCooldownSec.value)
}

@Test
fun `resendConfirmation 실패 시 쿨다운 시작하지 않음`() = runTest {
    val authRepo = mockk<AuthRepository>(relaxed = true)
    val userRepo = mockk<UserRepository>(relaxed = true)
    coEvery { authRepo.restoreSession() } returns null
    coEvery { authRepo.resendConfirmation(any()) } returns
        Result.failure(com.gunnys.eundunhealth.data.auth.AppErrorException(AppError.Network()))

    val vm = AuthViewModel(authRepo, userRepo)
    advanceUntilIdle()
    vm.resendConfirmation("a@b.com")
    advanceUntilIdle()

    assertEquals(0, vm.resendCooldownSec.value)
}
```

**Step 2: 테스트 실행 — 실패**

**Step 3: 구현**

```kotlin
private val _resendCooldownSec = MutableStateFlow(0)
val resendCooldownSec: StateFlow<Int> = _resendCooldownSec.asStateFlow()

// 마지막으로 보낸 resend의 에러를 한 번만 노출하기 위한 transient state
private val _resendError = MutableStateFlow<AppError?>(null)
val resendError: StateFlow<AppError?> = _resendError.asStateFlow()
fun clearResendError() { _resendError.value = null }

fun resendConfirmation(email: String) = viewModelScope.launch {
    if (_resendCooldownSec.value > 0) return@launch
    authRepo.resendConfirmation(email)
        .onSuccess {
            _resendCooldownSec.value = 60
            // 카운트다운
            while (_resendCooldownSec.value > 0) {
                kotlinx.coroutines.delay(1_000)
                _resendCooldownSec.value = (_resendCooldownSec.value - 1).coerceAtLeast(0)
            }
        }
        .onFailure { e ->
            val appErr = (e as? com.gunnys.eundunhealth.data.auth.AppErrorException)?.appError
                ?: e.toAppError().also { it.reportToSentry() }
            _resendError.value = appErr
        }
}
```

**Step 4: 테스트 통과 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.gunnys.eundunhealth.ui.auth.AuthViewModelTest"`

**Step 5: Commit**

```bash
git add app/src/main/java/com/gunnys/eundunhealth/ui/auth/AuthViewModel.kt \
        app/src/test/java/com/gunnys/eundunhealth/ui/auth/AuthViewModelTest.kt
git commit -m "feat(auth): resendConfirmation + 60초 쿨다운"
```

---

### Task 9: AuthViewModel.pendingEmail (set/clear)

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/auth/AuthViewModel.kt`
- Modify: `app/src/test/java/com/gunnys/eundunhealth/ui/auth/AuthViewModelTest.kt`

**Step 1: 테스트 추가**

```kotlin
@Test
fun `pendingEmail set 후 clear 동작`() = runTest {
    val authRepo = mockk<AuthRepository>(relaxed = true)
    val userRepo = mockk<UserRepository>(relaxed = true)
    coEvery { authRepo.restoreSession() } returns null
    val vm = AuthViewModel(authRepo, userRepo)
    advanceUntilIdle()

    assertEquals(null, vm.pendingEmail.value)
    vm.setPendingEmail("a@b.com")
    assertEquals("a@b.com", vm.pendingEmail.value)
    vm.clearPendingEmail()
    assertEquals(null, vm.pendingEmail.value)
}
```

**Step 2: 실패 확인**

**Step 3: 구현**

```kotlin
private val _pendingEmail = MutableStateFlow<String?>(null)
val pendingEmail: StateFlow<String?> = _pendingEmail.asStateFlow()

fun setPendingEmail(email: String) { _pendingEmail.value = email }
fun clearPendingEmail() { _pendingEmail.value = null }
fun resetSignupState() { _signupState.value = SignupState.Form }
```

**Step 4: 통과 확인 + Commit**

```bash
git add app/src/main/java/com/gunnys/eundunhealth/ui/auth/AuthViewModel.kt \
        app/src/test/java/com/gunnys/eundunhealth/ui/auth/AuthViewModelTest.kt
git commit -m "feat(auth): pendingEmail StateFlow + setter/clearer 추가"
```

---

### Task 10: ForgotPassword 흐름을 authOpState로 통합 (회귀 검증 포함)

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/auth/AuthViewModel.kt`
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/auth/ForgotPasswordScreen.kt`
- Modify: `app/src/test/java/com/gunnys/eundunhealth/ui/auth/AuthViewModelTest.kt`

**Step 1: 테스트 추가**

```kotlin
@Test
fun `resetPassword 성공 시 authOpState 가 Idle 로 복귀하고 별도 success 플래그 노출`() = runTest {
    val authRepo = mockk<AuthRepository>(relaxed = true)
    val userRepo = mockk<UserRepository>(relaxed = true)
    coEvery { authRepo.restoreSession() } returns null
    coEvery { authRepo.resetPassword("a@b.com") } returns Result.success(Unit)

    val vm = AuthViewModel(authRepo, userRepo)
    advanceUntilIdle()
    vm.resetPassword("a@b.com")
    advanceUntilIdle()

    assertEquals(true, vm.passwordResetSent.value)
    assertEquals(AuthOpState.Idle, vm.authOpState.value)
}
```

**Step 2: ViewModel 수정**

`ResetState` sealed class 제거. 대신:
```kotlin
private val _passwordResetSent = MutableStateFlow(false)
val passwordResetSent: StateFlow<Boolean> = _passwordResetSent.asStateFlow()
fun consumePasswordResetSent() { _passwordResetSent.value = false }

fun resetPassword(email: String) = viewModelScope.launch {
    _authOpState.value = AuthOpState.Loading
    authRepo.resetPassword(email)
        .onSuccess {
            _passwordResetSent.value = true
            _authOpState.value = AuthOpState.Idle
        }
        .onFailure { e ->
            val appErr = (e as? com.gunnys.eundunhealth.data.auth.AppErrorException)?.appError
                ?: e.toAppError().also { it.reportToSentry() }
            _authOpState.value = AuthOpState.Failed(appErr)
        }
}
```

기존 `_resetState`/`resetState`/`clearResetState` 제거.

**Step 3: ForgotPasswordScreen.kt 수정**

`resetState` 구독을 `passwordResetSent` + `authOpState`로 교체. `isLoading = authOpState is AuthOpState.Loading`. 에러 표시는 `authOpState.Failed` 사용.

**Step 4: 빌드 + 테스트 통과 확인**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`

**Step 5: 수동 회귀 검증 (debug APK 디바이스 설치)**
- 비밀번호 재설정 화면에서 잘못된 이메일 입력 → 에러 스낵바 표시
- 정상 이메일 입력 → "비밀번호 재설정 링크를 이메일로 보냈습니다" 스낵바 + 뒤로가기

**Step 6: Commit**

```bash
git add app/src/main/java/com/gunnys/eundunhealth/ui/auth/AuthViewModel.kt \
        app/src/main/java/com/gunnys/eundunhealth/ui/auth/ForgotPasswordScreen.kt \
        app/src/test/java/com/gunnys/eundunhealth/ui/auth/AuthViewModelTest.kt
git commit -m "refactor(auth): ResetState 제거, ForgotPassword 흐름을 authOpState로 통합"
```

---

### Task 11: AuthViewModel — 기존 AuthState / _error / clearError 제거

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/auth/AuthViewModel.kt`
- Modify: `app/src/test/java/com/gunnys/eundunhealth/ui/auth/AuthViewModelTest.kt`

**Step 1: ViewModel에서 다음 제거**

- `sealed class AuthState ...`
- `_authState`, `authState`, 모든 `_authState.value = ...` 사용처
- `_error`, `error`, `clearError`

`logout()`은 `_sessionState.value = SessionState.Unauthenticated` 로만 갱신.

**Step 2: 호출처 빌드 깨짐 확인**

`AppNavigation`, `SignupScreen`, `LoginScreen`이 컴파일 실패. **다음 task들(12~17)에서 즉시 수정.**

**Step 3: 테스트 파일에서 AuthState 관련 4개 테스트 제거**

(Task 5에서 보존했던 `AuthState Loading is initial default` 등.)

**Step 4: Commit 보류 — UI 변경과 함께 묶어 commit**

---

## Phase 5: Navigation

### Task 12: AppNavigation — sessionState 구독

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/navigation/AppNavigation.kt`

**Step 1: 수정**

```kotlin
val sessionState by authViewModel.sessionState.collectAsState()

LaunchedEffect(sessionState) {
    when (val s = sessionState) {
        is SessionState.Authenticated -> {
            val dest = if (s.needsOnboarding) Screen.Onboarding.route else Screen.Home.route
            navController.navigate(dest) { popUpTo(0) { inclusive = true } }
        }
        SessionState.Unauthenticated -> {
            navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } }
        }
        SessionState.Unknown -> { /* Splash 유지 */ }
    }
}
```

import 갱신: `AuthState` → `SessionState`.

**Step 2: 빌드 (UI 화면도 함께 수정해야 통과)**

Task 13~17와 함께 진행 후 빌드 통과 시점에 commit.

---

## Phase 6: UI

### Task 13: SignupScreen — 상태 머신 UI + AwaitingEmailConfirmation 카드

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/auth/SignupScreen.kt`

**Step 1: SignupScreen 전체 구조 변경**

- `signupState` 와 `resendCooldownSec` 를 collect.
- `signupState` 에 따라 분기 렌더:
  - `Form` / `Loading` / `Failed`: 기존 입력 폼 (Failed면 스낵바 표시, 2초 후 자동 `resetSignupState()` 또는 `LaunchedEffect`)
  - `AwaitingEmailConfirmation(email)`: 신규 안내 카드 — `<email>로 메일을 보냈습니다. 메일함을 확인하고 인증을 완료해주세요` + "메일 다시 보내기" 버튼 (`enabled = cooldown == 0`, label은 `cooldown > 0 ? "${cooldown}초 후 다시 보낼 수 있어요" : "메일 다시 보내기"`) + "로그인하러 가기" 버튼 (`onNavigateToLogin()` 호출 전에 `setPendingEmail(email)`).

**Step 2: 빌드 확인**

Run: `./gradlew :app:assembleDebug`

**Step 3: Commit 보류 (LoginScreen 변경과 함께)**

---

### Task 14: SignupScreen — 비밀번호 6자 미만 inline 검증

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/auth/SignupScreen.kt`

**Step 1: 수정**

비번 OutlinedTextField에 `isError = password.isNotEmpty() && password.length < 6` + `supportingText = "비밀번호는 6자 이상이어야 합니다"`. `Button.enabled` 조건에 `password.length >= 6` 추가.

**Step 2: 빌드 확인**

Run: `./gradlew :app:assembleDebug`

---

### Task 15: LoginScreen — pendingEmail 자동 채움 + EmailNotConfirmed inline 재전송

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/auth/LoginScreen.kt`

**Step 1: pendingEmail 1회 자동 채움**

```kotlin
val pending by authViewModel.pendingEmail.collectAsState()
LaunchedEffect(Unit) {
    pending?.let {
        email = it
        authViewModel.clearPendingEmail()
    }
}
```

**Step 2: authOpState 구독 + 에러 처리**

```kotlin
val authOpState by authViewModel.authOpState.collectAsState()
val isLoading = authOpState is AuthOpState.Loading
val lastError = (authOpState as? AuthOpState.Failed)?.error

LaunchedEffect(lastError) {
    lastError?.let {
        if (it !is AppError.EmailNotConfirmed) {
            // 일반 에러는 스낵바
            snackbarHostState.showSnackbar(it.userMessage)
        }
        // EmailNotConfirmed는 inline 버튼으로 표시 — 스낵바 X
    }
}
```

**Step 3: EmailNotConfirmed inline 재전송 버튼**

비번 필드 아래에:
```kotlin
val notConfirmedEmail = (lastError as? AppError.EmailNotConfirmed)?.email
if (notConfirmedEmail != null) {
    Text(lastError.userMessage, color = MaterialTheme.colorScheme.error)
    val cooldown by authViewModel.resendCooldownSec.collectAsState()
    TextButton(
        enabled = cooldown == 0,
        onClick = { authViewModel.resendConfirmation(notConfirmedEmail) }
    ) {
        Text(if (cooldown == 0) "인증 메일 다시 보내기" else "${cooldown}초 후 다시 보낼 수 있어요")
    }
}
```

**Step 4: 빌드 확인**

Run: `./gradlew :app:assembleDebug`

---

### Task 16: 빌드 + lint + Commit (Tasks 11~15 묶음)

**Files:** (앞 task들의 변경 누적)

**Step 1: 전체 검증**

Run:
```bash
./gradlew :app:spotlessApply
./gradlew :app:detektDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```
Expected: 모두 통과. detekt가 새 baseline 위반 보고하면 `./gradlew :app:detektBaselineDebug` 로 baseline 갱신.

**Step 2: Commit**

```bash
git add app/src/main/java/com/gunnys/eundunhealth/ui/auth/SignupScreen.kt \
        app/src/main/java/com/gunnys/eundunhealth/ui/auth/LoginScreen.kt \
        app/src/main/java/com/gunnys/eundunhealth/ui/auth/AuthViewModel.kt \
        app/src/main/java/com/gunnys/eundunhealth/ui/navigation/AppNavigation.kt \
        app/src/test/java/com/gunnys/eundunhealth/ui/auth/AuthViewModelTest.kt
git commit -m "feat(auth): SignupScreen 상태 머신 UI + LoginScreen 자동 채움/재전송"
```

---

## Phase 7: 런타임 검증 (Supabase 가정)

### Task 17: 첫 빌드 + logcat 검증

**Files:** (코드 변경 없음 — 검증만)

**Step 1: 실기기에 debug APK 설치**

Run:
```bash
./gradlew :app:installDebug
```

**Step 2: logcat 모니터 시작**

```bash
adb logcat -c && adb logcat *:W io.github.jan:V Supabase:V AndroidRuntime:E -v time
```

**Step 3: 실제 가입 시도 → logcat 출력 캡처**

- 새 이메일로 가입 → AwaitingEmailConfirmation 카드 표시 + logcat에서 supabase 호출 흔적 확인
- 메일 인증 안 한 채 Login에서 같은 이메일로 시도 → 정확한 에러 메시지 문자열 캡처
  - 예상: `"email_not_confirmed"` 또는 `"Email not confirmed"` — 둘 다 매칭하도록 `mapAuthError`에 이미 구현됨

**Step 4: 캡처된 문자열을 mapAuthError 검증에 사용**

만약 다른 문자열이 발견되면 (`AuthErrorMappingTest`에 케이스 추가) + `mapAuthError`에 매칭 추가 + commit.

**Step 5: design doc §8 가정 검증 항목을 ✓로 갱신**

`docs/plans/2026-05-26-signup-confirmation-flow-design.md` 의 §8 표 ⚠️ → ✅ 또는 실제 결과 반영.

**Step 6: Commit (필요 시)**

```bash
git add docs/plans/2026-05-26-signup-confirmation-flow-design.md \
        # mapAuthError 매칭 추가 시 파일들
git commit -m "verify(auth): Supabase 가정 런타임 검증 + mapAuthError 매칭 보강"
```

---

## Phase 8: 수동 검증 + 출시

### Task 18: 수동 검증 체크리스트 (release APK)

**Files:** (검증만)

**Step 1: release APK 빌드 + 설치**

Run:
```bash
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

**Step 2: design doc §9.수동 검증 체크리스트 7개 항목 실행**

1. 새 이메일 가입 → 안내 카드 + 실제 메일 도착
2. 재전송 버튼 → 새 메일 + 60초 카운트다운 동작 확인
3. 메일 인증 → 앱 복귀 → 로그인하러 가기 → 이메일 자동 채움 → 로그인 성공 → Onboarding 진입
4. 미인증 상태로 Login 시도 → "인증 메일 다시 보내기" inline 노출 → 클릭 → 메일 재전송
5. 같은 이메일 재가입 → "이미 가입된 이메일..." 메시지 + 화면 유지 (튕기지 않음)
6. 약한 비번(5자) 입력 → 가입 버튼 disabled + supportingText 표시
7. 비행기 모드 → 가입 시도 → "네트워크 연결을 확인해주세요" + 화면 유지

각 항목 결과를 PR description에 체크리스트로 첨부.

**Step 3: 회귀 검증 (기존 흐름)**
- 로그인 성공 → Home 진입
- 로그아웃 → Login 화면 복귀
- 비밀번호 재설정 → Task 10 회귀 검증 재실행
- 계정 삭제 → Login 복귀

**Step 4: 문제 발견 시 별도 fix commit** (해당 task의 코드 위치만 수정).

**Commit 없음 (검증 단계)**

---

### Task 19: versionCode 14 → 15 bump

**Files:**
- Modify: `app/build.gradle.kts:74-75`

**Step 1: 수정**

```kotlin
versionCode = 15
versionName = "0.1.1"
```

> versionName 결정: 사용자에게 확인 받기. 기능 추가 + 버그 수정이므로 0.1.1(patch) 또는 0.2.0(minor) 중. 본 plan은 0.1.1로 기본값 제안.

**Step 2: CHANGELOG.md 업데이트**

`docs/CHANGELOG.md`에 v0.1.1 항목 추가:
- Added: 회원가입 이메일 확인 흐름 (안내 카드, 60초 재전송, 자동 채움)
- Added: Login 미인증 inline 재전송
- Changed: 인증 상태 모델을 SessionState/AuthOpState/SignupState로 분리
- Fixed: 가입/로그인 실패 시 Login으로 튕겨 스낵바가 잘리던 구조적 버그

**Step 3: PRD/SPEC versionCode 갱신 (해당 부분)**

`docs/PRD.md`, `docs/SPEC.md`에서 versionCode 14 → 15 갱신.

**Step 4: Commit**

```bash
git add app/build.gradle.kts docs/CHANGELOG.md docs/PRD.md docs/SPEC.md
git commit -m "release(android): versionCode 15 + 회원가입 흐름 안정화"
```

---

### Task 20: PR 생성

**Files:** (PR 메타데이터만)

**Step 1: 브랜치 push**

```bash
git push -u origin feat/signup-email-confirmation
```

**Step 2: gh로 PR 생성**

```bash
gh pr create --title "feat(auth): 회원가입 이메일 확인 흐름 + 인증 상태 리팩터" --body "$(cat <<'EOF'
## Summary
- Supabase email confirmation 켜진 상태에서 가입→메일 인증→로그인 흐름 출시 품질로 정비
- 가입/로그인 실패 시 화면이 Login으로 튕기며 스낵바가 잘리던 구조적 버그 제거 (`SessionState`/`AuthOpState`/`SignupState` 분리)
- `AppError.EmailNotConfirmed(email)` 신규 + Login 화면에서 inline 메일 재전송
- 메일 재전송 60초 쿨다운

## Design / SDD
- Design: `docs/plans/2026-05-26-signup-confirmation-flow-design.md`
- SPEC 갱신: Task 1
- Plan: `docs/plans/2026-05-26-signup-confirmation-flow-plan.md`

## Test plan
- [x] AppErrorTest — EmailNotConfirmed 매핑
- [x] AuthErrorMappingTest — mapAuthError 6개 케이스
- [x] AuthViewModelTest — signup/login/resendConfirmation/pendingEmail/resetPassword
- [ ] 수동: 새 이메일 가입 → 안내 카드 + 메일 도착
- [ ] 수동: 재전송 → 60초 쿨다운
- [ ] 수동: 메일 인증 → 자동 채움 → 로그인 성공
- [ ] 수동: 미인증 Login → inline 재전송 노출
- [ ] 수동: 이미 가입된 이메일 재가입 → 안내 메시지 + 화면 유지
- [ ] 수동: 약한 비번 inline 차단
- [ ] 수동: 비행기 모드 가입 → 네트워크 메시지
- [ ] 회귀: 비밀번호 재설정 / 로그인-로그아웃 / 계정 삭제

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

**Step 3: PR URL 출력**

PR URL을 사용자에게 알려주고, internal testing 빌드를 Play Console에 업로드할지 여부는 사용자 결정.

---

## 부록 — 변경 파일 요약

| 변경 종류 | 파일 |
|---|---|
| Create | `app/src/test/java/com/gunnys/eundunhealth/data/auth/AuthErrorMappingTest.kt` |
| Create | `docs/plans/2026-05-26-signup-confirmation-flow-plan.md` (본 문서) |
| Modify | `docs/SPEC.md` |
| Modify | `docs/PRD.md` |
| Modify | `docs/CHANGELOG.md` |
| Modify | `app/build.gradle.kts` |
| Modify | `app/src/main/java/com/gunnys/eundunhealth/domain/model/AppError.kt` |
| Modify | `app/src/main/java/com/gunnys/eundunhealth/domain/repository/AuthRepository.kt` |
| Modify | `app/src/main/java/com/gunnys/eundunhealth/data/auth/AuthRepositoryImpl.kt` |
| Modify | `app/src/main/java/com/gunnys/eundunhealth/ui/auth/AuthViewModel.kt` |
| Modify | `app/src/main/java/com/gunnys/eundunhealth/ui/auth/SignupScreen.kt` |
| Modify | `app/src/main/java/com/gunnys/eundunhealth/ui/auth/LoginScreen.kt` |
| Modify | `app/src/main/java/com/gunnys/eundunhealth/ui/auth/ForgotPasswordScreen.kt` |
| Modify | `app/src/main/java/com/gunnys/eundunhealth/ui/navigation/AppNavigation.kt` |
| Modify | `app/src/test/java/com/gunnys/eundunhealth/domain/model/AppErrorTest.kt` |
| Modify | `app/src/test/java/com/gunnys/eundunhealth/ui/auth/AuthViewModelTest.kt` |

총 16개 파일, 20개 task, 약 13개 commit.

## 부록 — 잠재 함정

1. **`OtpType.Email.SIGNUP` import 경로**: supabase-kt 3.x에서 정확한 경로가 다를 수 있음. IDE 자동완성으로 해결. 컴파일 실패 시 `io.github.jan.supabase.auth.*` 하위에서 검색.
2. **mockk가 SupabaseClient를 직접 mock 어려움**: 본 plan은 `AuthRepository` 인터페이스 레벨에서 mock — `AuthRepositoryImpl`의 Supabase 호출은 단위 테스트하지 않고 런타임 검증(Task 17)에 의존.
3. **`advanceTimeBy`로 60초 카운트다운 테스트**: `StandardTestDispatcher`에서 `delay`가 가상 시간으로 동작 — `advanceTimeBy(60_000)` 으로 한 번에 진행 가능. 실제 60초 기다리지 않음.
4. **`UserRepository.getProfile()` mock 반환**: 404가 와도 null로 처리되는 구조 (`.getOrNull() != null`). 테스트에서 `Result.success(mockk(relaxed = true))` 로 충분.
5. **`AppNavigation`의 `popUpTo(0) inclusive=true`**: Authenticated 진입 시 SignupScreen이 정상적으로 dispose되며 Onboarding으로 이동. AwaitingEmailConfirmation 상태에서는 sessionState가 변하지 않아 이 navigate가 트리거되지 않음 — 안전.
6. **detekt baseline**: 코드 추가/리팩터 후 새 위반이 baseline에 없으면 detektDebug 실패. `./gradlew :app:detektBaselineDebug` 로 갱신 후 `config/detekt/baseline.xml` 커밋.
