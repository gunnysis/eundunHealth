package com.gunnys.eundunhealth.domain.model

import androidx.compose.runtime.Immutable

/**
 * 스마일 트레이닝 v2 단일 표준 규격.
 * v1의 (루틴 × 난이도 × 목표 반복 횟수) 3중 설정을 완전 폐기하고 단일 표준 스펙(2초 유지 × 3회 반복)으로 고정한다.
 */
object SmileTrainingSpec {
    const val HOLD_DURATION_MS: Long = 2_000L
    const val TARGET_REPS: Int = 3
    const val ENTER_SMILE_THRESHOLD: Float = 0.55f
    const val EXIT_SMILE_THRESHOLD: Float = 0.45f
    const val MIN_EYE_OPEN_PROBABILITY: Float = 0.35f
    const val MAX_YAW_DEG: Float = 15f
    const val MAX_ROLL_DEG: Float = 12f
    const val MIN_FACE_WIDTH_RATIO: Float = 0.22f
    const val READY_AUTO_START_HOLD_MS: Long = 1_000L
    const val EMA_SMOOTHING_ALPHA: Float = 0.6f
}

enum class SmileSyncStatus {
    PENDING,
    SYNCED,
    BLOCKED,
}

@Immutable
data class SmileSessionRecord(
    val id: String,
    val userId: String,
    val durationSec: Int,
    val completedReps: Int,
    val occurredAtEpochMs: Long,
    val syncStatus: SmileSyncStatus,
)

@Immutable
data class SmileSummaryStats(
    val totalSessions: Int = 0,
    val totalDurationSec: Int = 0,
    val isCompletedToday: Boolean = false,
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0,
    val pendingSyncCount: Int = 0,
    val blockedSyncCount: Int = 0,
)
