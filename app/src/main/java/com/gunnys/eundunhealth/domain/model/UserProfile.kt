package com.gunnys.eundunhealth.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class UserProfile(
    val userId: String,
    val heightCm: Float,
    val weightKg: Float,
    val bodyFatPercent: Float?,
    val muscleMassKg: Float?,
    /** 휴식일 — ISO DayOfWeek 값 (1=월 ~ 7=일). 기본값 7(일요일). */
    val restDay: Int = 7,
) {
    val bmi: Float get() = weightKg / ((heightCm / 100f) * (heightCm / 100f))

    // null coalesces to 0f → BMI-only path. Null-safe type, NOT a behavior change:
    // (bodyFatPercent ?: 0f) == 0f gives the same classification result as old 0f default.
    val fitnessLevel: FitnessLevel get() = when {
        (bodyFatPercent ?: 0f) > 30f || bmi > 30f -> FitnessLevel.BEGINNER
        (bodyFatPercent ?: 0f) > 20f || bmi > 25f -> FitnessLevel.INTERMEDIATE
        else -> FitnessLevel.ADVANCED
    }
}

enum class FitnessLevel { BEGINNER, INTERMEDIATE, ADVANCED }
