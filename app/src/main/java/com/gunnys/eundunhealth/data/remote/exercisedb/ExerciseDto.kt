package com.gunnys.eundunhealth.data.remote.exercisedb

import com.gunnys.eundunhealth.domain.model.Exercise
import com.gunnys.eundunhealth.domain.model.ExerciseType

data class ExerciseDto(
    val id: String,
    val name: String,
    val bodyPart: String,
    val equipment: String,
    val gifUrl: String,
    val instructions: List<String>,
    val target: String,
    val secondaryMuscles: List<String>
)

fun ExerciseDto.toDomain(sets: Int, reps: Int, type: ExerciseType) = Exercise(
    id = id,
    name = name,
    bodyPart = bodyPart,
    equipment = equipment,
    gifUrl = gifUrl,
    instructions = instructions,
    sets = sets,
    reps = reps,
    type = type
)
