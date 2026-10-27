package com.gunnys.eundunhealth.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class UserProfile(
    val userId: String,
    val heightCm: Float,
    val weightKg: Float,
    val gender: Gender,
    val bodyFatPercent: Float?,
    val muscleMassKg: Float?,
    /** 휴식일 — ISO DayOfWeek 값 (1=월 ~ 7=일). 기본값 7(일요일). */
    val restDay: Int = 7,
    /** 다음 주 계획 자동 생성에만 쓰며, 이미 생성된 계획의 mode는 바꾸지 않는다. */
    val defaultWorkoutPlanMode: WorkoutPlanMode? = null,
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

enum class Gender { MALE, FEMALE, UNSPECIFIED }
