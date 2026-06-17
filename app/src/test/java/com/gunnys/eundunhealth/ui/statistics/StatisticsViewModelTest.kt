package com.gunnys.eundunhealth.ui.statistics

import com.gunnys.eundunhealth.domain.model.Statistics
import com.gunnys.eundunhealth.domain.model.WeeklyRate
import com.gunnys.eundunhealth.domain.repository.WorkoutRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var workoutRepo: WorkoutRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        workoutRepo = mockk()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `weeklyRates 가 비면 Empty 상태`() = runTest {
        coEvery { workoutRepo.getStatistics(12) } returns Result.success(Statistics(emptyList(), 0, 0))
        val vm = StatisticsViewModel(workoutRepo)
        advanceUntilIdle()

        assertTrue(vm.uiState.value is StatisticsUiState.Empty)
    }

    @Test
    fun `weeklyRates 가 있으면 Loaded 상태`() = runTest {
        val stats = Statistics(listOf(WeeklyRate(LocalDate.of(2026, 6, 8), 0.5f)), currentStreak = 1, longestStreak = 2)
        coEvery { workoutRepo.getStatistics(12) } returns Result.success(stats)
        val vm = StatisticsViewModel(workoutRepo)
        advanceUntilIdle()

        assertTrue(vm.uiState.value is StatisticsUiState.Loaded)
    }

    @Test
    fun `로드 실패 시 Error 상태`() = runTest {
        coEvery { workoutRepo.getStatistics(12) } returns Result.failure(RuntimeException("x"))
        val vm = StatisticsViewModel(workoutRepo)
        advanceUntilIdle()

        assertTrue(vm.uiState.value is StatisticsUiState.Error)
    }
}
