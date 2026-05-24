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
    const val WEEK_1_COMPLETE = "week_1_complete"
    const val WEEK_2_COMPLETE = "week_2_complete"
    const val STREAK_3_WEEKS = "streak_3weeks"
}
