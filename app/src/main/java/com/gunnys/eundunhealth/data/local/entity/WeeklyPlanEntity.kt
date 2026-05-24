package com.gunnys.eundunhealth.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weekly_plans")
data class WeeklyPlanEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val weekStart: String,
    val dayPlansJson: String,
    val cachedAt: Long = System.currentTimeMillis(),
)
