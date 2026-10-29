package com.gunnys.eundunhealth.data.local

import androidx.work.WorkManager
import com.gunnys.eundunhealth.data.local.dao.SmileSessionDao
import com.gunnys.eundunhealth.data.local.dao.WeeklyPlanDao
import com.gunnys.eundunhealth.data.preferences.ThemePreferences
import com.gunnys.eundunhealth.data.sync.SmileSyncWorker
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [DefaultLocalDataWiper] 단위 테스트.
 *
 * 핵심 검증:
 * 1. `isV2LocalResetCompleted() == true` 인 일반 앱 기동 경로에서는 `Lazy<WeeklyPlanDao>` 와
 *    `Lazy<SmileSessionDao>` 의 `.get()` 이 호출되지 않아 Room DB 초기화가 기동 크리티컬 패스에서 일어나지 않는다.
 * 2. 최초 1회 v2 리셋(`isV2LocalResetCompleted() == false`) 및 계정 삭제 시에는 WorkManager 취소 +
 *    양쪽 DAO 정리 + DataStore 마킹/초기화가 정확히 수행된다.
 */
class DefaultLocalDataWiperTest {

    @Test
    fun `ensureV2LocalResetCompleted does not instantiate DAOs when reset is already completed`() = runTest {
        var weeklyDaoAccessed = 0
        var smileDaoAccessed = 0
        val lazyWeeklyDao = Lazy<WeeklyPlanDao> {
            weeklyDaoAccessed++
            mockk(relaxed = true)
        }
        val lazySmileDao = Lazy<SmileSessionDao> {
            smileDaoAccessed++
            mockk(relaxed = true)
        }
        val themePreferences = mockk<ThemePreferences> {
            coEvery { isV2LocalResetCompleted() } returns true
        }
        val workManager = mockk<WorkManager>(relaxed = true)

        val wiper = DefaultLocalDataWiper(
            weeklyPlanDao = lazyWeeklyDao,
            smileSessionDao = lazySmileDao,
            themePreferences = themePreferences,
            workManager = workManager,
        )

        wiper.ensureV2LocalResetCompleted()

        assertEquals("리셋 완료 상태에서는 WeeklyPlanDao를 초기화하면 안 됩니다", 0, weeklyDaoAccessed)
        assertEquals("리셋 완료 상태에서는 SmileSessionDao를 초기화하면 안 됩니다", 0, smileDaoAccessed)
        verify(exactly = 0) { workManager.cancelUniqueWork(any()) }
    }

    @Test
    fun `ensureV2LocalResetCompleted clears both DAOs and marks reset completed when not yet reset`() = runTest {
        val weeklyDao = mockk<WeeklyPlanDao>(relaxed = true)
        val smileDao = mockk<SmileSessionDao>(relaxed = true)
        val themePreferences = mockk<ThemePreferences>(relaxed = true) {
            coEvery { isV2LocalResetCompleted() } returns false
        }
        val workManager = mockk<WorkManager>(relaxed = true)

        val wiper = DefaultLocalDataWiper(
            weeklyPlanDao = Lazy { weeklyDao },
            smileSessionDao = Lazy { smileDao },
            themePreferences = themePreferences,
            workManager = workManager,
        )

        wiper.ensureV2LocalResetCompleted()

        verify(exactly = 1) { workManager.cancelUniqueWork(SmileSyncWorker.UNIQUE_WORK_NAME) }
        coVerify(exactly = 1) { weeklyDao.clearAll() }
        coVerify(exactly = 1) { smileDao.clearAll() }
        coVerify(exactly = 1) { themePreferences.markV2LocalResetCompleted() }
    }

    @Test
    fun `wipeAllForAccountDeletion deletes user rows and clears preferences when userId is present`() = runTest {
        val weeklyDao = mockk<WeeklyPlanDao>(relaxed = true)
        val smileDao = mockk<SmileSessionDao>(relaxed = true)
        val themePreferences = mockk<ThemePreferences>(relaxed = true)
        val workManager = mockk<WorkManager>(relaxed = true)

        val wiper = DefaultLocalDataWiper(
            weeklyPlanDao = Lazy { weeklyDao },
            smileSessionDao = Lazy { smileDao },
            themePreferences = themePreferences,
            workManager = workManager,
        )

        wiper.wipeAllForAccountDeletion("user-123")

        verify(exactly = 1) { workManager.cancelUniqueWork(SmileSyncWorker.UNIQUE_WORK_NAME) }
        coVerify(exactly = 1) { weeklyDao.deleteAllByUser("user-123") }
        coVerify(exactly = 1) { smileDao.deleteAllByUser("user-123") }
        coVerify(exactly = 1) { themePreferences.clearAll() }
    }
}
