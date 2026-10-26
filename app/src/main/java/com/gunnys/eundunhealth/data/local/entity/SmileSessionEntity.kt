package com.gunnys.eundunhealth.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "smile_sessions",
    indices = [
        Index(value = ["userId", "occurredAtUtc"], orders = [Index.Order.ASC, Index.Order.DESC]),
        Index(value = ["userId", "syncStatus"]),
    ],
)
data class SmileSessionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val routine: String,
    val difficulty: String,
    val durationSec: Int,
    val completedReps: Int,
    val reflectionEmoji: Int?,
    val occurredAtUtc: Long,
    val syncStatus: String,
    val syncedAtUtc: Long? = null,
    val lastSyncErrorCode: Int? = null,
)
