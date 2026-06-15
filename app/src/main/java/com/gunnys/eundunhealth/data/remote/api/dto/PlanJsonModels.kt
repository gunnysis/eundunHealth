package com.gunnys.eundunhealth.data.remote.api.dto

import com.gunnys.eundunhealth.domain.model.DayPlan
import com.gunnys.eundunhealth.domain.model.Exercise
import com.gunnys.eundunhealth.domain.model.ExerciseType
import java.time.LocalDate

data class DayPlanJson(
    val date: String,
    val exercises: List<ExerciseJson>,
    val isRestDay: Boolean,
    val isCompleted: Boolean,
    // 백엔드 update_completion 이 수동 토글 시 기록하는 키와 동일(camelCase). 구버전 row(필드 부재)는
    // Gson 이 false 로 기본 처리 → 하위호환.
    val manuallySet: Boolean = false,
) {
    constructor(dayPlan: DayPlan) : this(
        date = dayPlan.date.toString(),
        exercises = dayPlan.exercises.map { ExerciseJson(it) },
        isRestDay = dayPlan.isRestDay,
        isCompleted = dayPlan.isCompleted,
        manuallySet = dayPlan.manuallySet,
    )

    fun toDayPlan() = DayPlan(
        date = LocalDate.parse(date),
        exercises = exercises.map { it.toExercise() },
        isRestDay = isRestDay,
        isCompleted = isCompleted,
        manuallySet = manuallySet,
    )
}

data class ExerciseJson(
    val id: String,
    val name: String,
    val bodyPart: String,
    val equipment: String,
    val gifUrl: String,
    val instructions: List<String>,
    val sets: Int,
    val reps: Int,
    val type: String,
) {
    constructor(exercise: Exercise) : this(
        id = exercise.id, name = exercise.name, bodyPart = exercise.bodyPart,
        equipment = exercise.equipment, gifUrl = exercise.gifUrl,
        instructions = exercise.instructions, sets = exercise.sets,
        reps = exercise.reps, type = exercise.type.name,
    )

    fun toExercise() = Exercise(
        id = id, name = name, bodyPart = bodyPart, equipment = equipment,
        gifUrl = gifUrl, instructions = instructions, sets = sets, reps = reps,
        type = ExerciseType.valueOf(type),
    )
}
