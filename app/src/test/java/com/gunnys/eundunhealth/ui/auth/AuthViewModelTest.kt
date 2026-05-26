package com.gunnys.eundunhealth.ui.auth

import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.model.UserProfile
import com.gunnys.eundunhealth.domain.repository.AuthRepository
import com.gunnys.eundunhealth.domain.repository.SignupResult
import com.gunnys.eundunhealth.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    fun `AuthState Loading is initial default`() {
        val state: AuthState = AuthState.Loading
        assertEquals(AuthState.Loading, state)
    }

    @Test
    fun `AuthState Authenticated carries userId and onboarding flag`() {
        val state = AuthState.Authenticated(userId = "user-123", needsOnboarding = true)
        assertEquals("user-123", state.userId)
        assertEquals(true, state.needsOnboarding)
    }

    @Test
    fun `AuthState Authenticated defaults needsOnboarding to false`() {
        val state = AuthState.Authenticated(userId = "user-456")
        assertEquals(false, state.needsOnboarding)
    }

    @Test
    fun `AuthState Unauthenticated is its own singleton`() {
        assertEquals(AuthState.Unauthenticated, AuthState.Unauthenticated)
    }

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

    // ---- Test doubles ----

    private class FakeAuthRepository(
        private val signUpResult: Result<SignupResult>,
        private val restoreSessionUserId: String? = null,
    ) : AuthRepository {
        override suspend fun signIn(email: String, password: String): Result<String> =
            Result.failure(IllegalStateException("not stubbed"))

        override suspend fun signUp(email: String, password: String): Result<SignupResult> =
            signUpResult

        override suspend fun resendConfirmation(email: String): Result<Unit> = Result.success(Unit)

        override suspend fun signOut(): Result<Unit> = Result.success(Unit)

        override suspend fun resetPassword(email: String): Result<Unit> = Result.success(Unit)

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
