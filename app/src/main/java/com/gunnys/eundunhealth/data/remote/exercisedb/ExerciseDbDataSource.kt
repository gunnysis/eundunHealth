package com.gunnys.eundunhealth.data.remote.exercisedb

import com.gunnys.eundunhealth.domain.model.FitnessLevel
import javax.inject.Inject

class ExerciseDbDataSource @Inject constructor(
    private val api: ExerciseDbApi,
) {
    suspend fun getStrengthExercises(bodyPart: String, limit: Int = 5): List<ExerciseDto> = api.getExercises(bodyParts = bodyPart, limit = limit).data

    suspend fun getCardioExercises(limit: Int = 5): List<ExerciseDto> = api.getExercises(bodyParts = "cardio", limit = limit).data

    fun getSetsAndReps(level: FitnessLevel): Pair<Int, Int> = when (level) {
        FitnessLevel.BEGINNER -> 3 to 10
        FitnessLevel.INTERMEDIATE -> 4 to 12
        FitnessLevel.ADVANCED -> 4 to 15
    }
}
