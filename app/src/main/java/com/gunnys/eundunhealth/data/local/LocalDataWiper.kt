package com.gunnys.eundunhealth.data.local

import androidx.work.WorkManager
import com.gunnys.eundunhealth.data.local.dao.SmileSessionDao
import com.gunnys.eundunhealth.data.local.dao.WeeklyPlanDao
import com.gunnys.eundunhealth.data.preferences.ThemePreferences
import com.gunnys.eundunhealth.data.sync.SmileSyncWorker
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton

interface LocalDataWiper {
    suspend fun wipeAllForAccountDeletion(userId: String?)
    suspend fun ensureV2LocalResetCompleted()
}

/**
 * 계정 삭제 및 v2 로컬 리셋 경계의 Room/WorkManager/DataStore 정리를 담당한다.
 *
 * [weeklyPlanDao] 와 [smileSessionDao] 는 [Lazy] 로 주입받는다:
 * - [DefaultLocalDataWiper] 는 `AuthViewModel` 생성자에서 첫 Compose 프레임(메인 스레드)에 즉시 주입된다.
 * - Eager 주입 시 [ThemePreferences.isV2LocalResetCompleted] 가 이미 `true` 인 일반 기동에서도
 *   `EundunDatabase` 와 `SmileTrainingDatabase` 의 `Room.databaseBuilder().build()` 가 메인 스레드
 *   생성자 체인에서 동기 실행되어 기동 지연 및 생성자 예외 전파 위험을 만든다(INC-2026-10-29-34).
 */
@Singleton
class DefaultLocalDataWiper @Inject constructor(
    private val weeklyPlanDao: Lazy<WeeklyPlanDao>,
    private val smileSessionDao: Lazy<SmileSessionDao>,
    private val themePreferences: ThemePreferences,
    private val workManager: WorkManager,
) : LocalDataWiper {

    override suspend fun wipeAllForAccountDeletion(userId: String?) {
        workManager.cancelUniqueWork(SmileSyncWorker.UNIQUE_WORK_NAME)
        if (userId != null) {
            weeklyPlanDao.get().deleteAllByUser(userId)
            smileSessionDao.get().deleteAllByUser(userId)
        } else {
            weeklyPlanDao.get().clearAll()
            smileSessionDao.get().clearAll()
        }
        themePreferences.clearAll()
    }

    override suspend fun ensureV2LocalResetCompleted() {
        if (themePreferences.isV2LocalResetCompleted()) return
        workManager.cancelUniqueWork(SmileSyncWorker.UNIQUE_WORK_NAME)
        weeklyPlanDao.get().clearAll()
        smileSessionDao.get().clearAll()
        themePreferences.markV2LocalResetCompleted()
    }
}
