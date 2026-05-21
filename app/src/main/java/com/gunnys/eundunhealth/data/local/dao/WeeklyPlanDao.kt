package com.gunnys.eundunhealth.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gunnys.eundunhealth.data.local.entity.WeeklyPlanEntity

@Dao
interface WeeklyPlanDao {
    @Query("SELECT * FROM weekly_plans WHERE weekStart = :weekStart ORDER BY cachedAt DESC LIMIT 1")
    suspend fun getPlan(weekStart: String): WeeklyPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: WeeklyPlanEntity)

    @Query("DELETE FROM weekly_plans WHERE cachedAt < :timestamp")
    suspend fun deleteOldPlans(timestamp: Long)
}
