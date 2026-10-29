package com.gunnys.eundunhealth.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gunnys.eundunhealth.data.local.entity.SmileSessionEntity
import com.gunnys.eundunhealth.data.local.entity.WeeklyPlanEntity
import com.gunnys.eundunhealth.di.DatabaseModule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 실제 SQLite 런타임 상에서 [DatabaseModule] 의 Room 빌더 설정과 `1 -> 2` 마이그레이션을 검증한다.
 *
 * **배경 (INC-2026-10-29-34 / EUNDUNHEALTH-H)**:
 * `DatabaseModule.provideSmileTrainingDatabase` 및 `DatabaseModule.provideDatabase` 가
 * 실제 Android SQLite 엔진 위에서(신규 생성 및 구버전 DB 마이그레이션 모두) 크래시 없이
 * 초기화되고 CRUD 가 동작하는지 보장한다.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationAndInitTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun cleanUpBefore() {
        context.deleteDatabase("eundun_db")
        context.deleteDatabase("smile_training.db")
    }

    @After
    fun cleanUpAfter() {
        context.deleteDatabase("eundun_db")
        context.deleteDatabase("smile_training.db")
    }

    @Test
    fun databaseModuleProvidesWorkingDatabasesOnFreshInstall() = runBlocking {
        val eundunDb = DatabaseModule.provideDatabase(context)
        val smileDb = DatabaseModule.provideSmileTrainingDatabase(context)
        try {
            val weeklyDao = DatabaseModule.provideWeeklyPlanDao(eundunDb)
            val smileDao = DatabaseModule.provideSmileSessionDao(smileDb)

            weeklyDao.insertPlan(
                WeeklyPlanEntity(
                    id = "plan-1",
                    userId = "user-1",
                    weekStart = "2026-10-26",
                    planMode = "home",
                    dayPlansJson = "[]",
                ),
            )
            assertNotNull(weeklyDao.getPlan("user-1", "2026-10-26"))

            smileDao.insertSession(
                SmileSessionEntity(
                    id = "session-1",
                    userId = "user-1",
                    durationSec = 30,
                    completedReps = 5,
                    occurredAtUtc = 1700000000000L,
                    syncStatus = "PENDING",
                ),
            )
            assertEquals(1, smileDao.getOldestPendingByUser("user-1").size)
        } finally {
            eundunDb.close()
            smileDb.close()
        }
    }

    @Test
    fun smileTrainingDatabaseMigratesCleanlyFromV1ToV2() = runBlocking {
        val dbFile = context.getDatabasePath("smile_training.db")
        dbFile.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { rawDb ->
            rawDb.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `smile_sessions` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `durationSec` INTEGER NOT NULL,
                    `completedReps` INTEGER NOT NULL,
                    `timestamp` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            rawDb.execSQL(
                "INSERT INTO `smile_sessions` (`durationSec`, `completedReps`, `timestamp`) VALUES (30, 5, 1700000000000)",
            )
            rawDb.version = 1
        }

        val migratedDb = DatabaseModule.provideSmileTrainingDatabase(context)
        try {
            val dao = DatabaseModule.provideSmileSessionDao(migratedDb)
            assertEquals(emptyList<SmileSessionEntity>(), dao.getOldestPendingByUser("user-1"))

            dao.insertSession(
                SmileSessionEntity(
                    id = "session-v2",
                    userId = "user-1",
                    durationSec = 45,
                    completedReps = 8,
                    occurredAtUtc = 1700000005000L,
                    syncStatus = "PENDING",
                ),
            )
            assertEquals(1, dao.getOldestPendingByUser("user-1").size)
        } finally {
            migratedDb.close()
        }
    }
}
