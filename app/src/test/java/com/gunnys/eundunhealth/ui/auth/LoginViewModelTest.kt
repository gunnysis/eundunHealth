package com.gunnys.eundunhealth.ui.auth

import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.model.UserProfile
import com.gunnys.eundunhealth.domain.repository.AuthRepository
import com.gunnys.eundunhealth.domain.repository.SignupResult
import com.gunnys.eundunhealth.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `login 성공 시 LoginSuccess SideEffect 발행`() = runTest {
        val vm = createViewModel(
            signInResult = Result.success("user-1"),
            profile = UserProfile("user-1", 175f, 70f, 20f, 30f),
        )

        var effect: LoginSideEffect? = null
        val job = launch { effect = vm.sideEffect.first() }

        vm.login("a@b.com", "pw")
        advanceUntilIdle()
        job.join()

        assertTrue(effect is LoginSideEffect.LoginSuccess)
        val success = effect as LoginSideEffect.LoginSuccess
        assertEquals("user-1", success.userId)
        assertEquals(false, success.needsOnboarding)
    }

    @Test
    fun `login 성공 + 프로필 없음 시 needsOnboarding=true`() = runTest {
        val vm = createViewModel(signInResult = Result.success("user-1"))

        var effect: LoginSideEffect? = null
        val job = launch { effect = vm.sideEffect.first() }

        vm.login("a@b.com", "pw")
        advanceUntilIdle()
        job.join()

        assertTrue(effect is LoginSideEffect.LoginSuccess)
        assertEquals(true, (effect as LoginSideEffect.LoginSuccess).needsOnboarding)
    }

    @Test
    fun `login EmailNotConfirmed 에러 시 uiState=Failed(EmailNotConfirmed)`() = runTest {
        val vm = createViewModel(
            signInResult = Result.failure(
                com.gunnys.eundunhealth.data.auth.AppErrorException(
                    AppError.EmailNotConfirmed("a@b.com"),
                ),
            ),
        )

        vm.login("a@b.com", "pw")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is LoginUiState.Failed)
        assertTrue((state as LoginUiState.Failed).error is AppError.EmailNotConfirmed)
    }

    @Test
    fun `consumeError 시 Failed → Idle`() = runTest {
        val vm = createViewModel(
            signInResult = Result.failure(
                com.gunnys.eundunhealth.data.auth.AppErrorException(AppError.Auth("에러")),
            ),
        )

        vm.login("a@b.com", "pw")
        advanceUntilIdle()
        assertTrue(vm.uiState.value is LoginUiState.Failed)

        vm.consumeError()
        assertEquals(LoginUiState.Idle, vm.uiState.value)
    }

    @Test
    fun `setExternalError 시 uiState=Failed`() = runTest {
        val vm = createViewModel()

        vm.setExternalError(AppError.Auth("deep link 에러"))

        val state = vm.uiState.value
        assertTrue(state is LoginUiState.Failed)
        assertTrue((state as LoginUiState.Failed).error is AppError.Auth)
    }

    @Test
    fun `resendConfirmation 성공 시 60초 쿨다운 시작 후 0으로 감소`() = runTest {
        val vm = createViewModel(resendConfirmationResult = Result.success(Unit))

        vm.resendConfirmation("a@b.com")
        advanceTimeBy(1)
        runCurrent()
        assertEquals(60, vm.resendCooldownSec.value)

        advanceTimeBy(60_000)
        assertEquals(0, vm.resendCooldownSec.value)
    }

    @Test
    fun `resendConfirmation 실패 시 resendError 설정 + 쿨다운 시작하지 않음`() = runTest {
        val vm = createViewModel(
            resendConfirmationResult = Result.failure(
                com.gunnys.eundunhealth.data.auth.AppErrorException(AppError.Network()),
            ),
        )

        vm.resendConfirmation("a@b.com")
        advanceUntilIdle()

        assertEquals(0, vm.resendCooldownSec.value)
        assertTrue(vm.resendError.value is AppError.Network)
    }

    @Test
    fun `clearResendError 후 resendError 가 null`() = runTest {
        val vm = createViewModel(
            resendConfirmationResult = Result.failure(
                com.gunnys.eundunhealth.data.auth.AppErrorException(AppError.Network()),
            ),
        )

        vm.resendConfirmation("a@b.com")
        advanceUntilIdle()
        assertTrue(vm.resendError.value != null)

        vm.clearResendError()
        assertEquals(null, vm.resendError.value)
    }

    // ---- Helpers ----

    private fun createViewModel(
        signInResult: Result<String> = Result.failure(IllegalStateException("not stubbed")),
        profile: UserProfile? = null,
        resendConfirmationResult: Result<Unit> = Result.failure(IllegalStateException("not stubbed")),
    ): LoginViewModel {
        val authRepo = FakeAuthRepository(
            signInResult = signInResult,
            resendConfirmationResult = resendConfirmationResult,
        )
        val userRepo = FakeUserRepository(profile = profile)
        return LoginViewModel(authRepo, userRepo)
    }

    private class FakeAuthRepository(
        private val signInResult: Result<String> = Result.failure(IllegalStateException("not stubbed")),
        private val resendConfirmationResult: Result<Unit> = Result.failure(IllegalStateException("not stubbed")),
    ) : AuthRepository {
        override suspend fun signIn(email: String, password: String): Result<String> = signInResult
        override suspend fun signUp(email: String, password: String): Result<SignupResult> = Result.failure(IllegalStateException("not stubbed"))
        override suspend fun resendConfirmation(email: String): Result<Unit> = resendConfirmationResult
        override suspend fun signOut(): Result<Unit> = Result.success(Unit)
        override suspend fun resetPassword(email: String): Result<Unit> = Result.failure(IllegalStateException("not stubbed"))
        override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)
        override suspend fun getCurrentUserId(): String? = null
        override fun isLoggedIn(): Boolean = false
        override fun restoreSession(): String? = null
    }

    private class FakeUserRepository(
        private val profile: UserProfile? = null,
    ) : UserRepository {
        override suspend fun getProfile(): Result<UserProfile?> = Result.success(profile)
        override suspend fun saveProfile(profile: UserProfile): Result<Unit> = Result.success(Unit)
    }
}
