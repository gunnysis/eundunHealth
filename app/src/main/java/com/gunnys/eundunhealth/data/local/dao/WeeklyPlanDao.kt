package com.gunnys.eundunhealth.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gunnys.eundunhealth.data.local.entity.WeeklyPlanEntity

@Dao
interface WeeklyPlanDao {
    @Query("SELECT * FROM weekly_plans WHERE userId = :userId AND weekStart = :weekStart")
    suspend fun getPlan(userId: String, weekStart: String): WeeklyPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: WeeklyPlanEntity)

    @Query("DELETE FROM weekly_plans WHERE cachedAt < :timestamp")
    suspend fun deleteOldPlans(timestamp: Long)
}
