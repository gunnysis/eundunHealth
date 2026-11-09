package com.gunnys.eundunhealth.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * Wear OS 6 워치(`:wear`)로부터 Data Layer 또는 백엔드(`/v2/biometrics/sync`)를 통해 동기화된
 * 5차원 상시 생체·마음 상태 일일 요약 로컬 캐시 엔티티.
 *
 * 복합 기본키 `(userId, recordDate)` 로 사용자별·날짜별 멱등 업서트를 보장한다.
 */
@Entity(
    tableName = "wear_biometric_daily_summaries",
    primaryKeys = ["userId", "recordDate"],
    indices = [
        Index(value = ["userId", "syncStatus"]),
    ],
)
data class WearBiometricSummaryEntity(
    val userId: String,
    val recordDate: String,
    val capturedAtEpochMs: Long,
    val totalSteps: Int = 0,
    val activeCaloriesKcal: Double = 0.0,
    val restingBpm: Int? = null,
    val avgBpm: Int? = null,
    val minBpm: Int? = null,
    val maxBpm: Int? = null,
    val hrvRmssdMs: Double? = null,
    val totalMonitoredMinutes: Int = 0,
    val relaxedZoneMinutes: Int = 0,
    val normalZoneMinutes: Int = 0,
    val elevatedTensionMinutes: Int = 0,
    val highLoadMinutes: Int = 0,
    val tensionEpisodeCount: Int = 0,
    val tensionMinutes: Int = 0,
    val peakTensionBpm: Int? = null,
    val rapidDeltaSpikeCount: Int = 0,
    val maxDeltaBpmPerMin: Int = 0,
    val activityOverloadCount: Int = 0,
    val avgRecoveryDropBpm: Int? = null,
    val sleepAwakeningCount: Int = 0,
    val recoveryScore: Int? = null,
    val hourlyBpmBucketsJson: String = "[]",
    val sourceDevicesJson: String = "[\"Xiaomi Watch 5 (M2501W1)\"]",
    val watchModel: String = "Xiaomi Watch 5 (M2501W1)",
    val syncStatus: String = "PENDING",
    val syncedAtUtc: Long? = null,
)
