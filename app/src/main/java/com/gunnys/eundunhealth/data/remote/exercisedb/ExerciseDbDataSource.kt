package com.gunnys.eundunhealth.data.remote.exercisedb

import com.gunnys.eundunhealth.domain.model.FitnessLevel
import javax.inject.Inject

class ExerciseDbDataSource @Inject constructor(
    private val api: ExerciseDbApi
) {
    suspend fun getStrengthExercises(bodyPart: String, limit: Int = 5): List<ExerciseDto> =
        api.getByBodyPart(bodyPart, limit = limit)

    suspend fun getCardioExercises(limit: Int = 5): List<ExerciseDto> =
        api.getByBodyPart("cardio", limit = limit)

    fun getSetsAndReps(level: FitnessLevel): Pair<Int, Int> = when (level) {
        FitnessLevel.BEGINNER -> 3 to 10
        FitnessLevel.INTERMEDIATE -> 4 to 12
        FitnessLevel.ADVANCED -> 4 to 15
    }
}
