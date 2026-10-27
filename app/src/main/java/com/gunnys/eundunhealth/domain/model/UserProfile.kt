package com.gunnys.eundunhealth.domain.model

import androidx.compose.runtime.Immutable

const val DEFAULT_REST_DAY = 7
const val MIN_DAY_OF_WEEK = 1
const val MAX_DAY_OF_WEEK = 7
const val MAX_REST_DAYS = 6
const val TOTAL_DAYS_IN_WEEK = 7

/**
 * 휴식 요일 컬렉션을 도메인 불변식(1..7 범위 클램프, 중복 제거, 오름차순 정렬, 최대 6일, 빈 입력 시 [7])에 맞게 정규화한다.
 */
fun normalizeRestDays(raw: Collection<Int>?): List<Int> = raw
    ?.map { it.coerceIn(MIN_DAY_OF_WEEK, MAX_DAY_OF_WEEK) }
    ?.distinct()
    ?.sorted()
    ?.take(MAX_REST_DAYS)
    ?.takeIf { it.isNotEmpty() }
    ?: listOf(DEFAULT_REST_DAY)

@Immutable
data class UserProfile(
    val userId: String,
    val heightCm: Float,
    val weightKg: Float,
    val gender: Gender,
    val bodyFatPercent: Float?,
    val muscleMassKg: Float?,
    /** 휴식 요일 목록 — ISO DayOfWeek 값 (1=월 ~ 7=일), 오름차순 1~6개. 기본값 [7](일요일). */
    val restDays: List<Int> = listOf(DEFAULT_REST_DAY),
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
