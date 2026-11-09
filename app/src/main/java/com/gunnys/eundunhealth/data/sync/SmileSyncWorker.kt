package com.gunnys.eundunhealth.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gunnys.eundunhealth.domain.repository.SmileTrainingRepository
import com.gunnys.eundunhealth.domain.repository.SyncBatchOutcome
import com.gunnys.eundunhealth.domain.repository.WearBiometricRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class SmileSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: SmileTrainingRepository,
    private val wearBiometricRepository: WearBiometricRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val smileOutcome = repository.syncPendingSessions(MAX_BATCH_SIZE)
        val wearOutcome = wearBiometricRepository.syncPendingSummaries(MAX_WEAR_BATCH_DAYS)
        return when {
            smileOutcome == SyncBatchOutcome.AUTH_REQUIRED || wearOutcome == SyncBatchOutcome.AUTH_REQUIRED ->
                Result.failure()
            smileOutcome == SyncBatchOutcome.RETRY_NEEDED || wearOutcome == SyncBatchOutcome.RETRY_NEEDED ->
                Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "smile-sync"
        private const val MAX_BATCH_SIZE = 10
        private const val MAX_WEAR_BATCH_DAYS = 14
        private const val INITIAL_BACKOFF_SECONDS = 30L

        fun buildRequest(): OneTimeWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            return OneTimeWorkRequestBuilder<SmileSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    INITIAL_BACKOFF_SECONDS,
                    TimeUnit.SECONDS,
                )
                .build()
        }

        fun enqueueUnique(workManager: WorkManager) {
            workManager.enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                buildRequest(),
            )
        }
    }
}
