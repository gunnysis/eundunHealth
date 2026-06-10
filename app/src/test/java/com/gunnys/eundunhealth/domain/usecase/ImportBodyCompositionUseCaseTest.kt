package com.gunnys.eundunhealth.domain.usecase

import com.gunnys.eundunhealth.domain.model.BodyComposition
import com.gunnys.eundunhealth.domain.model.DailyActivity
import com.gunnys.eundunhealth.domain.repository.HealthRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

class ImportBodyCompositionUseCaseTest {

    class FakeHealthRepo(
        private val available: Boolean = true,
        private val hasBodyPerms: Boolean = true,
        private val latest: Result<BodyComposition> = Result.success(BodyComposition(70f, 18f, null)),
    ) : HealthRepository {
        override suspend fun isAvailable(): Boolean = available
        override suspend fun hasPermissions(): Boolean = true
        override suspend fun getExerciseDatesThisWeek(weekStart: LocalDate): Result<List<LocalDate>> = Result.success(emptyList())
        override suspend fun hasBodyCompositionPermissions(): Boolean = hasBodyPerms
        override suspend fun getLatestBodyComposition(): Result<BodyComposition> = latest
        override suspend fun hasDailyActivityPermissions(): Boolean = false
        override suspend fun getTodayActivity(): Result<DailyActivity> = Result.success(DailyActivity(null, null, null))
    }

    @Test
    fun `returns latest body composition when available and permitted`() = runTest {
        val useCase = ImportBodyCompositionUseCase(FakeHealthRepo())
        val result = useCase().getOrThrow()
        assertEquals(70f, result?.weightKg)
        assertEquals(18f, result?.bodyFatPercent)
    }

    @Test
    fun `returns null when no body composition permission`() = runTest {
        val useCase = ImportBodyCompositionUseCase(FakeHealthRepo(hasBodyPerms = false))
        assertNull(useCase().getOrThrow())
    }

    @Test
    fun `returns null when health connect unavailable`() = runTest {
        val useCase = ImportBodyCompositionUseCase(FakeHealthRepo(available = false))
        assertNull(useCase().getOrThrow())
    }

    @Test
    fun `read failure propagates as Result_failure distinct from no-records`() = runTest {
        val useCase = ImportBodyCompositionUseCase(
            FakeHealthRepo(latest = Result.failure(IOException("HC read failed"))),
        )
        val result = useCase()
        assertTrue(result.isFailure)
    }

    @Test
    fun `no records returns object with null fields`() = runTest {
        val useCase = ImportBodyCompositionUseCase(
            FakeHealthRepo(latest = Result.success(BodyComposition(null, null, null))),
        )
        val result = useCase().getOrThrow()
        assertNull(result?.weightKg)
        assertNull(result?.bodyFatPercent)
    }
}
