package com.gunnys.eundunhealth.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class Exercise(
    val id: String,
    val name: String,
    val bodyPart: String,
    val equipment: String,
    val gifUrl: String,
    val instructions: List<String>,
    val sets: Int,
    val reps: Int,
    val type: ExerciseType,
)

enum class ExerciseType { STRENGTH, CARDIO }
