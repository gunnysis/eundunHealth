package com.gunnys.eundunhealth.ui.auth

import android.app.Activity
import com.gunnys.eundunhealth.data.auth.AppErrorException
import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.model.UserProfile
import com.gunnys.eundunhealth.domain.repository.AuthCancelledException
import com.gunnys.eundunhealth.domain.repository.AuthRepository
import com.gunnys.eundunhealth.domain.repository.UserRepository
import io.mockk.mockk
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
import java.net.UnknownHostException

/**
 * AuthViewModel 테스트 — 세션 복원 + 인증 게이트 상태 전이.
 *
 * 브라우저 위임 전환으로 Login/Signup/ForgotPassword VM 3종이 사라져 그 테스트들도 함께
 * 폐기됐다. 대신 여기서 게이트의 상태 전이를 검증한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val activity = mockk<Activity>(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- 세션 복원 ----

    @Test
    fun `세션 없으면 Unauthenticated`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(SessionState.Unauthenticated, vm.sessionState.value)
    }

    @Test
    fun `세션 복원 성공 + 프로필 없음 → 온보딩 필요`() = runTest {
        val vm = createViewModel(restoreSessionUserId = "user-1")
        advanceUntilIdle()

        val session = vm.sessionState.value
        assertTrue(session is SessionState.Authenticated)
        assertEquals("user-1", (session as SessionState.Authenticated).userId)
        assertEquals(true, session.needsOnboarding)
    }

    @Test
    fun `세션 복원 성공 + 프로필 있음 → 온보딩 불필요`() = runTest {
        val vm = createViewModel(restoreSessionUserId = "user-1", profileResult = Result.success(sampleProfile()))
        advanceUntilIdle()

        val session = vm.sessionState.value
        assertEquals(false, (session as SessionState.Authenticated).needsOnboarding)
    }

    @Test
    fun `세션 복원 시 프로필 조회 실패는 온보딩으로 보내지 않는다`() = runTest {
        // A1 회귀 가드. `getOrNull()` 로 되돌리면 실패가 "프로필 없음"과 같아져 기존 사용자가
        // 온보딩으로 라우팅되고, 온보딩을 마치면 PUT /profile 이 신체정보를 덮어쓴다(비가역).
        // 신규 사용자를 홈으로 보내는 반대 방향은 홈 에러 + 재시도로 회복 가능하다.
        val vm = createViewModel(
            restoreSessionUserId = "user-1",
            profileResult = Result.failure(UnknownHostException("offline")),
        )
        advanceUntilIdle()

        val session = vm.sessionState.value
        assertTrue(session is SessionState.Authenticated)
        assertEquals(false, (session as SessionState.Authenticated).needsOnboarding)
    }

    @Test
    fun `authenticate 직후 프로필 조회 실패도 온보딩으로 보내지 않는다`() = runTest {
        // checkSession 과 authenticate 두 경로가 같은 규칙을 쓰는지 고정 — 한쪽만 고쳐지는 회귀 방지.
        val vm = createViewModel(
            authenticateResult = Result.success("user-9"),
            profileResult = Result.failure(UnknownHostException("offline")),
        )
        advanceUntilIdle()

        vm.authenticate(activity)
        advanceUntilIdle()

        val session = vm.sessionState.value as SessionState.Authenticated
        assertEquals("user-9", session.userId)
        assertEquals(false, session.needsOnboarding)
    }

    @Test
    fun `authenticate 직후 프로필이 진짜 없으면 온보딩으로 보낸다`() = runTest {
        // 정상 경로는 그대로여야 한다 — 404(success(null))는 여전히 온보딩.
        val vm = createViewModel(
            authenticateResult = Result.success("user-9"),
            profileResult = Result.success(null),
        )
        advanceUntilIdle()

        vm.authenticate(activity)
        advanceUntilIdle()

        assertEquals(true, (vm.sessionState.value as SessionState.Authenticated).needsOnboarding)
    }

    // ---- 인증 게이트 상태 전이 (설계 §5.3) ----

    @Test
    fun `authenticate 성공 시 Authenticated 로 전환되고 게이트는 Idle 로 돌아온다`() = runTest {
        val vm = createViewModel(authenticateResult = Result.success("user-9"))
        advanceUntilIdle()

        vm.authenticate(activity)
        advanceUntilIdle()

        assertEquals(AuthGateUiState.Idle, vm.uiState.value)
        assertEquals("user-9", (vm.sessionState.value as SessionState.Authenticated).userId)
    }

    @Test
    fun `사용자 취소는 에러가 아니다 — 배너 없이 Idle 복귀`() = runTest {
        // 의도적 행동에 빨간 배너를 띄우면 안 된다(설계 §5.3).
        val vm = createViewModel(authenticateResult = Result.failure(AuthCancelledException()))
        advanceUntilIdle()

        vm.authenticate(activity)
        advanceUntilIdle()

        assertEquals(AuthGateUiState.Idle, vm.uiState.value)
        assertEquals(SessionState.Unauthenticated, vm.sessionState.value)
    }

    @Test
    fun `인증 실패는 Failed 로 전이하고 AppError 를 보존한다`() = runTest {
        val vm = createViewModel(
            authenticateResult = Result.failure(AppErrorException(AppError.Network())),
        )
        advanceUntilIdle()

        vm.authenticate(activity)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is AuthGateUiState.Failed)
        assertTrue((state as AuthGateUiState.Failed).error is AppError.Network)
    }

    @Test
    fun `진행 중 중복 탭은 무시된다`() = runTest {
        // MSAL 은 대화형 요청이 겹치면 예외를 던진다.
        val authRepo = FakeAuthRepository(authenticateResult = Result.success("user-1"))
        val vm = AuthViewModel(authRepo, FakeUserRepository())
        advanceUntilIdle()

        vm.authenticate(activity)
        vm.authenticate(activity) // 아직 첫 호출이 진행 중
        advanceUntilIdle()

        assertEquals(1, authRepo.authenticateCallCount)
    }

    @Test
    fun `dismissError 는 Failed 일 때만 Idle 로 되돌린다`() = runTest {
        val vm = createViewModel(
            authenticateResult = Result.failure(AppErrorException(AppError.Auth("실패"))),
        )
        advanceUntilIdle()

        vm.authenticate(activity)
        advanceUntilIdle()
        assertTrue(vm.uiState.value is AuthGateUiState.Failed)

        vm.dismissError()
        assertEquals(AuthGateUiState.Idle, vm.uiState.value)
    }

    // ---- 세션 종료 ----

    @Test
    fun `logout 시 Unauthenticated`() = runTest {
        val vm = createViewModel(restoreSessionUserId = "user-1")
        advanceUntilIdle()

        vm.logout()
        advanceUntilIdle()

        assertEquals(SessionState.Unauthenticated, vm.sessionState.value)
    }

    @Test
    fun `onSessionEnded 는 재로그아웃 없이 게이트로 되돌린다`() = runTest {
        // 계정 삭제 경로는 이미 로그아웃을 마쳤다 — 다시 부르면 없는 계정에 signOut 을 건다.
        val authRepo = FakeAuthRepository(restoreSessionUserId = "user-1")
        val vm = AuthViewModel(authRepo, FakeUserRepository())
        advanceUntilIdle()

        vm.onSessionEnded()

        assertEquals(SessionState.Unauthenticated, vm.sessionState.value)
        assertEquals(0, authRepo.signOutCallCount)
    }

    // ---- Helpers ----

    private fun sampleProfile() = UserProfile(
        userId = "user-1",
        heightCm = 170f,
        weightKg = 65f,
        bodyFatPercent = 20f,
        muscleMassKg = 30f,
    )

    private fun createViewModel(
        restoreSessionUserId: String? = null,
        profileResult: Result<UserProfile?> = Result.success(null),
        authenticateResult: Result<String> = Result.failure(IllegalStateException("not stubbed")),
    ): AuthViewModel = AuthViewModel(
        FakeAuthRepository(restoreSessionUserId, authenticateResult),
        FakeUserRepository(profileResult),
    )

    private class FakeAuthRepository(
        private val restoreSessionUserId: String? = null,
        private val authenticateResult: Result<String> = Result.failure(IllegalStateException("not stubbed")),
    ) : AuthRepository {
        var authenticateCallCount = 0
            private set
        var signOutCallCount = 0
            private set

        override suspend fun authenticate(activity: Activity): Result<String> {
            authenticateCallCount++
            return authenticateResult
        }

        override suspend fun signOut(): Result<Unit> {
            signOutCallCount++
            return Result.success(Unit)
        }

        override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)
        override suspend fun getCurrentUserId(): String? = restoreSessionUserId
        override suspend fun restoreSession(): String? = restoreSessionUserId
    }

    /**
     * `getProfile()` 은 3분기다 — success(프로필) / success(null, 404) / failure(조회 불가).
     * 세 번째를 표현할 수 있어야 A1 회귀(실패를 "프로필 없음"으로 오판)를 테스트로 고정할 수 있다.
     */
    private class FakeUserRepository(
        private val profileResult: Result<UserProfile?> = Result.success(null),
    ) : UserRepository {
        override suspend fun getProfile(): Result<UserProfile?> = profileResult
        override suspend fun saveProfile(profile: UserProfile): Result<Unit> = Result.success(Unit)
    }
}
