package com.gunnys.eundunhealth.ui.auth

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.gunnys.eundunhealth.api.generated.api.AppCompatibilityApi
import com.gunnys.eundunhealth.api.generated.model.AppCompatibilityResponse
import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.model.AppErrorException
import com.gunnys.eundunhealth.domain.model.UserProfile
import com.gunnys.eundunhealth.domain.model.WorkoutPlanMode
import com.gunnys.eundunhealth.domain.repository.AuthCancelledException
import com.gunnys.eundunhealth.domain.repository.AuthRepository
import com.gunnys.eundunhealth.domain.repository.UserRepository
import com.gunnys.eundunhealth.ui.update.InAppUpdateChecker
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
import retrofit2.Response
import java.net.UnknownHostException

/**
 * AuthViewModel 테스트 — 세션 복원 + 인증 게이트 상태 전이 + 강제 업데이트 이중 가드(콜드 스타트 & ON_START 포그라운드 복귀).
 *
 * 브라우저 위임 전환으로 Login/Signup/ForgotPassword VM 3종이 사라져 그 테스트들도 함께
 * 폐기됐다. 대신 여기서 게이트의 상태 전이와 강제 업데이트 감지를 검증한다.
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
            profileResult = Result.success(sampleProfile()),
        ).let {
            createViewModel(
                restoreSessionUserId = "user-1",
                profileResult = Result.failure(UnknownHostException("offline")),
            )
        }
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

    // ---- 강제 업데이트 이중 가드 (콜드 스타트 + ON_START 포그라운드 복귀) ----

    @Test
    fun `콜드 스타트 시 백엔드 updateRequired 가 true 이면 UpdateRequired 로 전이한다`() = runTest {
        val compatApi = FakeAppCompatibilityApi(updateRequired = true, minimumVersionCode = 44)
        val vm = createViewModel(
            restoreSessionUserId = "user-1",
            appCompatibilityApi = compatApi,
        )
        advanceUntilIdle()

        assertEquals(
            SessionState.UpdateRequired(playPackageUri = DEFAULT_PLAY_PACKAGE_URI),
            vm.sessionState.value,
        )
    }

    @Test
    fun `콜드 스타트 시 백엔드가 false 여도 Play InAppUpdateChecker 가 true 이면 UpdateRequired 로 전이한다`() = runTest {
        val compatApi = FakeAppCompatibilityApi(updateRequired = false, minimumVersionCode = 43)
        val updateChecker = FakeInAppUpdateChecker(immediateAvailable = true)
        val vm = createViewModel(
            restoreSessionUserId = "user-1",
            appCompatibilityApi = compatApi,
            inAppUpdateChecker = updateChecker,
        )
        advanceUntilIdle()

        assertEquals(
            SessionState.UpdateRequired(playPackageUri = DEFAULT_PLAY_PACKAGE_URI),
            vm.sessionState.value,
        )
    }

    @Test
    fun `Authenticated 상태에서 포그라운드 복귀(onAppForegrounded) 시 쿨다운 경과 후 새 버전이 감지되면 즉시 UpdateRequired 로 전환한다`() = runTest {
        val compatApi = FakeAppCompatibilityApi(updateRequired = false, minimumVersionCode = 43)
        val updateChecker = FakeInAppUpdateChecker(immediateAvailable = false)
        var nowMs = 1_000L
        val vm = createViewModel(
            restoreSessionUserId = "user-1",
            profileResult = Result.success(sampleProfile()),
            appCompatibilityApi = compatApi,
            inAppUpdateChecker = updateChecker,
        ).also { it.clockMs = { nowMs } }
        advanceUntilIdle()

        assertTrue(vm.sessionState.value is SessionState.Authenticated)
        assertEquals(1, compatApi.callCount)

        // 스토어에 새 버전 출시 & 30초 쿨다운 경과 후 앱 재실행(ON_START)
        updateChecker.immediateAvailable = true
        nowMs += AuthViewModel.FOREGROUND_CHECK_COOLDOWN_MS + 1L
        vm.onAppForegrounded()
        advanceUntilIdle()

        assertEquals(
            SessionState.UpdateRequired(playPackageUri = DEFAULT_PLAY_PACKAGE_URI),
            vm.sessionState.value,
        )
        assertEquals(2, compatApi.callCount)
    }

    @Test
    fun `포그라운드 복귀가 30초 쿨다운 이내에 연속 발생하면 중복 버전 체크를 생략한다`() = runTest {
        val compatApi = FakeAppCompatibilityApi(updateRequired = false, minimumVersionCode = 43)
        var nowMs = 5_000L
        val vm = createViewModel(
            restoreSessionUserId = "user-1",
            profileResult = Result.success(sampleProfile()),
            appCompatibilityApi = compatApi,
        ).also { it.clockMs = { nowMs } }
        advanceUntilIdle()

        assertEquals(1, compatApi.callCount)

        // 10초 뒤 포그라운드 복귀 (30초 미만) → 호출 생략
        nowMs += 10_000L
        vm.onAppForegrounded()
        advanceUntilIdle()

        assertEquals(1, compatApi.callCount)
    }

    @Test
    fun `콜드 스타트 시 오프라인으로 백엔드 체크가 실패했으면 다음 포그라운드 복귀 시 쿨다운 없이 즉시 재시도한다`() = runTest {
        val compatApi = FakeAppCompatibilityApi(
            updateRequired = true,
            minimumVersionCode = 44,
            shouldThrowOffline = true,
        )
        var nowMs = 1_000L
        val vm = createViewModel(
            restoreSessionUserId = "user-1",
            profileResult = Result.success(sampleProfile()),
            appCompatibilityApi = compatApi,
        ).also { it.clockMs = { nowMs } }
        advanceUntilIdle()

        // 오프라인 시에는 기존 세션으로 진입 허용
        assertTrue(vm.sessionState.value is SessionState.Authenticated)
        assertEquals(1, compatApi.callCount)

        // 2초 뒤 네트워크 복구 후 포그라운드 재진입 → 쿨다운 없이 즉시 감지하여 UpdateRequired 전환
        compatApi.shouldThrowOffline = false
        nowMs += 2_000L
        vm.onAppForegrounded()
        advanceUntilIdle()

        assertEquals(
            SessionState.UpdateRequired(playPackageUri = DEFAULT_PLAY_PACKAGE_URI),
            vm.sessionState.value,
        )
        assertEquals(2, compatApi.callCount)
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
        gender = com.gunnys.eundunhealth.domain.model.Gender.MALE,
        bodyFatPercent = 20f,
        muscleMassKg = 30f,
    )

    private fun createViewModel(
        restoreSessionUserId: String? = null,
        profileResult: Result<UserProfile?> = Result.success(null),
        authenticateResult: Result<String> = Result.failure(IllegalStateException("not stubbed")),
        appCompatibilityApi: AppCompatibilityApi? = null,
        inAppUpdateChecker: InAppUpdateChecker? = null,
    ): AuthViewModel = AuthViewModel(
        FakeAuthRepository(restoreSessionUserId, authenticateResult),
        FakeUserRepository(profileResult),
        appCompatibilityApi = appCompatibilityApi,
        inAppUpdateChecker = inAppUpdateChecker,
    )

    private class FakeAppCompatibilityApi(
        var updateRequired: Boolean = false,
        var minimumVersionCode: Int = 43,
        var shouldThrowOffline: Boolean = false,
    ) : AppCompatibilityApi {
        var callCount = 0
            private set

        override suspend fun getAppCompatibility(
            platform: AppCompatibilityApi.PlatformGetAppCompatibility,
            versionCode: Int,
        ): Response<AppCompatibilityResponse> {
            callCount++
            if (shouldThrowOffline) {
                throw UnknownHostException("offline")
            }
            return Response.success(
                AppCompatibilityResponse(
                    platform = "android",
                    minimumSupportedVersionCode = minimumVersionCode,
                    updateRequired = updateRequired,
                    playPackageUri = DEFAULT_PLAY_PACKAGE_URI,
                ),
            )
        }
    }

    private class FakeInAppUpdateChecker(
        var immediateAvailable: Boolean = false,
    ) : InAppUpdateChecker {
        override suspend fun isImmediateUpdateAvailable(): Boolean = immediateAvailable
        override suspend fun launchImmediateUpdateIfAvailable(
            launcher: ActivityResultLauncher<IntentSenderRequest>,
        ): Boolean = immediateAvailable
    }

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
        override suspend fun updateDefaultWorkoutPlanMode(mode: WorkoutPlanMode?): Result<UserProfile> = error("unused in AuthViewModelTest")
    }
}
