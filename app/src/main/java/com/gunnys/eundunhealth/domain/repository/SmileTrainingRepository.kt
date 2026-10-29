package com.gunnys.eundunhealth.domain.repository

import com.gunnys.eundunhealth.domain.model.SmileSessionRecord
import com.gunnys.eundunhealth.domain.model.SmileSummaryStats
import kotlinx.coroutines.flow.Flow

interface SmileTrainingRepository {
    fun observeSessions(): Flow<List<SmileSessionRecord>>

    fun observeSummaryStats(): Flow<SmileSummaryStats>

    suspend fun saveCompletedSession(
        durationSec: Int,
        completedReps: Int,
        occurredAtEpochMs: Long = System.currentTimeMillis(),
    ): Result<SmileSessionRecord>

    suspend fun syncPendingSessions(maxBatchSize: Int = 10): SyncBatchOutcome

    suspend fun refreshRemoteHistoryIfEmpty(): Result<Int> = Result.success(0)

    suspend fun retryBlockedSessions(): Result<Int>

    suspend fun deleteAllSessions(): Result<Unit>
}

enum class SyncBatchOutcome {
    ALL_SYNCED,
    RETRY_NEEDED,
    AUTH_REQUIRED,
    SKIPPED_NO_USER,
}
