package com.gunnys.eundunhealth.data.repository

import androidx.work.WorkManager
import com.gunnys.eundunhealth.api.generated.api.SmileTrainingApi
import com.gunnys.eundunhealth.api.generated.model.SmileDifficulty
import com.gunnys.eundunhealth.api.generated.model.SmileRoutine
import com.gunnys.eundunhealth.api.generated.model.SmileTrainingSessionRequest
import com.gunnys.eundunhealth.api.generated.model.SmileTrainingSessionResponse
import com.gunnys.eundunhealth.data.local.dao.SmileSessionDao
import com.gunnys.eundunhealth.data.local.entity.SmileSessionEntity
import com.gunnys.eundunhealth.data.sync.SmileSyncWorker
import com.gunnys.eundunhealth.domain.model.SmileDifficultyLevel
import com.gunnys.eundunhealth.domain.model.SmileRoutineType
import com.gunnys.eundunhealth.domain.model.SmileSessionRecord
import com.gunnys.eundunhealth.domain.model.SmileSummaryStats
import com.gunnys.eundunhealth.domain.model.SmileSyncStatus
import com.gunnys.eundunhealth.domain.repository.AuthRepository
import com.gunnys.eundunhealth.domain.repository.SmileTrainingRepository
import com.gunnys.eundunhealth.domain.repository.SyncBatchOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmileTrainingRepositoryImpl @Inject constructor(
    private val smileSessionDao: SmileSessionDao,
    private val smileTrainingApi: SmileTrainingApi,
    private val authRepository: AuthRepository,
    private val workManager: WorkManager,
) : SmileTrainingRepository {

    override fun observeSessions(): Flow<List<SmileSessionRecord>> = flow {
        val userId = authRepository.getCurrentUserId()
        if (userId == null) {
            emit(emptyList())
            return@flow
        }
        val mappedFlow = smileSessionDao.observeSessionsByUser(userId).map { entities: List<SmileSessionEntity> ->
            mapEntitiesToDomain(entities)
        }
        emitAll(mappedFlow)
    }

    override fun observeSummaryStats(): Flow<SmileSummaryStats> = flow {
        val userId = authRepository.getCurrentUserId()
        if (userId == null) {
            emit(SmileSummaryStats())
            return@flow
        }
        val statsFlow = smileSessionDao.observeSessionsByUser(userId).map { entities: List<SmileSessionEntity> ->
            computeSummaryStats(entities, LocalDate.now(KST_ZONE))
        }
        emitAll(statsFlow)
    }

    override suspend fun saveCompletedSession(
        routine: SmileRoutineType,
        difficulty: SmileDifficultyLevel,
        durationSec: Int,
        completedReps: Int,
        reflectionEmoji: Int?,
        occurredAtEpochMs: Long,
    ): Result<SmileSessionRecord> = runCatching {
        val userId = authRepository.getCurrentUserId() ?: error("로그인이 필요합니다.")
        val sessionId = UUID.randomUUID().toString()
        val entity = SmileSessionEntity(
            id = sessionId,
            userId = userId,
            routine = routine.name,
            difficulty = difficulty.name,
            durationSec = durationSec.coerceIn(0, MAX_DURATION_SEC),
            completedReps = completedReps.coerceIn(1, MAX_COMPLETED_REPS),
            reflectionEmoji = reflectionEmoji?.coerceIn(1, 5),
            occurredAtUtc = occurredAtEpochMs,
            syncStatus = SmileSyncStatus.PENDING.name,
            syncedAtUtc = null,
            lastSyncErrorCode = null,
        )
        smileSessionDao.insertSession(entity)
        SmileSyncWorker.enqueueUnique(workManager)
        entity.toDomain()
    }

    override suspend fun syncPendingSessions(maxBatchSize: Int): SyncBatchOutcome {
        val userId = authRepository.getCurrentUserId() ?: return SyncBatchOutcome.SKIPPED_NO_USER
        val pendingList = smileSessionDao.getOldestPendingByUser(userId, limit = maxBatchSize)
        if (pendingList.isEmpty()) return SyncBatchOutcome.ALL_SYNCED

        var needsRetry = false
        for (entity in pendingList) {
            val outcome = syncSingleSession(entity, userId)
            when (outcome) {
                SingleSyncResult.SYNCED, SingleSyncResult.BLOCKED -> Unit
                SingleSyncResult.AUTH_REQUIRED -> return SyncBatchOutcome.AUTH_REQUIRED
                SingleSyncResult.RETRY -> needsRetry = true
            }
        }
        return if (needsRetry) SyncBatchOutcome.RETRY_NEEDED else SyncBatchOutcome.ALL_SYNCED
    }

    override suspend fun refreshRemoteHistoryIfEmpty(): Result<Int> = runCatching {
        val userId = authRepository.getCurrentUserId() ?: return@runCatching 0
        if (smileSessionDao.getSessionsByUser(userId).isNotEmpty()) {
            return@runCatching 0
        }
        val response = smileTrainingApi.listSmileTrainingSessions(limit = REMOTE_HYDRATE_LIMIT)
        if (!response.isSuccessful) {
            throw HttpException(response)
        }
        val remoteItems = response.body()?.items.orEmpty()
        val nowMs = System.currentTimeMillis()
        var insertedCount = 0
        for (item in remoteItems) {
            val entity = mapRemoteItemToEntity(item, userId, nowMs)
            val rowId = smileSessionDao.insertSession(entity)
            if (rowId != -1L) {
                insertedCount += 1
            }
        }
        insertedCount
    }

    override suspend fun retryBlockedSessions(): Result<Int> = runCatching {
        val userId = authRepository.getCurrentUserId() ?: error("로그인이 필요합니다.")
        val resetCount = smileSessionDao.resetBlockedToPending(userId)
        if (resetCount > 0) {
            SmileSyncWorker.enqueueUnique(workManager)
        }
        resetCount
    }

    override suspend fun deleteAllSessions(): Result<Unit> = runCatching {
        val userId = authRepository.getCurrentUserId() ?: error("로그인이 필요합니다.")
        val response = smileTrainingApi.deleteAllSmileTrainingSessions()
        if (!response.isSuccessful) {
            throw HttpException(response)
        }
        smileSessionDao.deleteAllByUser(userId)
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun syncSingleSession(
        entity: SmileSessionEntity,
        userId: String,
    ): SingleSyncResult {
        val request = SmileTrainingSessionRequest(
            id = UUID.fromString(entity.id),
            routine = runCatching { SmileRoutine.valueOf(entity.routine) }.getOrDefault(SmileRoutine.GENTLE),
            difficulty = runCatching { SmileDifficulty.valueOf(entity.difficulty) }
                .getOrDefault(SmileDifficulty.STANDARD),
            durationSec = entity.durationSec,
            completedReps = entity.completedReps,
            reflectionEmoji = entity.reflectionEmoji,
            occurredAt = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(entity.occurredAtUtc)),
        )

        return try {
            val response = smileTrainingApi.recordSmileTrainingSession(request)
            val code = response.code()
            when {
                response.isSuccessful -> {
                    smileSessionDao.updateSyncStatus(
                        id = entity.id,
                        userId = userId,
                        status = SmileSyncStatus.SYNCED.name,
                        syncedAtUtc = System.currentTimeMillis(),
                        errorCode = null,
                    )
                    SingleSyncResult.SYNCED
                }
                code == HTTP_UNAUTHORIZED -> SingleSyncResult.AUTH_REQUIRED
                code == HTTP_BAD_REQUEST || code == HTTP_CONFLICT || code == HTTP_UNPROCESSABLE_ENTITY -> {
                    smileSessionDao.updateSyncStatus(
                        id = entity.id,
                        userId = userId,
                        status = SmileSyncStatus.BLOCKED.name,
                        syncedAtUtc = null,
                        errorCode = code,
                    )
                    SingleSyncResult.BLOCKED
                }
                else -> SingleSyncResult.RETRY
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: IOException) {
            SingleSyncResult.RETRY
        } catch (_: Exception) {
            SingleSyncResult.RETRY
        }
    }

    private enum class SingleSyncResult {
        SYNCED,
        BLOCKED,
        AUTH_REQUIRED,
        RETRY,
    }

    companion object {
        private val KST_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
        private const val MAX_DURATION_SEC = 1800
        private const val MAX_COMPLETED_REPS = 5
        private const val REMOTE_HYDRATE_LIMIT = 50
        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_CONFLICT = 409
        private const val HTTP_UNPROCESSABLE_ENTITY = 422

        private fun mapRemoteItemToEntity(
            item: SmileTrainingSessionResponse,
            userId: String,
            syncedAtUtc: Long,
        ): SmileSessionEntity {
            val occurredEpochMs = runCatching { Instant.parse(item.occurredAt).toEpochMilli() }
                .recoverCatching { OffsetDateTime.parse(item.occurredAt).toInstant().toEpochMilli() }
                .getOrDefault(syncedAtUtc)

            return SmileSessionEntity(
                id = item.id,
                userId = userId,
                routine = item.routine.name,
                difficulty = item.difficulty.name,
                durationSec = item.durationSec.coerceIn(0, MAX_DURATION_SEC),
                completedReps = item.completedReps.coerceIn(1, MAX_COMPLETED_REPS),
                reflectionEmoji = item.reflectionEmoji?.coerceIn(1, 5),
                occurredAtUtc = occurredEpochMs,
                syncStatus = SmileSyncStatus.SYNCED.name,
                syncedAtUtc = syncedAtUtc,
                lastSyncErrorCode = null,
            )
        }

        private fun mapEntitiesToDomain(entities: List<SmileSessionEntity>): List<SmileSessionRecord> {
            val result = ArrayList<SmileSessionRecord>(entities.size)
            for (entity in entities) {
                result.add(entity.toDomain())
            }
            return result
        }

        internal fun computeSummaryStats(
            entities: List<SmileSessionEntity>,
            todayKst: LocalDate,
        ): SmileSummaryStats {
            if (entities.isEmpty()) return SmileSummaryStats()

            val totalSessions = entities.size
            val totalDurationSec = entities.sumOf { it.durationSec }
            val pendingCount = entities.count { it.syncStatus == SmileSyncStatus.PENDING.name }
            val blockedCount = entities.count { it.syncStatus == SmileSyncStatus.BLOCKED.name }

            val distinctDatesDesc = entities
                .map { Instant.ofEpochMilli(it.occurredAtUtc).atZone(KST_ZONE).toLocalDate() }
                .distinct()
                .sortedDescending()

            val isCompletedToday = distinctDatesDesc.firstOrNull() == todayKst
            val currentStreak = computeCurrentStreak(distinctDatesDesc, todayKst)
            val longestStreak = computeLongestStreak(distinctDatesDesc)

            return SmileSummaryStats(
                totalSessions = totalSessions,
                totalDurationSec = totalDurationSec,
                isCompletedToday = isCompletedToday,
                currentStreakDays = currentStreak,
                longestStreakDays = longestStreak,
                pendingSyncCount = pendingCount,
                blockedSyncCount = blockedCount,
            )
        }

        private fun computeCurrentStreak(
            distinctDatesDesc: List<LocalDate>,
            todayKst: LocalDate,
        ): Int {
            val latest = distinctDatesDesc.firstOrNull() ?: return 0
            val yesterday = todayKst.minusDays(1)
            if (latest != todayKst && latest != yesterday) return 0

            var streak = 1
            var expected = latest.minusDays(1)
            for (idx in 1 until distinctDatesDesc.size) {
                if (distinctDatesDesc[idx] == expected) {
                    streak += 1
                    expected = expected.minusDays(1)
                } else {
                    break
                }
            }
            return streak
        }

        private fun computeLongestStreak(distinctDatesDesc: List<LocalDate>): Int {
            if (distinctDatesDesc.isEmpty()) return 0
            val datesAsc = distinctDatesDesc.asReversed()
            var longest = 1
            var current = 1
            for (idx in 1 until datesAsc.size) {
                if (datesAsc[idx] == datesAsc[idx - 1].plusDays(1)) {
                    current += 1
                    if (current > longest) longest = current
                } else {
                    current = 1
                }
            }
            return longest
        }

        private fun SmileSessionEntity.toDomain(): SmileSessionRecord = SmileSessionRecord(
            id = id,
            userId = userId,
            routine = runCatching { SmileRoutineType.valueOf(routine) }.getOrDefault(SmileRoutineType.GENTLE),
            difficulty = runCatching { SmileDifficultyLevel.valueOf(difficulty) }
                .getOrDefault(SmileDifficultyLevel.STANDARD),
            durationSec = durationSec,
            completedReps = completedReps,
            reflectionEmoji = reflectionEmoji,
            occurredAtEpochMs = occurredAtUtc,
            syncStatus = runCatching { SmileSyncStatus.valueOf(syncStatus) }.getOrDefault(SmileSyncStatus.PENDING),
        )
    }
}
