package com.gunnys.eundunhealth.ui.onboarding

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
class OnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var userRepo: UserRepository
    private lateinit var authRepo: AuthRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        userRepo = mockk()
        authRepo = mockk()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `saveProfile 는 userId 가 null 이면 저장하지 않고 로그인 안내를 낸다`() = runTest {
        coEvery { authRepo.getCurrentUserId() } returns null
        val vm = OnboardingViewModel(userRepo, authRepo)
        val effects = mutableListOf<OnboardingSideEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.sideEffect.collect { effects.add(it) } }

        vm.saveProfile(175f, 70f, 18f, 33f)
        advanceUntilIdle()

        assertTrue(effects.any { it is OnboardingSideEffect.ShowSnackbar })
        coVerify(exactly = 0) { userRepo.saveProfile(any()) }
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `saveProfile 성공 시 NavigateToHome 을 낸다`() = runTest {
        coEvery { authRepo.getCurrentUserId() } returns "user-1"
        coEvery { userRepo.saveProfile(any()) } returns Result.success(Unit)
        val vm = OnboardingViewModel(userRepo, authRepo)
        val effects = mutableListOf<OnboardingSideEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.sideEffect.collect { effects.add(it) } }

        vm.saveProfile(175f, 70f, 18f, 33f)
        advanceUntilIdle()

        assertTrue(effects.any { it is OnboardingSideEffect.NavigateToHome })
        assertFalse(vm.uiState.value.isLoading)
    }
}
