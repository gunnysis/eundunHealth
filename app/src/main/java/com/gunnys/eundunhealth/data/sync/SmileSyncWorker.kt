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
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class SmileSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: SmileTrainingRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = when (repository.syncPendingSessions(MAX_BATCH_SIZE)) {
        SyncBatchOutcome.ALL_SYNCED -> Result.success()
        SyncBatchOutcome.RETRY_NEEDED -> Result.retry()
        SyncBatchOutcome.AUTH_REQUIRED -> Result.failure()
        SyncBatchOutcome.SKIPPED_NO_USER -> Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "smile-sync"
        private const val MAX_BATCH_SIZE = 10
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
                ExistingWorkPolicy.KEEP,
                buildRequest(),
            )
        }
    }
}
