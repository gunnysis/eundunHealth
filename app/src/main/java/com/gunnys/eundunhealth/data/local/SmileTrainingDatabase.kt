package com.gunnys.eundunhealth.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.gunnys.eundunhealth.data.local.dao.SmileSessionDao
import com.gunnys.eundunhealth.data.local.entity.SmileSessionEntity

@Database(
    entities = [SmileSessionEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class SmileTrainingDatabase : RoomDatabase() {
    abstract fun smileSessionDao(): SmileSessionDao
}
