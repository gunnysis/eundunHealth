package com.gunnys.eundunhealth.domain.usecase

import com.gunnys.eundunhealth.domain.model.DayPlan
import com.gunnys.eundunhealth.domain.model.UserProfile
import com.gunnys.eundunhealth.domain.model.WeeklyPlan
import com.gunnys.eundunhealth.domain.repository.HealthRepository
import com.gunnys.eundunhealth.domain.repository.WorkoutRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SyncHealthDataUseCaseTest {

    private val weekStart = LocalDate.of(2026, 1, 5)

    private fun createPlan(): WeeklyPlan = WeeklyPlan(
        "plan1",
        "user1",
        weekStart,
        listOf(
            DayPlan(weekStart, emptyList(), isRestDay = false, isCompleted = false),
            DayPlan(weekStart.plusDays(1), emptyList(), isRestDay = false, isCompleted = false),
            DayPlan(weekStart.plusDays(6), emptyList(), isRestDay = true, isCompleted = false),
        ),
    )

    class FakeHealthRepo(
        private val hasPerms: Boolean,
        private val exerciseDates: List<LocalDate> = emptyList(),
    ) : HealthRepository {
        override suspend fun hasPermissions(): Boolean = hasPerms
        override suspend fun getExerciseDatesThisWeek(weekStart: LocalDate): Result<List<LocalDate>> = Result.success(exerciseDates)
    }

    class FakeWorkoutRepo : WorkoutRepository {
        override suspend fun getCurrentWeekPlan(): Result<WeeklyPlan?> = Result.success(null)
        override suspend fun createWeeklyPlan(profile: UserProfile): Result<WeeklyPlan> = Result.failure(NotImplementedError())
        override suspend fun savePlanToServer(plan: WeeklyPlan): Result<Unit> = Result.success(Unit)
        override suspend fun updateDayCompletion(planId: String, date: LocalDate, completed: Boolean): Result<Unit> = Result.success(Unit)
        override suspend fun getHistory(page: Int, size: Int): Result<Pair<List<WeeklyPlan>, Int>> = Result.success(emptyList<WeeklyPlan>() to 0)
        override suspend fun getStatistics(weeks: Int): Result<com.gunnys.eundunhealth.domain.model.Statistics> = Result.success(com.gunnys.eundunhealth.domain.model.Statistics(emptyList(), 0, 0))
    }

    @Test
    fun `syncs health data and marks completed days`() = runTest {
        val healthRepo = FakeHealthRepo(hasPerms = true, exerciseDates = listOf(weekStart))
        val useCase = SyncHealthDataUseCase(healthRepo, FakeWorkoutRepo())

        val result = useCase(createPlan())
        assertTrue(result.isSuccess)
        val plan = result.getOrThrow()
        assertTrue(plan.days[0].isCompleted)
        assertFalse(plan.days[1].isCompleted)
        assertFalse(plan.days[2].isCompleted)
    }

    @Test
    fun `no permissions returns plan unchanged`() = runTest {
        val healthRepo = FakeHealthRepo(hasPerms = false)
        val useCase = SyncHealthDataUseCase(healthRepo, FakeWorkoutRepo())

        val result = useCase(createPlan())
        assertTrue(result.isSuccess)
        assertFalse(result.getOrThrow().days[0].isCompleted)
    }
}
