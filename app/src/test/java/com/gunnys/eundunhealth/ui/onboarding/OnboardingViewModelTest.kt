package com.gunnys.eundunhealth.ui.onboarding

import com.gunnys.eundunhealth.domain.model.Gender
import com.gunnys.eundunhealth.domain.model.WorkoutPlanMode
import com.gunnys.eundunhealth.domain.repository.AuthRepository
import com.gunnys.eundunhealth.domain.repository.UserRepository
import com.gunnys.eundunhealth.domain.usecase.CreateWeeklyPlanUseCase
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var userRepo: UserRepository
    private lateinit var authRepo: AuthRepository
    private lateinit var createWeeklyPlan: CreateWeeklyPlanUseCase

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        userRepo = mockk()
        authRepo = mockk()
        createWeeklyPlan = mockk()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `saveProfile 는 userId 가 null 이면 저장하지 않고 error 상태를 낸다`() = runTest {
        coEvery { authRepo.getCurrentUserId() } returns null
        val vm = OnboardingViewModel(userRepo, authRepo, createWeeklyPlan)

        vm.saveProfile(175f, 70f, Gender.MALE, 18f, 33f)
        advanceUntilIdle()

        assertNotNull("로그인 필요 에러 상태", vm.uiState.value.error)
        coVerify(exactly = 0) { userRepo.saveProfile(any()) }
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `saveProfile 성공 시 다중 휴식 요일 정규화 후 NavigateToHome sideEffect 를 낸다`() = runTest {
        coEvery { authRepo.getCurrentUserId() } returns "user-1"
        coEvery { userRepo.saveProfile(any()) } returns Result.success(Unit)
        coEvery { createWeeklyPlan(any()) } returns Result.failure(IOException("transient"))
        val vm = OnboardingViewModel(userRepo, authRepo, createWeeklyPlan)
        val effects = mutableListOf<OnboardingSideEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.sideEffect.collect { effects.add(it) } }

        vm.saveProfile(
            heightCm = 175f,
            weightKg = 70f,
            gender = Gender.MALE,
            bodyFatPct = 18f,
            muscleMassKg = 33f,
            restDays = listOf(6, 2, 4, 7, 2),
        )
        advanceUntilIdle()

        assertTrue(effects.any { it is OnboardingSideEffect.NavigateToHome })
        assertFalse(vm.uiState.value.isLoading)
        coVerify(exactly = 1) {
            userRepo.saveProfile(
                match {
                    it.restDays == listOf(2, 4, 6, 7)
                },
            )
        }
    }

    @Test
    fun `체성분(bodyFatPct, muscleMassKg)이 null 이어도 가짜 기본값 없이 null 그대로 저장한다`() = runTest {
        coEvery { authRepo.getCurrentUserId() } returns "user-1"
        coEvery { userRepo.saveProfile(any()) } returns Result.success(Unit)
        coEvery { createWeeklyPlan(WorkoutPlanMode.GYM) } returns Result.failure(IOException("skip"))

        val vm = OnboardingViewModel(userRepo, authRepo, createWeeklyPlan)
        vm.saveProfile(
            heightCm = 174f,
            weightKg = 72f,
            gender = Gender.MALE,
            bodyFatPct = null,
            muscleMassKg = null,
            restDays = listOf(7),
            selectedMode = WorkoutPlanMode.GYM,
            saveAsDefaultMode = true,
        )
        advanceUntilIdle()

        coVerify(exactly = 1) {
            userRepo.saveProfile(
                match {
                    it.bodyFatPercent == null &&
                        it.muscleMassKg == null &&
                        it.defaultWorkoutPlanMode == WorkoutPlanMode.GYM
                },
            )
        }
        coVerify(exactly = 1) { createWeeklyPlan(WorkoutPlanMode.GYM) }
    }
}
