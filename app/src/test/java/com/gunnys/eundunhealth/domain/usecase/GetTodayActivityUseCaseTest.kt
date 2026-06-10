package com.gunnys.eundunhealth.domain.usecase

import com.gunnys.eundunhealth.domain.model.DailyActivity
import com.gunnys.eundunhealth.domain.repository.HealthRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

class GetTodayActivityUseCaseTest {

    class FakeHealthRepo(
        private val available: Boolean = true,
        private val hasActivityPerms: Boolean = true,
        private val activity: Result<DailyActivity> = Result.success(DailyActivity(8000L, 320, 72L)),
    ) : HealthRepository {
        override suspend fun isAvailable(): Boolean = available
        override suspend fun hasPermissions(): Boolean = true
        override suspend fun getExerciseDatesThisWeek(weekStart: LocalDate): Result<List<LocalDate>> = Result.success(emptyList())
        override suspend fun hasDailyActivityPermissions(): Boolean = hasActivityPerms
        override suspend fun getTodayActivity(): Result<DailyActivity> = activity
    }

    @Test
    fun `returns activity with permission when available`() = runTest {
        val result = GetTodayActivityUseCase(FakeHealthRepo())().getOrThrow()
        assertTrue(result.hasPermission)
        assertEquals(8000L, result.activity?.steps)
        assertEquals(320, result.activity?.totalCaloriesKcal)
        assertEquals(72L, result.activity?.avgHeartRateBpm)
    }

    @Test
    fun `no permission returns null activity and hasPermission false`() = runTest {
        val result = GetTodayActivityUseCase(FakeHealthRepo(hasActivityPerms = false))().getOrThrow()
        assertFalse(result.hasPermission)
        assertNull(result.activity)
    }

    @Test
    fun `unavailable returns null activity`() = runTest {
        val result = GetTodayActivityUseCase(FakeHealthRepo(available = false))().getOrThrow()
        assertFalse(result.hasPermission)
        assertNull(result.activity)
    }

    @Test
    fun `read failure falls back to null activity but keeps permission true`() = runTest {
        val result = GetTodayActivityUseCase(
            FakeHealthRepo(activity = Result.failure(IOException("HC read failed"))),
        )().getOrThrow()
        assertTrue(result.hasPermission)
        assertNull(result.activity)
    }

    @Test
    fun `no data returns activity object with null fields`() = runTest {
        val result = GetTodayActivityUseCase(
            FakeHealthRepo(activity = Result.success(DailyActivity(null, null, null))),
        )().getOrThrow()
        assertTrue(result.hasPermission)
        assertFalse(result.activity?.hasAny ?: true)
    }
}
