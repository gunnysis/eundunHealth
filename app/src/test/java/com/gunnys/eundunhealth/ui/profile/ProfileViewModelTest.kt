package com.gunnys.eundunhealth.ui.profile

import com.gunnys.eundunhealth.domain.model.UserProfile
import com.gunnys.eundunhealth.domain.repository.AuthRepository
import com.gunnys.eundunhealth.domain.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var userRepo: UserRepository
    private lateinit var authRepo: AuthRepository
    private val profile = UserProfile("user-1", 175f, 70f, 18f, 33f, restDay = 7)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        userRepo = mockk()
        authRepo = mockk()
        coEvery { userRepo.getProfile() } returns Result.success(profile) // init load → Loaded
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun createViewModel() = ProfileViewModel(userRepo, authRepo)

    @Test
    fun `saveProfile 는 userId 가 null 이면 저장하지 않고 로그인 안내를 낸다`() = runTest {
        coEvery { authRepo.getCurrentUserId() } returns null
        val vm = createViewModel()
        val effects = mutableListOf<ProfileSideEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.sideEffect.collect { effects.add(it) } }
        advanceUntilIdle()

        vm.saveProfile(175f, 70f, 18f, 33f)
        advanceUntilIdle()

        assertTrue(effects.any { it is ProfileSideEffect.ShowSnackbar && it.message == "로그인이 필요합니다" })
        coVerify(exactly = 0) { userRepo.saveProfile(any()) }
        assertFalse((vm.uiState.value as ProfileUiState.Loaded).isSaving)
    }

    @Test
    fun `deleteAccount 실패 시 NavigateToLogin 없이 isDeleting 을 해제한다`() = runTest {
        coEvery { authRepo.deleteAccount() } returns Result.failure(RuntimeException("server"))
        val vm = createViewModel()
        val effects = mutableListOf<ProfileSideEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.sideEffect.collect { effects.add(it) } }
        advanceUntilIdle()

        vm.deleteAccount()
        advanceUntilIdle()

        // 실패한 삭제로 로그인 화면 이동하면 사용자에게 "삭제됨"으로 오인된다(회귀가드).
        assertFalse(effects.any { it is ProfileSideEffect.NavigateToLogin })
        assertTrue(effects.any { it is ProfileSideEffect.ShowSnackbar })
        assertFalse((vm.uiState.value as ProfileUiState.Loaded).isDeleting)
    }

    @Test
    fun `deleteAccount 성공 시 NavigateToLogin 을 낸다`() = runTest {
        coEvery { authRepo.deleteAccount() } returns Result.success(Unit)
        val vm = createViewModel()
        val effects = mutableListOf<ProfileSideEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.sideEffect.collect { effects.add(it) } }
        advanceUntilIdle()

        vm.deleteAccount()
        advanceUntilIdle()

        assertTrue(effects.any { it is ProfileSideEffect.NavigateToLogin })
    }
}
