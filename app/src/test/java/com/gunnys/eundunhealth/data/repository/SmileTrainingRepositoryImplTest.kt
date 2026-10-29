package com.gunnys.eundunhealth.data.repository

import androidx.work.WorkManager
import com.gunnys.eundunhealth.api.generated.api.SmileTrainingApi
import com.gunnys.eundunhealth.api.generated.model.SmileTrainingSessionResponse
import com.gunnys.eundunhealth.api.generated.model.SmileTrainingSessionsPageResponse
import com.gunnys.eundunhealth.data.local.dao.SmileSessionDao
import com.gunnys.eundunhealth.data.local.entity.SmileSessionEntity
import com.gunnys.eundunhealth.domain.model.SmileSyncStatus
import com.gunnys.eundunhealth.domain.repository.AuthRepository
import com.gunnys.eundunhealth.domain.repository.SyncBatchOutcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class SmileTrainingRepositoryImplTest {

    private val kstZone = ZoneId.of("Asia/Seoul")

    @Test
    fun `computeSummaryStats calculates KST streaks and today completion accurately`() {
        val today = LocalDate.of(2026, 10, 26)
        val entities = listOf(
            entityAtKst(2026, 10, 26, 0, 15, durationSec = 40, status = SmileSyncStatus.SYNCED),
            entityAtKst(2026, 10, 25, 23, 50, durationSec = 50, status = SmileSyncStatus.PENDING),
            entityAtKst(2026, 10, 24, 12, 0, durationSec = 30, status = SmileSyncStatus.BLOCKED),
            // Gap on Oct 23, earlier 2-day streak on Oct 21-22
            entityAtKst(2026, 10, 22, 9, 0, durationSec = 20, status = SmileSyncStatus.SYNCED),
            entityAtKst(2026, 10, 21, 9, 0, durationSec = 25, status = SmileSyncStatus.SYNCED),
        )

        val stats = SmileTrainingRepositoryImpl.computeSummaryStats(entities, today)

        assertEquals(5, stats.totalSessions)
        assertEquals(165, stats.totalDurationSec)
        assertTrue(stats.isCompletedToday)
        assertEquals(3, stats.currentStreakDays)
        assertEquals(3, stats.longestStreakDays)
        assertEquals(1, stats.pendingSyncCount)
        assertEquals(1, stats.blockedSyncCount)
    }

    @Test
    fun `computeSummaryStats ignores future timestamps caused by clock skew`() {
        val today = LocalDate.of(2026, 10, 26)
        val entities = listOf(
            // Future timestamp (Oct 28) from clock skew + yesterday (Oct 25)
            entityAtKst(2026, 10, 28, 12, 0, durationSec = 20, status = SmileSyncStatus.SYNCED),
            entityAtKst(2026, 10, 25, 18, 0, durationSec = 30, status = SmileSyncStatus.SYNCED),
        )

        val stats = SmileTrainingRepositoryImpl.computeSummaryStats(entities, today)

        assertEquals(2, stats.totalSessions)
        assertEquals(1, stats.currentStreakDays)
        assertEquals(1, stats.longestStreakDays)
    }

    @Test
    fun `syncPendingSessions drains multiple batches in a single pass and deleteAllSessions cancels worker`() = runTest {
        val dao = InMemorySmileSessionDao()
        val api = mockk<SmileTrainingApi>()
        val authRepo = mockk<AuthRepository>()
        val workManager = mockk<WorkManager>(relaxed = true)

        coEvery { authRepo.getCurrentUserId() } returns "user-1"
        coEvery { api.recordSmileTrainingSession(any()) } answers {
            val req = firstArg<com.gunnys.eundunhealth.api.generated.model.SmileTrainingSessionRequest>()
            Response.success(
                201,
                SmileTrainingSessionResponse(
                    id = req.id.toString(),
                    durationSec = req.durationSec,
                    completedReps = req.completedReps,
                    occurredAt = req.occurredAt,
                    createdAt = req.occurredAt,
                ),
            )
        }
        coEvery { api.deleteAllSmileTrainingSessions() } returns Response.success(
            200,
            com.gunnys.eundunhealth.api.generated.model.SmileTrainingDeleteResponse(
                status = "deleted",
                deletedCount = 5,
            ),
        )

        val repo = SmileTrainingRepositoryImpl(dao, api, authRepo, workManager)
        repeat(5) {
            repo.saveCompletedSession(durationSec = 20, completedReps = 3).getOrThrow()
        }

        // Sync with maxBatchSize = 2 -> should loop through all 3 batches (2 + 2 + 1 = 5)
        val outcome = repo.syncPendingSessions(maxBatchSize = 2)
        assertEquals(SyncBatchOutcome.ALL_SYNCED, outcome)
        val synced = repo.observeSessions().first()
        assertTrue(synced.all { it.syncStatus == SmileSyncStatus.SYNCED })

        repo.deleteAllSessions().getOrThrow()
        coVerify { workManager.cancelUniqueWork(com.gunnys.eundunhealth.data.sync.SmileSyncWorker.UNIQUE_WORK_NAME) }
        assertTrue(repo.observeSessions().first().isEmpty())
    }

    @Test
    fun `syncPendingSessions classifies 201 as SYNCED, 422 as BLOCKED, and IOException as RETRY_NEEDED`() = runTest {
        val dao = InMemorySmileSessionDao()
        val api = mockk<SmileTrainingApi>()
        val authRepo = mockk<AuthRepository>()
        val workManager = mockk<WorkManager>(relaxed = true)

        coEvery { authRepo.getCurrentUserId() } returns "user-1"

        val repo = SmileTrainingRepositoryImpl(dao, api, authRepo, workManager)

        val saved1 = repo.saveCompletedSession(
            durationSec = 30,
            completedReps = 3,
        ).getOrThrow()

        val saved2 = repo.saveCompletedSession(
            durationSec = 45,
            completedReps = 3,
        ).getOrThrow()

        // First session succeeds (201), second session gets 422 Unprocessable Entity -> marked BLOCKED
        coEvery {
            api.recordSmileTrainingSession(match { it.id.toString() == saved1.id })
        } returns Response.success(
            201,
            SmileTrainingSessionResponse(
                id = saved1.id,
                durationSec = 30,
                completedReps = 3,
                occurredAt = "2026-10-26T00:00:00Z",
                createdAt = "2026-10-26T00:00:00Z",
            ),
        )

        coEvery {
            api.recordSmileTrainingSession(match { it.id.toString() == saved2.id })
        } returns Response.error(
            422,
            """{"detail":"invalid"}""".toResponseBody("application/json".toMediaType()),
        )

        val outcome = repo.syncPendingSessions()
        // Both were processed without transient network failure (one SYNCED, one BLOCKED)
        assertEquals(SyncBatchOutcome.ALL_SYNCED, outcome)

        val records = repo.observeSessions().first()
        val byId = records.associateBy { it.id }
        assertEquals(SmileSyncStatus.SYNCED, byId.getValue(saved1.id).syncStatus)
        assertEquals(SmileSyncStatus.BLOCKED, byId.getValue(saved2.id).syncStatus)

        // Now user triggers retryBlockedSessions -> resets BLOCKED to PENDING, then transient IOException -> RETRY_NEEDED
        val resetCount = repo.retryBlockedSessions().getOrThrow()
        assertEquals(1, resetCount)

        coEvery {
            api.recordSmileTrainingSession(match { it.id.toString() == saved2.id })
        } throws IOException("offline")

        val retryOutcome = repo.syncPendingSessions()
        assertEquals(SyncBatchOutcome.RETRY_NEEDED, retryOutcome)
    }

    @Test
    fun `refreshRemoteHistoryIfEmpty pulls remote sessions when local DB is empty and skips when populated`() = runTest {
        val dao = InMemorySmileSessionDao()
        val api = mockk<SmileTrainingApi>()
        val authRepo = mockk<AuthRepository>()
        val workManager = mockk<WorkManager>(relaxed = true)

        coEvery { authRepo.getCurrentUserId() } returns "user-1"
        coEvery { api.listSmileTrainingSessions(cursor = null, limit = 50) } returns Response.success(
            SmileTrainingSessionsPageResponse(
                items = listOf(
                    SmileTrainingSessionResponse(
                        id = "11111111-2222-3333-4444-555555555555",
                        durationSec = 55,
                        completedReps = 3,
                        occurredAt = "2026-10-25T10:00:00Z",
                        createdAt = "2026-10-25T10:00:01Z",
                    ),
                ),
                nextCursor = null,
            ),
        )

        val repo = SmileTrainingRepositoryImpl(dao, api, authRepo, workManager)

        val inserted = repo.refreshRemoteHistoryIfEmpty().getOrThrow()
        assertEquals(1, inserted)

        val records = repo.observeSessions().first()
        assertEquals(1, records.size)
        assertEquals("11111111-2222-3333-4444-555555555555", records.first().id)
        assertEquals(SmileSyncStatus.SYNCED, records.first().syncStatus)
        assertEquals(55, records.first().durationSec)
        assertEquals(3, records.first().completedReps)

        // Second call when local DB is already populated -> skips network call and returns 0
        val secondInserted = repo.refreshRemoteHistoryIfEmpty().getOrThrow()
        assertEquals(0, secondInserted)
        coVerify(exactly = 1) { api.listSmileTrainingSessions(cursor = null, limit = 50) }
    }

    private fun entityAtKst(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        durationSec: Int,
        status: SmileSyncStatus,
    ): SmileSessionEntity {
        val epochMs = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, kstZone)
            .toInstant()
            .toEpochMilli()
        return SmileSessionEntity(
            id = "id-$year-$month-$day-$hour-$minute",
            userId = "user-1",
            durationSec = durationSec,
            completedReps = 3,
            occurredAtUtc = epochMs,
            syncStatus = status.name,
        )
    }

    private class InMemorySmileSessionDao : SmileSessionDao {
        private val state = MutableStateFlow<List<SmileSessionEntity>>(emptyList())

        override suspend fun insertSession(entity: SmileSessionEntity): Long {
            if (state.value.any { it.id == entity.id }) return -1L
            state.value = state.value + entity
            return 1L
        }

        override suspend fun getById(id: String, userId: String): SmileSessionEntity? = state.value.firstOrNull { it.id == id && it.userId == userId }

        override fun observeSessionsByUser(userId: String): Flow<List<SmileSessionEntity>> = state

        override suspend fun getSessionsByUser(userId: String): List<SmileSessionEntity> = state.value.filter { it.userId == userId }.sortedByDescending { it.occurredAtUtc }

        override suspend fun getOldestPendingByUser(userId: String, limit: Int): List<SmileSessionEntity> = state.value
            .filter { it.userId == userId && it.syncStatus == SmileSyncStatus.PENDING.name }
            .sortedBy { it.occurredAtUtc }
            .take(limit)

        override suspend fun updateSyncStatus(
            id: String,
            userId: String,
            status: String,
            syncedAtUtc: Long?,
            errorCode: Int?,
        ) {
            state.value = state.value.map {
                if (it.id == id && it.userId == userId) {
                    it.copy(syncStatus = status, syncedAtUtc = syncedAtUtc, lastSyncErrorCode = errorCode)
                } else {
                    it
                }
            }
        }

        override suspend fun resetBlockedToPending(userId: String): Int {
            var count = 0
            state.value = state.value.map {
                if (it.userId == userId && it.syncStatus == SmileSyncStatus.BLOCKED.name) {
                    count++
                    it.copy(syncStatus = SmileSyncStatus.PENDING.name, lastSyncErrorCode = null)
                } else {
                    it
                }
            }
            return count
        }

        override suspend fun deleteAllByUser(userId: String) {
            state.value = state.value.filterNot { it.userId == userId }
        }

        override suspend fun clearAll() {
            state.value = emptyList()
        }
    }
}
