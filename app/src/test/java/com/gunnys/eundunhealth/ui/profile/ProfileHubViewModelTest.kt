package com.gunnys.eundunhealth.ui.profile

import com.gunnys.eundunhealth.data.preferences.ThemeMode
import com.gunnys.eundunhealth.data.preferences.ThemePreferences
import com.gunnys.eundunhealth.domain.model.Gender
import com.gunnys.eundunhealth.domain.model.UserProfile
import com.gunnys.eundunhealth.domain.model.WorkoutPlanMode
import com.gunnys.eundunhealth.domain.repository.HealthRepository
import com.gunnys.eundunhealth.domain.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileHubViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var healthRepository: HealthRepository
    private lateinit var themePreferences: ThemePreferences
    private lateinit var userRepository: UserRepository

    private val profile = UserProfile(
        userId = "u1",
        heightCm = 172f,
        weightKg = 68f,
        gender = Gender.MALE,
        bodyFatPercent = 18f,
        muscleMassKg = 32f,
        restDays = listOf(7),
        defaultWorkoutPlanMode = WorkoutPlanMode.GYM,
    )

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        healthRepository = mockk()
        themePreferences = mockk()
        userRepository = mockk()
        every { themePreferences.themeMode } returns flowOf(ThemeMode.SYSTEM)
        coEvery { userRepository.getProfile() } returns Result.success(profile)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `unavailable Health Connect never reports granted permissions`() = runTest {
        coEvery { healthRepository.isAvailable() } returns false

        val viewModel = ProfileHubViewModel(healthRepository, themePreferences, userRepository)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isHealthConnectAvailable)
        assertFalse(viewModel.uiState.value.hasHealthPermission)
        assertFalse(viewModel.uiState.value.isHealthConnectLoading)
        assertEquals(WorkoutPlanMode.GYM, viewModel.uiState.value.defaultWorkoutPlanMode)
    }

    @Test
    fun `refresh reads the latest Health Connect permission state`() = runTest {
        coEvery { healthRepository.isAvailable() } returns true
        coEvery { healthRepository.hasPermissions() } returnsMany listOf(false, true)

        val viewModel = ProfileHubViewModel(healthRepository, themePreferences, userRepository)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.hasHealthPermission)

        viewModel.refreshHealthConnectStatus()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasHealthPermission)
    }

    @Test
    fun `updateDefaultWorkoutPlanMode 성공 시 전용 API 만 호출하고 상태를 갱신한다`() = runTest {
        coEvery { healthRepository.isAvailable() } returns false
        coEvery { userRepository.updateDefaultWorkoutPlanMode(WorkoutPlanMode.HOME) } returns
            Result.success(profile.copy(defaultWorkoutPlanMode = WorkoutPlanMode.HOME))

        val viewModel = ProfileHubViewModel(healthRepository, themePreferences, userRepository)
        advanceUntilIdle()

        viewModel.updateDefaultWorkoutPlanMode(WorkoutPlanMode.HOME)
        advanceUntilIdle()

        assertEquals(WorkoutPlanMode.HOME, viewModel.uiState.value.defaultWorkoutPlanMode)
        assertNull(viewModel.uiState.value.defaultPlanModeError)
        coVerify(exactly = 1) { userRepository.updateDefaultWorkoutPlanMode(WorkoutPlanMode.HOME) }
        coVerify(exactly = 0) { userRepository.saveProfile(any()) }
    }

    @Test
    fun `updateDefaultWorkoutPlanMode 실패 시 이전 값으로 복원하고 에러를 노출한다`() = runTest {
        coEvery { healthRepository.isAvailable() } returns false
        coEvery { userRepository.updateDefaultWorkoutPlanMode(WorkoutPlanMode.HOME) } returns
            Result.failure(IOException("network error"))

        val viewModel = ProfileHubViewModel(healthRepository, themePreferences, userRepository)
        advanceUntilIdle()

        viewModel.updateDefaultWorkoutPlanMode(WorkoutPlanMode.HOME)
        advanceUntilIdle()

        assertEquals(WorkoutPlanMode.GYM, viewModel.uiState.value.defaultWorkoutPlanMode)
        assertNotNull(viewModel.uiState.value.defaultPlanModeError)
    }
}
