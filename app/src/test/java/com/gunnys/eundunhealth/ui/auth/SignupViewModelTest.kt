package com.gunnys.eundunhealth.ui.auth

import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.repository.AuthRepository
import com.gunnys.eundunhealth.domain.repository.SignupResult
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
class SignupViewModelTest {

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
    fun `signup AwaitingConfirmation 결과 시 uiState 가 AwaitingEmailConfirmation`() = runTest {
        val vm = createViewModel(
            signUpResult = Result.success(SignupResult.AwaitingConfirmation("a@b.com")),
        )

        vm.signup("a@b.com", "password123")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is SignupUiState.AwaitingEmailConfirmation)
        assertEquals("a@b.com", (state as SignupUiState.AwaitingEmailConfirmation).email)
    }

    @Test
    fun `signup AutoSignedIn 결과 시 AutoSignedIn SideEffect 발행`() = runTest {
        val vm = createViewModel(
            signUpResult = Result.success(SignupResult.AutoSignedIn("user-1")),
        )

        var effect: SignupSideEffect? = null
        val job = launch { effect = vm.sideEffect.first() }

        vm.signup("a@b.com", "pw")
        advanceUntilIdle()
        job.join()

        assertTrue(effect is SignupSideEffect.AutoSignedIn)
        assertEquals("user-1", (effect as SignupSideEffect.AutoSignedIn).userId)
        assertEquals(SignupUiState.Form, vm.uiState.value)
    }

    @Test
    fun `signup 실패 시 uiState 가 Failed`() = runTest {
        val vm = createViewModel(
            signUpResult = Result.failure(
                com.gunnys.eundunhealth.data.auth.AppErrorException(
                    AppError.Auth("이미 가입된 이메일입니다"),
                ),
            ),
        )

        vm.signup("a@b.com", "pw")
        advanceUntilIdle()

        assertTrue(vm.uiState.value is SignupUiState.Failed)
    }

    @Test
    fun `clearSignupError transitions Failed to Form`() = runTest {
        val vm = createViewModel(
            signUpResult = Result.failure(
                com.gunnys.eundunhealth.data.auth.AppErrorException(
                    AppError.Auth("요청이 너무 많습니다"),
                ),
            ),
        )

        vm.signup("a@b.com", "password123")
        advanceUntilIdle()
        assertTrue(vm.uiState.value is SignupUiState.Failed)

        vm.clearSignupError()
        assertEquals(SignupUiState.Form, vm.uiState.value)
    }

    @Test
    fun `clearSignupError is no-op when state is Form (D6)`() = runTest {
        val vm = createViewModel(
            signUpResult = Result.success(SignupResult.AwaitingConfirmation("a@b.com")),
        )

        assertEquals(SignupUiState.Form, vm.uiState.value)
        vm.clearSignupError()
        assertEquals(SignupUiState.Form, vm.uiState.value)
    }

    @Test
    fun `resetSignupState 시 Form 으로 전환`() = runTest {
        val vm = createViewModel(
            signUpResult = Result.success(SignupResult.AwaitingConfirmation("a@b.com")),
        )

        vm.signup("a@b.com", "pw")
        advanceUntilIdle()
        assertTrue(vm.uiState.value is SignupUiState.AwaitingEmailConfirmation)

        vm.resetSignupState()
        assertEquals(SignupUiState.Form, vm.uiState.value)
    }

    @Test
    fun `resendConfirmation 성공 시 60초 쿨다운 시작`() = runTest {
        val vm = createViewModel(
            signUpResult = Result.failure(IllegalStateException("not used")),
            resendConfirmationResult = Result.success(Unit),
        )

        vm.resendConfirmation("a@b.com")
        advanceTimeBy(1)
        runCurrent()
        assertEquals(60, vm.resendCooldownSec.value)

        advanceTimeBy(60_000)
        assertEquals(0, vm.resendCooldownSec.value)
    }

    @Test
    fun `resendConfirmation 실패 시 resendError 설정`() = runTest {
        val vm = createViewModel(
            signUpResult = Result.failure(IllegalStateException("not used")),
            resendConfirmationResult = Result.failure(
                com.gunnys.eundunhealth.data.auth.AppErrorException(AppError.Network()),
            ),
        )

        vm.resendConfirmation("a@b.com")
        advanceUntilIdle()

        assertEquals(0, vm.resendCooldownSec.value)
        assertTrue(vm.resendError.value is AppError.Network)
    }

    // ---- Helpers ----

    private fun createViewModel(
        signUpResult: Result<SignupResult>,
        resendConfirmationResult: Result<Unit> = Result.failure(IllegalStateException("not stubbed")),
    ): SignupViewModel {
        val authRepo = FakeAuthRepository(
            signUpResult = signUpResult,
            resendConfirmationResult = resendConfirmationResult,
        )
        return SignupViewModel(authRepo)
    }

    private class FakeAuthRepository(
        private val signUpResult: Result<SignupResult>,
        private val resendConfirmationResult: Result<Unit> = Result.failure(IllegalStateException("not stubbed")),
    ) : AuthRepository {
        override suspend fun signIn(email: String, password: String): Result<String> = Result.failure(IllegalStateException("not stubbed"))
        override suspend fun signUp(email: String, password: String): Result<SignupResult> = signUpResult
        override suspend fun resendConfirmation(email: String): Result<Unit> = resendConfirmationResult
        override suspend fun signOut(): Result<Unit> = Result.success(Unit)
        override suspend fun resetPassword(email: String): Result<Unit> = Result.failure(IllegalStateException("not stubbed"))
        override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)
        override suspend fun getCurrentUserId(): String? = null
        override fun isLoggedIn(): Boolean = false
        override fun restoreSession(): String? = null
    }
}
