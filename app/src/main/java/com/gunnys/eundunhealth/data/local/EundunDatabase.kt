package com.gunnys.eundunhealth.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.gunnys.eundunhealth.data.local.dao.WeeklyPlanDao
import com.gunnys.eundunhealth.data.local.entity.WeeklyPlanEntity

@Database(entities = [WeeklyPlanEntity::class], version = 2, exportSchema = false)
abstract class EundunDatabase : RoomDatabase() {
    abstract fun weeklyPlanDao(): WeeklyPlanDao
}
