package com.gunnys.eundunhealth.domain.model

import androidx.compose.runtime.Immutable

enum class SmileRoutineType(
    val displayName: String,
    val subtitle: String,
    val baseHoldDurationMs: Long,
    val baseEnterSmileThreshold: Float,
    val baseExitSmileThreshold: Float,
    val requiresEyesOpen: Boolean,
) {
    GENTLE(
        displayName = "가벼운 미소",
        subtitle = "입꼬리를 살짝 올리고 1.5초 유지해요",
        baseHoldDurationMs = 1500L,
        baseEnterSmileThreshold = 0.60f,
        baseExitSmileThreshold = 0.50f,
        requiresEyesOpen = false,
    ),
    SUSTAINED(
        displayName = "미소 유지",
        subtitle = "분명한 미소를 3초간 편안하게 유지해요",
        baseHoldDurationMs = 3000L,
        baseEnterSmileThreshold = 0.75f,
        baseExitSmileThreshold = 0.65f,
        requiresEyesOpen = false,
    ),
    SMILE_AND_FRONT(
        displayName = "정면 바라보며 미소",
        subtitle = "카메라를 정면으로 보며 2초간 미소 지어요",
        baseHoldDurationMs = 2000L,
        baseEnterSmileThreshold = 0.60f,
        baseExitSmileThreshold = 0.50f,
        requiresEyesOpen = true,
    ),
}

enum class SmileDifficultyLevel(
    val displayName: String,
    val description: String,
    val smileThresholdDelta: Float,
    val holdMultiplier: Float,
    val maxYawDeg: Float,
    val maxRollDeg: Float,
) {
    COMFORTABLE(
        displayName = "편안",
        description = "낮은 기준과 짧은 유지 시간",
        smileThresholdDelta = -0.10f,
        holdMultiplier = 0.80f,
        maxYawDeg = 15f,
        maxRollDeg = 12f,
    ),
    STANDARD(
        displayName = "기본",
        description = "권장 기준과 기본 유지 시간",
        smileThresholdDelta = 0.0f,
        holdMultiplier = 1.0f,
        maxYawDeg = 15f,
        maxRollDeg = 12f,
    ),
    CHALLENGE(
        displayName = "도전",
        description = "1.5배 유지 시간과 더 곧은 정면 자세",
        smileThresholdDelta = 0.0f,
        holdMultiplier = 1.50f,
        maxYawDeg = 10f,
        maxRollDeg = 10f,
    ),
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
    val routine: SmileRoutineType,
    val difficulty: SmileDifficultyLevel,
    val durationSec: Int,
    val completedReps: Int,
    val reflectionEmoji: Int?,
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
