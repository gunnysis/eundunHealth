package com.gunnys.eundunhealth.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gunnys.eundunhealth.data.local.dao.SmileSessionDao
import com.gunnys.eundunhealth.data.local.entity.SmileSessionEntity

@Database(
    entities = [SmileSessionEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class SmileTrainingDatabase : RoomDatabase() {
    abstract fun smileSessionDao(): SmileSessionDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `smile_sessions`")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `smile_sessions` (
                        `id` TEXT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `durationSec` INTEGER NOT NULL,
                        `completedReps` INTEGER NOT NULL,
                        `occurredAtUtc` INTEGER NOT NULL,
                        `syncStatus` TEXT NOT NULL,
                        `syncedAtUtc` INTEGER,
                        `lastSyncErrorCode` INTEGER,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_smile_sessions_userId_occurredAtUtc` " +
                        "ON `smile_sessions` (`userId` ASC, `occurredAtUtc` DESC)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_smile_sessions_userId_syncStatus` " +
                        "ON `smile_sessions` (`userId`, `syncStatus`)",
                )
            }
        }
    }
}
