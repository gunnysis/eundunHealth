package com.gunnys.eundunhealth.domain.model

import androidx.compose.runtime.Immutable
import java.time.Instant

enum class GoalType(val key: String, val unit: String, val label: String) {
    WEIGHT("weight", "kg", "목표 체중"),
    BODY_FAT("body_fat", "%", "목표 체지방률"),
    ;

    companion object {
        fun fromKey(key: String): GoalType? = entries.firstOrNull { it.key == key }
    }
}

@Immutable
data class Goal(
    val type: GoalType,
    val targetValue: Float,
    val createdAt: Instant?,
)

@Immutable
data class ProfileHistoryPoint(
    val heightCm: Float,
    val weightKg: Float,
    val bodyFatPct: Float?,
    val muscleMassKg: Float?,
    val recordedAt: Instant?,
)
