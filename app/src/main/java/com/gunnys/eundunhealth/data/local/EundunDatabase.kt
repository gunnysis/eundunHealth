package com.gunnys.eundunhealth.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gunnys.eundunhealth.data.local.dao.WearBiometricDao
import com.gunnys.eundunhealth.data.local.dao.WeeklyPlanDao
import com.gunnys.eundunhealth.data.local.entity.WearBiometricSummaryEntity
import com.gunnys.eundunhealth.data.local.entity.WeeklyPlanEntity

@Database(
    entities = [
        WeeklyPlanEntity::class,
        WearBiometricSummaryEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class EundunDatabase : RoomDatabase() {
    abstract fun weeklyPlanDao(): WeeklyPlanDao
    abstract fun wearBiometricDao(): WearBiometricDao

    companion object {
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `wear_biometric_daily_summaries` (
                        `userId` TEXT NOT NULL,
                        `recordDate` TEXT NOT NULL,
                        `capturedAtEpochMs` INTEGER NOT NULL,
                        `totalSteps` INTEGER NOT NULL,
                        `activeCaloriesKcal` REAL NOT NULL,
                        `restingBpm` INTEGER,
                        `avgBpm` INTEGER,
                        `minBpm` INTEGER,
                        `maxBpm` INTEGER,
                        `hrvRmssdMs` REAL,
                        `totalMonitoredMinutes` INTEGER NOT NULL,
                        `relaxedZoneMinutes` INTEGER NOT NULL,
                        `normalZoneMinutes` INTEGER NOT NULL,
                        `elevatedTensionMinutes` INTEGER NOT NULL,
                        `highLoadMinutes` INTEGER NOT NULL,
                        `tensionEpisodeCount` INTEGER NOT NULL,
                        `tensionMinutes` INTEGER NOT NULL,
                        `peakTensionBpm` INTEGER,
                        `rapidDeltaSpikeCount` INTEGER NOT NULL,
                        `maxDeltaBpmPerMin` INTEGER NOT NULL,
                        `activityOverloadCount` INTEGER NOT NULL,
                        `avgRecoveryDropBpm` INTEGER,
                        `sleepAwakeningCount` INTEGER NOT NULL,
                        `recoveryScore` INTEGER,
                        `hourlyBpmBucketsJson` TEXT NOT NULL,
                        `sourceDevicesJson` TEXT NOT NULL,
                        `watchModel` TEXT NOT NULL,
                        `syncStatus` TEXT NOT NULL,
                        `syncedAtUtc` INTEGER,
                        PRIMARY KEY(`userId`, `recordDate`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_wear_biometric_daily_summaries_userId_syncStatus` " +
                        "ON `wear_biometric_daily_summaries` (`userId`, `syncStatus`)",
                )
            }
        }
    }
}
