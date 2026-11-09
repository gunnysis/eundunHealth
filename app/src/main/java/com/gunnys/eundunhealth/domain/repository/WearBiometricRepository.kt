package com.gunnys.eundunhealth.domain.repository

import com.gunnys.eundunhealth.wear.contract.BiometricDailySummaryRecord
import kotlinx.coroutines.flow.Flow

interface WearBiometricRepository {
    fun observeRecentSummaries(limitDays: Int = 14): Flow<List<BiometricDailySummaryRecord>>
    suspend fun ingestFromWatchEnvelope(rawGzipBytes: ByteArray): Result<Int>
    suspend fun requestWatchSyncNow(): Result<String>
    suspend fun syncPendingSummaries(maxBatchSize: Int = 14): SyncBatchOutcome
    suspend fun refreshRemoteIfEmpty(days: Int = 14): Result<Int>
    suspend fun deleteAllSummaries(): Result<Unit>
}
