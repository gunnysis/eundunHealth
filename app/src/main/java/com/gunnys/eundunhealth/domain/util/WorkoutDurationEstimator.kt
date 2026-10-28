package com.gunnys.eundunhealth.domain.util

import com.gunnys.eundunhealth.domain.model.Exercise
import com.gunnys.eundunhealth.domain.model.ExercisePrescriptionUnit
import com.gunnys.eundunhealth.domain.model.ExerciseType
import kotlin.math.ceil

object WorkoutDurationEstimator {
    private const val SECONDS_PER_REP = 3
    private const val STRENGTH_REST_SECONDS = 60
    private const val CARDIO_REST_SECONDS = 30
    private const val TRANSITION_SECONDS_PER_EXERCISE = 30
    private const val SECONDS_PER_MINUTE = 60.0
    private const val MIN_ESTIMATED_MINUTES = 5

    /**
     * 종목 리스트의 세트·횟수(또는 초) 처방을 기반으로 예상 운동 소요 시간(분)을 계산한다.
     * - 종목이 0개면 0분을 반환한다.
     * - 종목이 1개 이상이면 최소 5분 이상을 보장한다.
     */
    fun estimateMinutes(exercises: List<Exercise>): Int {
        if (exercises.isEmpty()) return 0
        val totalSeconds = exercises.sumOf { ex ->
            val sets = ex.sets.coerceAtLeast(1)
            val workSecPerSet = when (ex.prescriptionUnit) {
                ExercisePrescriptionUnit.SECONDS -> ex.reps.coerceAtLeast(10)
                ExercisePrescriptionUnit.REPS -> ex.reps.coerceAtLeast(1) * SECONDS_PER_REP
            }
            val restSecPerSet = if (ex.type == ExerciseType.CARDIO) CARDIO_REST_SECONDS else STRENGTH_REST_SECONDS
            (sets * workSecPerSet) + ((sets - 1).coerceAtLeast(0) * restSecPerSet) + TRANSITION_SECONDS_PER_EXERCISE
        }
        return ceil(totalSeconds / SECONDS_PER_MINUTE).toInt().coerceAtLeast(MIN_ESTIMATED_MINUTES)
    }
}
