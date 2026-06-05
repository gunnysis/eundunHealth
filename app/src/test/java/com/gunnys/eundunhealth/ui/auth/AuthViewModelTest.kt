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
 * AuthViewModel 테스트 — 세션 관리 + deep link + pendingEmail + onAuthSuccess.
 * 로그인/회원가입/비밀번호 재설정 테스트는 각각 LoginViewModelTest, SignupViewModelTest,
 * ForgotPasswordViewModelTest 로 분리됨.
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

    // ---- SessionState data class identity ----

    @Test
    fun `SessionState Authenticated carries userId and onboarding flag`() {
        val state = SessionState.Authenticated(userId = "u-1", needsOnboarding = true)
        assertEquals("u-1", state.userId)
        assertEquals(true, state.needsOnboarding)
    }

    // ---- pendingEmail set/clear ----

    @Test
    fun `pendingEmail set 후 clear 동작`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(null, vm.pendingEmail.value)
        vm.setPendingEmail("a@b.com")
        assertEquals("a@b.com", vm.pendingEmail.value)
        vm.clearPendingEmail()
        assertEquals(null, vm.pendingEmail.value)
    }

    // ---- onAuthSuccess ----

    @Test
    fun `onAuthSuccess 시 sessionState=Authenticated + pendingEmail 클리어`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.setPendingEmail("a@b.com")
        vm.onAuthSuccess("user-1", needsOnboarding = true)

        val session = vm.sessionState.value
        assertTrue(session is SessionState.Authenticated)
        assertEquals("user-1", (session as SessionState.Authenticated).userId)
        assertEquals(true, session.needsOnboarding)
        assertEquals(null, vm.pendingEmail.value)
    }

    // ---- onDeepLinkSuccess / onDeepLinkError ----

    @Test
    fun `onDeepLinkSuccess 신규 사용자(프로필 없음) → Authenticated needsOnboarding=true`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onDeepLinkSuccess("user-1")
        advanceUntilIdle()

        val session = vm.sessionState.value
        assertTrue(session is SessionState.Authenticated)
        assertEquals("user-1", (session as SessionState.Authenticated).userId)
        assertEquals(true, session.needsOnboarding)
    }

    @Test
    fun `onDeepLinkSuccess 기존 사용자(프로필 있음) → Authenticated needsOnboarding=false`() = runTest {
        val userProfile = UserProfile(
            userId = "user-1",
            heightCm = 170f,
            weightKg = 65f,
            bodyFatPercent = 20f,
            muscleMassKg = 30f,
        )
        val vm = createViewModel(profile = userProfile)
        advanceUntilIdle()

        vm.onDeepLinkSuccess("user-1")
        advanceUntilIdle()

        val session = vm.sessionState.value
        assertTrue(session is SessionState.Authenticated)
        assertEquals(false, (session as SessionState.Authenticated).needsOnboarding)
    }

    @Test
    fun `onDeepLinkError AppErrorException 시 deepLinkError 설정 + sessionState=Unauthenticated`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        val cause = com.gunnys.eundunhealth.data.auth.AppErrorException(
            AppError.Auth("인증 링크가 만료되었습니다. 다시 가입해주세요"),
        )
        vm.onDeepLinkError(cause)
        advanceUntilIdle()

        val error = vm.deepLinkError.value
        assertTrue(error is AppError.Auth)
        assertEquals(SessionState.Unauthenticated, vm.sessionState.value)
    }

    @Test
    fun `onDeepLinkError 일반 예외는 toAppError 폴백`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onDeepLinkError(java.net.UnknownHostException("no dns"))
        advanceUntilIdle()

        val error = vm.deepLinkError.value
        assertTrue(error is AppError.Network)
        assertEquals(SessionState.Unauthenticated, vm.sessionState.value)
    }

    @Test
    fun `consumeDeepLinkError 후 deepLinkError 가 null`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onDeepLinkError(
            com.gunnys.eundunhealth.data.auth.AppErrorException(AppError.Auth("에러")),
        )
        assertTrue(vm.deepLinkError.value != null)

        vm.consumeDeepLinkError()
        assertEquals(null, vm.deepLinkError.value)
    }

    // ---- logout ----

    @Test
    fun `logout 시 sessionState=Unauthenticated`() = runTest {
        val vm = createViewModel(restoreSessionUserId = "user-1")
        advanceUntilIdle()

        vm.logout()
        advanceUntilIdle()

        assertEquals(SessionState.Unauthenticated, vm.sessionState.value)
    }

    // ---- Helpers ----

    private fun createViewModel(
        restoreSessionUserId: String? = null,
        profile: UserProfile? = null,
    ): AuthViewModel {
        val authRepo = FakeAuthRepository(restoreSessionUserId = restoreSessionUserId)
        val userRepo = FakeUserRepository(profile = profile)
        return AuthViewModel(authRepo, userRepo)
    }

    private class FakeAuthRepository(
        private val restoreSessionUserId: String? = null,
    ) : AuthRepository {
        override suspend fun signIn(email: String, password: String): Result<String> = Result.failure(IllegalStateException("not stubbed"))
        override suspend fun signUp(email: String, password: String): Result<SignupResult> = Result.failure(IllegalStateException("not stubbed"))
        override suspend fun resendConfirmation(email: String): Result<Unit> = Result.failure(IllegalStateException("not stubbed"))
        override suspend fun signOut(): Result<Unit> = Result.success(Unit)
        override suspend fun resetPassword(email: String): Result<Unit> = Result.failure(IllegalStateException("not stubbed"))
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
