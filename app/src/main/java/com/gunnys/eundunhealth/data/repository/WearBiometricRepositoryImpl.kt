package com.gunnys.eundunhealth.data.repository

import android.content.Context
import androidx.work.WorkManager
import com.google.android.gms.wearable.Wearable
import com.gunnys.eundunhealth.api.generated.api.BiometricsApi
import com.gunnys.eundunhealth.api.generated.model.BiometricDailySummaryItem
import com.gunnys.eundunhealth.api.generated.model.BiometricSyncRequest
import com.gunnys.eundunhealth.data.local.dao.WearBiometricDao
import com.gunnys.eundunhealth.data.local.entity.WearBiometricSummaryEntity
import com.gunnys.eundunhealth.data.sync.SmileSyncWorker
import com.gunnys.eundunhealth.domain.model.SmileSyncStatus
import com.gunnys.eundunhealth.domain.model.runSuspendCatching
import com.gunnys.eundunhealth.domain.repository.AuthRepository
import com.gunnys.eundunhealth.domain.repository.SyncBatchOutcome
import com.gunnys.eundunhealth.domain.repository.WearBiometricRepository
import com.gunnys.eundunhealth.wear.contract.BiometricDailySummaryRecord
import com.gunnys.eundunhealth.wear.contract.BiometricPayloadCodec
import com.gunnys.eundunhealth.wear.contract.WearContractConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import retrofit2.HttpException
import java.io.IOException
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearBiometricRepositoryImpl @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val wearBiometricDao: WearBiometricDao,
    private val biometricsApi: BiometricsApi,
    private val authRepository: AuthRepository,
    private val workManager: WorkManager,
) : WearBiometricRepository {

    override fun observeRecentSummaries(limitDays: Int): Flow<List<BiometricDailySummaryRecord>> = flow {
        val userId = authRepository.getCurrentUserId()
        if (userId == null) {
            emit(emptyList())
            return@flow
        }
        val safeLimit = limitDays.coerceIn(1, MAX_BATCH_DAYS)
        val mapped =
            wearBiometricDao.observeRecentByUser(userId, safeLimit).map { entities ->
                entities.map { it.toContractRecord() }
            }
        emitAll(mapped)
    }

    override suspend fun ingestFromWatchEnvelope(rawGzipBytes: ByteArray): Result<Int> = runSuspendCatching {
        val userId = authRepository.getCurrentUserId() ?: return@runSuspendCatching 0
        val envelope = BiometricPayloadCodec.decodeBatchGzip(rawGzipBytes)
        if (envelope.summaries.isEmpty()) return@runSuspendCatching 0
        val entities =
            envelope.summaries.map { record ->
                record.toLocalEntity(
                    userId = userId,
                    watchModel = envelope.deviceModel.ifBlank { DEFAULT_WATCH_MODEL },
                    syncStatus = SmileSyncStatus.PENDING.name,
                    syncedAtUtc = null,
                )
            }
        wearBiometricDao.upsertAll(entities)
        SmileSyncWorker.enqueueUnique(workManager)
        entities.size
    }

    override suspend fun requestWatchSyncNow(): Result<String> = runSuspendCatching {
        val nodeClient = Wearable.getNodeClient(appContext)
        val messageClient = Wearable.getMessageClient(appContext)
        val connectedNodes = runCatching { nodeClient.connectedNodes.await() }.getOrDefault(emptyList())
        var pingedNodes = 0
        for (node in connectedNodes) {
            runCatching {
                messageClient
                    .sendMessage(
                        node.id,
                        WearContractConstants.SYNC_REQUEST_MESSAGE_PATH,
                        ByteArray(0),
                    ).await()
                pingedNodes++
            }
        }
        syncPendingSummaries(MAX_BATCH_DAYS)
        if (pingedNodes > 0) {
            "연결된 워치(${pingedNodes}대)에 즉시 동기화 요청 전송 완료"
        } else {
            "서버 동기화 완료 (연결된 워치 노드를 찾지 못해 로컬/서버 요약만 갱신)"
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun syncPendingSummaries(maxBatchSize: Int): SyncBatchOutcome {
        val userId = authRepository.getCurrentUserId() ?: return SyncBatchOutcome.SKIPPED_NO_USER
        val safeLimit = maxBatchSize.coerceIn(1, MAX_BATCH_DAYS)
        val pending = wearBiometricDao.getPendingByUser(userId, safeLimit)
        if (pending.isEmpty()) return SyncBatchOutcome.ALL_SYNCED

        val reqItems = pending.map { it.toApiItem() }
        return try {
            val response = biometricsApi.syncBiometricDailySummaries(BiometricSyncRequest(summaries = reqItems))
            val code = response.code()
            when {
                response.isSuccessful -> {
                    wearBiometricDao.updateSyncStatus(
                        userId = userId,
                        recordDates = pending.map { it.recordDate },
                        status = SmileSyncStatus.SYNCED.name,
                        syncedAtUtc = System.currentTimeMillis(),
                    )
                    SyncBatchOutcome.ALL_SYNCED
                }
                code == HTTP_UNAUTHORIZED -> SyncBatchOutcome.AUTH_REQUIRED
                code == HTTP_BAD_REQUEST || code == HTTP_UNPROCESSABLE_ENTITY -> {
                    wearBiometricDao.updateSyncStatus(
                        userId = userId,
                        recordDates = pending.map { it.recordDate },
                        status = SmileSyncStatus.BLOCKED.name,
                        syncedAtUtc = null,
                    )
                    SyncBatchOutcome.ALL_SYNCED
                }
                else -> SyncBatchOutcome.RETRY_NEEDED
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: IOException) {
            SyncBatchOutcome.RETRY_NEEDED
        } catch (_: Exception) {
            SyncBatchOutcome.RETRY_NEEDED
        }
    }

    override suspend fun refreshRemoteIfEmpty(days: Int): Result<Int> = runSuspendCatching {
        val userId = authRepository.getCurrentUserId() ?: return@runSuspendCatching 0
        if (wearBiometricDao.countByUser(userId) > 0) {
            return@runSuspendCatching 0
        }
        val response = biometricsApi.getBiometricDailySummaries(days = days.coerceIn(1, MAX_BATCH_DAYS))
        if (!response.isSuccessful) {
            throw HttpException(response)
        }
        val items = response.body()?.items.orEmpty()
        if (items.isEmpty()) return@runSuspendCatching 0
        val nowMs = System.currentTimeMillis()
        val entities = items.map { it.toRemoteEntity(userId = userId, syncedAtUtc = nowMs) }
        wearBiometricDao.upsertAll(entities)
        entities.size
    }

    override suspend fun deleteAllSummaries(): Result<Unit> = runSuspendCatching {
        val userId = authRepository.getCurrentUserId() ?: error("로그인이 필요합니다.")
        val response = biometricsApi.deleteBiometricDailySummaries()
        if (!response.isSuccessful) {
            throw HttpException(response)
        }
        wearBiometricDao.deleteAllByUser(userId)
    }

    companion object {
        private const val MAX_BATCH_DAYS = 30
        private const val DEFAULT_WATCH_MODEL = "Xiaomi Watch 5 (M2501W1)"
        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_UNPROCESSABLE_ENTITY = 422
    }
}

private fun BiometricDailySummaryRecord.toLocalEntity(
    userId: String,
    watchModel: String,
    syncStatus: String,
    syncedAtUtc: Long?,
): WearBiometricSummaryEntity = WearBiometricSummaryEntity(
    userId = userId,
    recordDate = recordDate,
    capturedAtEpochMs = capturedAtEpochMs,
    totalSteps = totalSteps,
    activeCaloriesKcal = activeCaloriesKcal,
    restingBpm = restingBpm,
    avgBpm = avgBpm,
    minBpm = minBpm,
    maxBpm = maxBpm,
    hrvRmssdMs = hrvRmssdMs,
    totalMonitoredMinutes = totalMonitoredMinutes,
    relaxedZoneMinutes = relaxedZoneMinutes,
    normalZoneMinutes = normalZoneMinutes,
    elevatedTensionMinutes = elevatedTensionMinutes,
    highLoadMinutes = highLoadMinutes,
    tensionEpisodeCount = tensionEpisodeCount,
    tensionMinutes = tensionMinutes,
    peakTensionBpm = peakTensionBpm,
    rapidDeltaSpikeCount = rapidDeltaSpikeCount,
    maxDeltaBpmPerMin = maxDeltaBpmPerMin ?: 0,
    activityOverloadCount = activityOverloadCount,
    avgRecoveryDropBpm = avgRecoveryDropBpm,
    sleepAwakeningCount = sleepAwakeningCount,
    recoveryScore = recoveryScore,
    hourlyBpmBucketsJson = BiometricPayloadCodec.encodeHourlyBucketsJson(hourlyBuckets),
    sourceDevicesJson =
    BiometricPayloadCodec.encodeSourceDevicesJson(
        sourceDevices.ifEmpty { listOf(watchModel) },
    ),
    watchModel = watchModel,
    syncStatus = syncStatus,
    syncedAtUtc = syncedAtUtc,
)

private fun WearBiometricSummaryEntity.toContractRecord(): BiometricDailySummaryRecord = BiometricDailySummaryRecord(
    recordDate = recordDate,
    capturedAtEpochMs = capturedAtEpochMs,
    totalSteps = totalSteps,
    activeCaloriesKcal = activeCaloriesKcal,
    restingBpm = restingBpm,
    avgBpm = avgBpm,
    minBpm = minBpm,
    maxBpm = maxBpm,
    hrvRmssdMs = hrvRmssdMs,
    totalMonitoredMinutes = totalMonitoredMinutes,
    relaxedZoneMinutes = relaxedZoneMinutes,
    normalZoneMinutes = normalZoneMinutes,
    elevatedTensionMinutes = elevatedTensionMinutes,
    highLoadMinutes = highLoadMinutes,
    tensionEpisodeCount = tensionEpisodeCount,
    tensionMinutes = tensionMinutes,
    peakTensionBpm = peakTensionBpm,
    rapidDeltaSpikeCount = rapidDeltaSpikeCount,
    maxDeltaBpmPerMin = maxDeltaBpmPerMin,
    activityOverloadCount = activityOverloadCount,
    avgRecoveryDropBpm = avgRecoveryDropBpm,
    sleepAwakeningCount = sleepAwakeningCount,
    recoveryScore = recoveryScore,
    hourlyBuckets = BiometricPayloadCodec.decodeHourlyBucketsJson(hourlyBpmBucketsJson),
    sourceDevices = BiometricPayloadCodec.decodeSourceDevicesJson(sourceDevicesJson).ifEmpty { listOf(watchModel) },
)

private fun WearBiometricSummaryEntity.toApiItem(): BiometricDailySummaryItem = BiometricDailySummaryItem(
    recordDate = recordDate,
    capturedAtEpochMs = capturedAtEpochMs,
    zoneId = "Asia/Seoul",
    totalSteps = totalSteps.coerceIn(0, 200_000),
    activeCaloriesKcal = BigDecimal.valueOf(activeCaloriesKcal.coerceIn(0.0, 20_000.0)),
    restingBpm = restingBpm?.coerceIn(30, 220),
    avgBpm = avgBpm?.coerceIn(30, 240),
    minBpm = minBpm?.coerceIn(25, 240),
    maxBpm = maxBpm?.coerceIn(30, 260),
    hrvRmssdMs = hrvRmssdMs?.coerceIn(0.0, 500.0)?.let { BigDecimal.valueOf(it) },
    totalMonitoredMinutes = totalMonitoredMinutes.coerceIn(0, 1440),
    relaxedZoneMinutes = relaxedZoneMinutes.coerceIn(0, 1440),
    normalZoneMinutes = normalZoneMinutes.coerceIn(0, 1440),
    elevatedTensionMinutes = elevatedTensionMinutes.coerceIn(0, 1440),
    highLoadMinutes = highLoadMinutes.coerceIn(0, 1440),
    tensionEpisodeCount = tensionEpisodeCount.coerceIn(0, 500),
    tensionMinutes = tensionMinutes.coerceIn(0, 1440),
    peakTensionBpm = peakTensionBpm?.coerceIn(40, 260),
    rapidDeltaSpikeCount = rapidDeltaSpikeCount.coerceIn(0, 500),
    maxDeltaBpmPerMin = maxDeltaBpmPerMin.coerceIn(0, 180),
    activityOverloadCount = activityOverloadCount.coerceIn(0, 500),
    avgRecoveryDropBpm = avgRecoveryDropBpm?.coerceIn(-100, 180),
    sleepAwakeningCount = sleepAwakeningCount.coerceIn(0, 200),
    recoveryScore = recoveryScore?.coerceIn(0, 100),
    hourlyBpmBucketsJson = hourlyBpmBucketsJson.take(8192),
    sourceDevicesJson = sourceDevicesJson.take(1024),
    watchModel = watchModel.take(128),
)

private fun BiometricDailySummaryItem.toRemoteEntity(
    userId: String,
    syncedAtUtc: Long,
): WearBiometricSummaryEntity = WearBiometricSummaryEntity(
    userId = userId,
    recordDate = recordDate,
    capturedAtEpochMs = capturedAtEpochMs,
    totalSteps = totalSteps ?: 0,
    activeCaloriesKcal = activeCaloriesKcal?.toDouble() ?: 0.0,
    restingBpm = restingBpm,
    avgBpm = avgBpm,
    minBpm = minBpm,
    maxBpm = maxBpm,
    hrvRmssdMs = hrvRmssdMs?.toDouble(),
    totalMonitoredMinutes = totalMonitoredMinutes ?: 0,
    relaxedZoneMinutes = relaxedZoneMinutes ?: 0,
    normalZoneMinutes = normalZoneMinutes ?: 0,
    elevatedTensionMinutes = elevatedTensionMinutes ?: 0,
    highLoadMinutes = highLoadMinutes ?: 0,
    tensionEpisodeCount = tensionEpisodeCount ?: 0,
    tensionMinutes = tensionMinutes ?: 0,
    peakTensionBpm = peakTensionBpm,
    rapidDeltaSpikeCount = rapidDeltaSpikeCount ?: 0,
    maxDeltaBpmPerMin = maxDeltaBpmPerMin ?: 0,
    activityOverloadCount = activityOverloadCount ?: 0,
    avgRecoveryDropBpm = avgRecoveryDropBpm,
    sleepAwakeningCount = sleepAwakeningCount ?: 0,
    recoveryScore = recoveryScore,
    hourlyBpmBucketsJson = hourlyBpmBucketsJson ?: "[]",
    sourceDevicesJson = sourceDevicesJson ?: "[\"Xiaomi Watch 5 (M2501W1)\"]",
    watchModel = watchModel ?: "Xiaomi Watch 5 (M2501W1)",
    syncStatus = SmileSyncStatus.SYNCED.name,
    syncedAtUtc = syncedAtUtc,
)
