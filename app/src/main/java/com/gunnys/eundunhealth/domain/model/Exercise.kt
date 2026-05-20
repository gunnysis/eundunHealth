package com.gunnys.eundunhealth.domain.model

data class Exercise(
    val id: String,
    val name: String,
    val bodyPart: String,
    val equipment: String,
    val gifUrl: String,
    val instructions: List<String>,
    val sets: Int,
    val reps: Int,
    val type: ExerciseType
)

enum class ExerciseType { STRENGTH, CARDIO }
