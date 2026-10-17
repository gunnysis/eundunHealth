package com.gunnys.eundunhealth.ui.profile

import com.gunnys.eundunhealth.data.preferences.ThemeMode
import com.gunnys.eundunhealth.data.preferences.ThemePreferences
import com.gunnys.eundunhealth.domain.repository.HealthRepository
import io.mockk.coEvery
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileHubViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var healthRepository: HealthRepository
    private lateinit var themePreferences: ThemePreferences

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        healthRepository = mockk()
        themePreferences = mockk()
        every { themePreferences.themeMode } returns flowOf(ThemeMode.SYSTEM)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `unavailable Health Connect never reports granted permissions`() = runTest {
        coEvery { healthRepository.isAvailable() } returns false

        val viewModel = ProfileHubViewModel(healthRepository, themePreferences)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isHealthConnectAvailable)
        assertFalse(viewModel.uiState.value.hasHealthPermission)
        assertFalse(viewModel.uiState.value.isHealthConnectLoading)
    }

    @Test
    fun `refresh reads the latest Health Connect permission state`() = runTest {
        coEvery { healthRepository.isAvailable() } returns true
        coEvery { healthRepository.hasPermissions() } returnsMany listOf(false, true)

        val viewModel = ProfileHubViewModel(healthRepository, themePreferences)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.hasHealthPermission)

        viewModel.refreshHealthConnectStatus()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasHealthPermission)
    }
}
