package com.gunnys.eundunhealth.ui.navigation

import com.gunnys.eundunhealth.domain.model.CurrentWeekPlanRead
import com.gunnys.eundunhealth.domain.model.DayPlan
import com.gunnys.eundunhealth.domain.model.Gender
import com.gunnys.eundunhealth.domain.model.Goal
import com.gunnys.eundunhealth.domain.model.GoalType
import com.gunnys.eundunhealth.domain.model.Statistics
import com.gunnys.eundunhealth.domain.model.UserProfile
import com.gunnys.eundunhealth.domain.model.WeeklyPlan
import com.gunnys.eundunhealth.domain.model.WeeklyRate
import com.gunnys.eundunhealth.domain.model.WorkoutPlanMode
import com.gunnys.eundunhealth.domain.repository.GoalRepository
import com.gunnys.eundunhealth.domain.repository.UserRepository
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressHubViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var workoutRepo: WorkoutRepository
    private lateinit var goalRepo: GoalRepository
    private lateinit var userRepo: UserRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        workoutRepo = mockk()
        goalRepo = mockk()
        userRepo = mockk()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `3개 요약 타일 데이터를 병렬로 로드하여 포맷팅한다`() = runTest {
        val monday = LocalDate.of(2026, 10, 26)
        val plan = WeeklyPlan(
            id = "p1",
            userId = "u1",
            weekStart = monday,
            days = listOf(
                DayPlan(monday, emptyList(), isRestDay = false, isCompleted = true),
                DayPlan(monday.plusDays(1), emptyList(), isRestDay = false, isCompleted = false),
                DayPlan(monday.plusDays(2), emptyList(), isRestDay = true, isCompleted = false),
            ),
            mode = WorkoutPlanMode.HOME,
        )
        coEvery { workoutRepo.loadCurrentWeekPlan() } returns CurrentWeekPlanRead.Found(plan)
        coEvery { workoutRepo.getStatistics(4) } returns Result.success(
            Statistics(
                weeklyRates = listOf(
                    WeeklyRate(monday.minusWeeks(1), 0.6f),
                    WeeklyRate(monday, 0.8f),
                ),
                currentStreak = 2,
                longestStreak = 3,
            ),
        )
        coEvery { goalRepo.getGoals() } returns Result.success(
            listOf(Goal(GoalType.WEIGHT, 68.0f, null)),
        )
        coEvery { userRepo.getProfile() } returns Result.success(
            UserProfile("u1", 174f, 70.5f, Gender.MALE, null, null),
        )

        val vm = ProgressHubViewModel(workoutRepo, goalRepo, userRepo)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals("1/2일", state.thisWeekValue)
        assertEquals("70%", state.recentFourWeeksValue)
        assertEquals("-2.5kg", state.goalDeltaValue)
    }

    @Test
    fun `일부 API 가 실패해도 해당 타일만 대시(—)로 폴백하고 나머지 타일은 정상 표시한다`() = runTest {
        coEvery { workoutRepo.loadCurrentWeekPlan() } returns CurrentWeekPlanRead.Failed(IOException("offline"))
        coEvery { workoutRepo.getStatistics(4) } returns Result.failure(IOException("offline"))
        coEvery { goalRepo.getGoals() } returns Result.success(
            listOf(Goal(GoalType.WEIGHT, 65.0f, null)),
        )
        coEvery { userRepo.getProfile() } returns Result.failure(IOException("offline"))

        val vm = ProgressHubViewModel(workoutRepo, goalRepo, userRepo)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals("—", state.thisWeekValue)
        assertEquals("—", state.recentFourWeeksValue)
        assertEquals("65.0kg", state.goalDeltaValue)
    }
}
