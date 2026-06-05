package com.gunnys.eundunhealth.ui.auth

import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.repository.AuthRepository
import com.gunnys.eundunhealth.domain.repository.SignupResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ForgotPasswordViewModelTest {

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
    fun `resetPassword 성공 시 ResetSent SideEffect 발행 + uiState=Idle`() = runTest {
        val vm = createViewModel(resetPasswordResult = Result.success(Unit))

        var effect: ForgotPasswordSideEffect? = null
        val job = launch { effect = vm.sideEffect.first() }

        vm.resetPassword("a@b.com")
        advanceUntilIdle()
        job.join()

        assertTrue(effect is ForgotPasswordSideEffect.ResetSent)
        assertEquals(ForgotPasswordUiState.Idle, vm.uiState.value)
    }

    @Test
    fun `resetPassword 실패 시 uiState=Failed`() = runTest {
        val vm = createViewModel(
            resetPasswordResult = Result.failure(
                com.gunnys.eundunhealth.data.auth.AppErrorException(AppError.Network()),
            ),
        )

        vm.resetPassword("a@b.com")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is ForgotPasswordUiState.Failed)
        assertTrue((state as ForgotPasswordUiState.Failed).error is AppError.Network)
    }

    @Test
    fun `consumeError 시 Failed → Idle`() = runTest {
        val vm = createViewModel(
            resetPasswordResult = Result.failure(
                com.gunnys.eundunhealth.data.auth.AppErrorException(AppError.Auth("에러")),
            ),
        )

        vm.resetPassword("a@b.com")
        advanceUntilIdle()
        assertTrue(vm.uiState.value is ForgotPasswordUiState.Failed)

        vm.consumeError()
        assertEquals(ForgotPasswordUiState.Idle, vm.uiState.value)
    }

    // ---- Helpers ----

    private fun createViewModel(
        resetPasswordResult: Result<Unit>,
    ): ForgotPasswordViewModel {
        val authRepo = FakeAuthRepository(resetPasswordResult = resetPasswordResult)
        return ForgotPasswordViewModel(authRepo)
    }

    private class FakeAuthRepository(
        private val resetPasswordResult: Result<Unit>,
    ) : AuthRepository {
        override suspend fun signIn(email: String, password: String): Result<String> = Result.failure(IllegalStateException("not stubbed"))
        override suspend fun signUp(email: String, password: String): Result<SignupResult> = Result.failure(IllegalStateException("not stubbed"))
        override suspend fun resendConfirmation(email: String): Result<Unit> = Result.failure(IllegalStateException("not stubbed"))
        override suspend fun signOut(): Result<Unit> = Result.success(Unit)
        override suspend fun resetPassword(email: String): Result<Unit> = resetPasswordResult
        override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)
        override suspend fun getCurrentUserId(): String? = null
        override fun isLoggedIn(): Boolean = false
        override fun restoreSession(): String? = null
    }
}
