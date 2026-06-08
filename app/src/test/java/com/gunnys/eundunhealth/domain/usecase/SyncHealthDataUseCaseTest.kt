package com.gunnys.eundunhealth.domain.usecase

import com.gunnys.eundunhealth.domain.model.BodyComposition
import com.gunnys.eundunhealth.domain.model.DailyActivity
import com.gunnys.eundunhealth.domain.model.DayPlan
import com.gunnys.eundunhealth.domain.model.WeeklyPlan
import com.gunnys.eundunhealth.domain.repository.HealthRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
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

    /**
     * 테스트 더블 — [exerciseResult] 로 Health Connect read 의 성공/실패까지 주입한다.
     */
    class FakeHealthRepo(
        private val available: Boolean = true,
        private val hasPerms: Boolean = true,
        private val exerciseResult: Result<List<LocalDate>> = Result.success(emptyList()),
    ) : HealthRepository {
        override suspend fun isAvailable(): Boolean = available
        override suspend fun hasPermissions(): Boolean = hasPerms
        override suspend fun getExerciseDatesThisWeek(weekStart: LocalDate): Result<List<LocalDate>> = exerciseResult
        override suspend fun hasBodyCompositionPermissions(): Boolean = false
        override suspend fun getLatestBodyComposition(): Result<BodyComposition> = Result.success(BodyComposition(null, null, null))
        override suspend fun hasDailyActivityPermissions(): Boolean = false
        override suspend fun getTodayActivity(): Result<DailyActivity> = Result.success(DailyActivity(null, null, null))
    }

    @Test
    fun `syncs health data and marks completed days`() = runTest {
        val healthRepo = FakeHealthRepo(exerciseResult = Result.success(listOf(weekStart)))
        val useCase = SyncHealthDataUseCase(healthRepo)

        val result = useCase(createPlan())

        assertTrue(result.isSuccess)
        val sync = result.getOrThrow()
        assertTrue(sync.plan.days[0].isCompleted)
        assertFalse(sync.plan.days[1].isCompleted)
        assertFalse(sync.plan.days[2].isCompleted)
        assertTrue(sync.isAvailable)
        assertTrue(sync.hasPermission)
        assertEquals(listOf(weekStart), sync.newlyCompletedDates)
    }

    @Test
    fun `no permissions returns plan unchanged`() = runTest {
        val healthRepo = FakeHealthRepo(hasPerms = false, exerciseResult = Result.success(listOf(weekStart)))
        val useCase = SyncHealthDataUseCase(healthRepo)

        val result = useCase(createPlan())

        assertTrue(result.isSuccess)
        val sync = result.getOrThrow()
        assertFalse(sync.plan.days[0].isCompleted)
        assertFalse(sync.hasPermission)
        assertTrue(sync.newlyCompletedDates.isEmpty())
    }

    @Test
    fun `health connect unavailable skips sync and reports availability`() = runTest {
        val healthRepo = FakeHealthRepo(available = false, exerciseResult = Result.success(listOf(weekStart)))
        val useCase = SyncHealthDataUseCase(healthRepo)

        val result = useCase(createPlan())

        assertTrue(result.isSuccess)
        val sync = result.getOrThrow()
        assertFalse(sync.isAvailable)
        assertFalse(sync.hasPermission)
        assertFalse(sync.plan.days[0].isCompleted)
        assertTrue(sync.newlyCompletedDates.isEmpty())
    }

    @Test
    fun `already completed day is not reported as newly completed`() = runTest {
        val plan = WeeklyPlan(
            "plan1",
            "user1",
            weekStart,
            listOf(DayPlan(weekStart, emptyList(), isRestDay = false, isCompleted = true)),
        )
        val healthRepo = FakeHealthRepo(exerciseResult = Result.success(listOf(weekStart)))
        val useCase = SyncHealthDataUseCase(healthRepo)

        val sync = useCase(plan).getOrThrow()

        assertTrue(sync.plan.days[0].isCompleted)
        assertTrue(sync.newlyCompletedDates.isEmpty())
    }

    @Test
    fun `rest day with exercise record is not marked completed`() = runTest {
        val plan = WeeklyPlan(
            "plan1",
            "user1",
            weekStart,
            listOf(DayPlan(weekStart, emptyList(), isRestDay = true, isCompleted = false)),
        )
        val healthRepo = FakeHealthRepo(exerciseResult = Result.success(listOf(weekStart)))
        val useCase = SyncHealthDataUseCase(healthRepo)

        val sync = useCase(plan).getOrThrow()

        assertFalse(sync.plan.days[0].isCompleted)
        assertTrue(sync.newlyCompletedDates.isEmpty())
    }

    @Test
    fun `health read failure falls back to empty without failing the sync`() = runTest {
        val healthRepo = FakeHealthRepo(exerciseResult = Result.failure(IOException("HC read failed")))
        val useCase = SyncHealthDataUseCase(healthRepo)

        val result = useCase(createPlan())

        assertTrue(result.isSuccess)
        val sync = result.getOrThrow()
        assertFalse(sync.plan.days[0].isCompleted)
        assertTrue(sync.newlyCompletedDates.isEmpty())
        assertTrue(sync.hasPermission)
    }
}
