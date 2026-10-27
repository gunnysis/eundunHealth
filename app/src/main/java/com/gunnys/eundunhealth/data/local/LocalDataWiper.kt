package com.gunnys.eundunhealth.data.local

import androidx.work.WorkManager
import com.gunnys.eundunhealth.data.local.dao.SmileSessionDao
import com.gunnys.eundunhealth.data.local.dao.WeeklyPlanDao
import com.gunnys.eundunhealth.data.preferences.ThemePreferences
import com.gunnys.eundunhealth.data.sync.SmileSyncWorker
import javax.inject.Inject
import javax.inject.Singleton

interface LocalDataWiper {
    suspend fun wipeAllForAccountDeletion(userId: String?)
    suspend fun ensureV2LocalResetCompleted()
}

@Singleton
class DefaultLocalDataWiper @Inject constructor(
    private val weeklyPlanDao: WeeklyPlanDao,
    private val smileSessionDao: SmileSessionDao,
    private val themePreferences: ThemePreferences,
    private val workManager: WorkManager,
) : LocalDataWiper {

    override suspend fun wipeAllForAccountDeletion(userId: String?) {
        workManager.cancelUniqueWork(SmileSyncWorker.UNIQUE_WORK_NAME)
        if (userId != null) {
            weeklyPlanDao.deleteAllByUser(userId)
            smileSessionDao.deleteAllByUser(userId)
        } else {
            weeklyPlanDao.clearAll()
            smileSessionDao.clearAll()
        }
        themePreferences.clearAll()
    }

    override suspend fun ensureV2LocalResetCompleted() {
        if (themePreferences.isV2LocalResetCompleted()) return
        workManager.cancelUniqueWork(SmileSyncWorker.UNIQUE_WORK_NAME)
        weeklyPlanDao.clearAll()
        smileSessionDao.clearAll()
        themePreferences.markV2LocalResetCompleted()
    }
}
