package com.gunnys.eundunhealth.domain.model

import java.time.Instant

data class Badge(
    val id: String,
    val userId: String,
    val key: String,
    val name: String,
    val description: String,
    val earnedAt: Instant?,
)

object BadgeKeys {
    // v0.1 — 주간/스트릭
    const val WEEK_1_COMPLETE = "week_1_complete"
    const val WEEK_2_COMPLETE = "week_2_complete"
    const val STREAK_3_WEEKS = "streak_3weeks"

    // v0.3 — 마일스톤
    const val FIRST_WORKOUT = "first_workout"
    const val WORKOUTS_10 = "workouts_10"
    const val WORKOUTS_50 = "workouts_50"
    const val STREAK_8_WEEKS = "streak_8weeks"

    // v0.3 — 목표 달성
    const val GOAL_WEIGHT_ACHIEVED = "goal_weight_achieved"
    const val GOAL_BODY_FAT_ACHIEVED = "goal_body_fat_achieved"
}
