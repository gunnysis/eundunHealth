package com.gunnys.eundunhealth.ui.auth

import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.model.UserProfile
import com.gunnys.eundunhealth.domain.repository.AuthRepository
import com.gunnys.eundunhealth.domain.repository.SignupResult
import com.gunnys.eundunhealth.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * NOTE on test doubles: mockk 1.13.x has a known bug where suspend functions returning `kotlin.Result`
 * (an inline value class) crash with ClassCastException at the coroutine ABI boundary even when stubbed
 * with `coEvery { ... } returns Result.success(...)`. To keep these tests reliable we use lightweight
 * fakes instead of mocks for the repositories. The fakes only implement the methods this VM touches.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- Sealed / data class identity tests (pure, no coroutines) ----

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

    // ---- signup behavior tests ----

    @Test
    fun `signup AwaitingConfirmation 결과 시 signupState 가 AwaitingEmailConfirmation`() = runTest {
        val authRepo = FakeAuthRepository(
            signUpResult = Result.success(SignupResult.AwaitingConfirmation("a@b.com")),
        )
        val userRepo = FakeUserRepository()

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
        val authRepo = FakeAuthRepository(
            signUpResult = Result.success(SignupResult.AutoSignedIn("user-1")),
        )
        val userRepo = FakeUserRepository()

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
        val authRepo = FakeAuthRepository(
            signUpResult = Result.failure(
                com.gunnys.eundunhealth.data.auth.AppErrorException(AppError.Auth("이미 가입된 이메일입니다")),
            ),
        )
        val userRepo = FakeUserRepository()

        val vm = AuthViewModel(authRepo, userRepo)
        advanceUntilIdle()
        vm.signup("a@b.com", "pw")
        advanceUntilIdle()

        val state = vm.signupState.value
        assertTrue(state is SignupState.Failed)
    }

    // ---- login behavior tests ----

    @Test
    fun `login 성공 시 sessionState=Authenticated, authOpState 가 Idle 로 복귀`() = runTest {
        val authRepo = FakeAuthRepository(
            signUpResult = Result.failure(IllegalStateException("not used")),
            signInResult = Result.success("user-1"),
        )
        val userRepo = FakeUserRepository(
            profile = UserProfile(
                userId = "user-1",
                heightCm = 175f,
                weightKg = 70f,
                bodyFatPercent = 20f,
                muscleMassKg = 30f,
            ),
        )

        val vm = AuthViewModel(authRepo, userRepo)
        advanceUntilIdle()
        vm.login("a@b.com", "pw")
        advanceUntilIdle()

        assertTrue(vm.sessionState.value is SessionState.Authenticated)
        assertEquals(AuthOpState.Idle, vm.authOpState.value)
    }

    @Test
    fun `login EmailNotConfirmed 에러 시 authOpState=Failed(EmailNotConfirmed)`() = runTest {
        val authRepo = FakeAuthRepository(
            signUpResult = Result.failure(IllegalStateException("not used")),
            signInResult = Result.failure(
                com.gunnys.eundunhealth.data.auth.AppErrorException(
                    AppError.EmailNotConfirmed("a@b.com"),
                ),
            ),
        )
        val userRepo = FakeUserRepository()

        val vm = AuthViewModel(authRepo, userRepo)
        advanceUntilIdle()
        vm.login("a@b.com", "pw")
        advanceUntilIdle()

        val state = vm.authOpState.value
        assertTrue(state is AuthOpState.Failed)
        assertTrue((state as AuthOpState.Failed).error is AppError.EmailNotConfirmed)
    }

    // ---- resendConfirmation behavior tests ----

    @Test
    fun `resendConfirmation 성공 시 60초 쿨다운 시작 후 0으로 감소`() = runTest {
        val authRepo = FakeAuthRepository(
            signUpResult = Result.failure(IllegalStateException("not used")),
            resendConfirmationResult = Result.success(Unit),
        )
        val userRepo = FakeUserRepository()

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
        val authRepo = FakeAuthRepository(
            signUpResult = Result.failure(IllegalStateException("not used")),
            resendConfirmationResult = Result.failure(
                com.gunnys.eundunhealth.data.auth.AppErrorException(AppError.Network()),
            ),
        )
        val userRepo = FakeUserRepository()

        val vm = AuthViewModel(authRepo, userRepo)
        advanceUntilIdle()
        vm.resendConfirmation("a@b.com")
        advanceUntilIdle()

        assertEquals(0, vm.resendCooldownSec.value)
    }

    // ---- pendingEmail set/clear ----

    @Test
    fun `pendingEmail set 후 clear 동작`() = runTest {
        val authRepo = FakeAuthRepository(
            signUpResult = Result.failure(IllegalStateException("not used")),
        )
        val userRepo = FakeUserRepository()
        val vm = AuthViewModel(authRepo, userRepo)
        advanceUntilIdle()

        assertEquals(null, vm.pendingEmail.value)
        vm.setPendingEmail("a@b.com")
        assertEquals("a@b.com", vm.pendingEmail.value)
        vm.clearPendingEmail()
        assertEquals(null, vm.pendingEmail.value)
    }

    // ---- resetPassword behavior tests ----

    @Test
    fun `resetPassword 성공 시 authOpState 가 Idle 로 복귀하고 별도 success 플래그 노출`() = runTest {
        val authRepo = FakeAuthRepository(
            signUpResult = Result.failure(IllegalStateException("not used")),
            resetPasswordResult = Result.success(Unit),
        )
        val userRepo = FakeUserRepository()
        val vm = AuthViewModel(authRepo, userRepo)
        advanceUntilIdle()
        vm.resetPassword("a@b.com")
        advanceUntilIdle()

        assertEquals(true, vm.passwordResetSent.value)
        assertEquals(AuthOpState.Idle, vm.authOpState.value)
    }

    @Test
    fun `resendConfirmation 실패 시 resendError 가 설정됨`() = runTest {
        val authRepo = FakeAuthRepository(
            signUpResult = Result.failure(IllegalStateException("not used")),
            resendConfirmationResult = Result.failure(
                com.gunnys.eundunhealth.data.auth.AppErrorException(AppError.Network()),
            ),
        )
        val userRepo = FakeUserRepository()
        val vm = AuthViewModel(authRepo, userRepo)
        advanceUntilIdle()
        vm.resendConfirmation("a@b.com")
        advanceUntilIdle()

        assertEquals(0, vm.resendCooldownSec.value)
        assertTrue(vm.resendError.value is AppError.Network)
    }

    @Test
    fun `login 성공 + 프로필 없음 시 needsOnboarding=true`() = runTest {
        val authRepo = FakeAuthRepository(
            signUpResult = Result.failure(IllegalStateException("not used")),
            signInResult = Result.success("user-1"),
        )
        val userRepo = FakeUserRepository() // profile = null by default
        val vm = AuthViewModel(authRepo, userRepo)
        advanceUntilIdle()
        vm.login("a@b.com", "pw")
        advanceUntilIdle()

        val session = vm.sessionState.value
        assertTrue(session is SessionState.Authenticated)
        assertEquals(true, (session as SessionState.Authenticated).needsOnboarding)
    }

    @Test
    fun `resetPassword 실패 시 authOpState=Failed`() = runTest {
        val authRepo = FakeAuthRepository(
            signUpResult = Result.failure(IllegalStateException("not used")),
            resetPasswordResult = Result.failure(
                com.gunnys.eundunhealth.data.auth.AppErrorException(AppError.Network()),
            ),
        )
        val userRepo = FakeUserRepository()
        val vm = AuthViewModel(authRepo, userRepo)
        advanceUntilIdle()
        vm.resetPassword("a@b.com")
        advanceUntilIdle()

        assertEquals(false, vm.passwordResetSent.value)
        val state = vm.authOpState.value
        assertTrue(state is AuthOpState.Failed)
        assertTrue((state as AuthOpState.Failed).error is AppError.Network)
    }

    // ---- onDeepLinkSuccess / onDeepLinkError behavior tests ----

    @Test
    fun `onDeepLinkSuccess 신규 사용자(프로필 없음) → Authenticated needsOnboarding=true`() = runTest {
        val authRepo = FakeAuthRepository(
            signUpResult = Result.failure(IllegalStateException("not used")),
        )
        val userRepo = FakeUserRepository() // profile = null by default
        val vm = AuthViewModel(authRepo, userRepo)
        advanceUntilIdle()

        vm.onDeepLinkSuccess("user-1")
        advanceUntilIdle()

        val session = vm.sessionState.value
        assertTrue(session is SessionState.Authenticated)
        assertEquals("user-1", (session as SessionState.Authenticated).userId)
        assertEquals(true, session.needsOnboarding)
        assertEquals(AuthOpState.Idle, vm.authOpState.value)
    }

    @Test
    fun `onDeepLinkSuccess 기존 사용자(프로필 있음) → Authenticated needsOnboarding=false`() = runTest {
        val authRepo = FakeAuthRepository(
            signUpResult = Result.failure(IllegalStateException("not used")),
        )
        val userProfile = UserProfile(
            userId = "user-1",
            heightCm = 170f,
            weightKg = 65f,
            bodyFatPercent = 20f,
            muscleMassKg = 30f,
        )
        val userRepo = FakeUserRepository(profile = userProfile)
        val vm = AuthViewModel(authRepo, userRepo)
        advanceUntilIdle()

        vm.onDeepLinkSuccess("user-1")
        advanceUntilIdle()

        val session = vm.sessionState.value
        assertTrue(session is SessionState.Authenticated)
        assertEquals(false, (session as SessionState.Authenticated).needsOnboarding)
    }

    @Test
    fun `onDeepLinkError AppErrorException 시 authOpState=Failed + sessionState=Unauthenticated`() = runTest {
        val authRepo = FakeAuthRepository(
            signUpResult = Result.failure(IllegalStateException("not used")),
        )
        val userRepo = FakeUserRepository()
        val vm = AuthViewModel(authRepo, userRepo)
        advanceUntilIdle()

        val cause = com.gunnys.eundunhealth.data.auth.AppErrorException(
            AppError.Auth("인증 링크가 만료되었습니다. 다시 가입해주세요"),
        )
        vm.onDeepLinkError(cause)
        advanceUntilIdle()

        val state = vm.authOpState.value
        assertTrue(state is AuthOpState.Failed)
        assertTrue((state as AuthOpState.Failed).error is AppError.Auth)
        assertEquals(SessionState.Unauthenticated, vm.sessionState.value)
    }

    @Test
    fun `onDeepLinkError 일반 예외는 toAppError 폴백 + reportToSentry`() = runTest {
        val authRepo = FakeAuthRepository(
            signUpResult = Result.failure(IllegalStateException("not used")),
        )
        val userRepo = FakeUserRepository()
        val vm = AuthViewModel(authRepo, userRepo)
        advanceUntilIdle()

        vm.onDeepLinkError(java.net.UnknownHostException("no dns"))
        advanceUntilIdle()

        val state = vm.authOpState.value
        assertTrue(state is AuthOpState.Failed)
        assertTrue((state as AuthOpState.Failed).error is AppError.Network)
        assertEquals(SessionState.Unauthenticated, vm.sessionState.value)
    }

    // ---- Test doubles ----

    private class FakeAuthRepository(
        private val signUpResult: Result<SignupResult>,
        private val signInResult: Result<String> = Result.failure(IllegalStateException("not stubbed")),
        private val restoreSessionUserId: String? = null,
        private val resendConfirmationResult: Result<Unit> = Result.failure(IllegalStateException("not stubbed")),
        private val resetPasswordResult: Result<Unit> = Result.failure(IllegalStateException("not stubbed")),
    ) : AuthRepository {
        override suspend fun signIn(email: String, password: String): Result<String> = signInResult

        override suspend fun signUp(email: String, password: String): Result<SignupResult> = signUpResult

        override suspend fun resendConfirmation(email: String): Result<Unit> = resendConfirmationResult

        override suspend fun signOut(): Result<Unit> = Result.success(Unit)

        override suspend fun resetPassword(email: String): Result<Unit> = resetPasswordResult

        override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)

        override suspend fun getCurrentUserId(): String? = restoreSessionUserId

        override fun isLoggedIn(): Boolean = restoreSessionUserId != null

        override fun restoreSession(): String? = restoreSessionUserId
    }

    private class FakeUserRepository(
        private val profile: UserProfile? = null,
    ) : UserRepository {
        override suspend fun getProfile(): Result<UserProfile?> = Result.success(profile)

        override suspend fun saveProfile(profile: UserProfile): Result<Unit> = Result.success(Unit)
    }
}
