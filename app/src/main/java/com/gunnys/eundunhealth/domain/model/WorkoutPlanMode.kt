package com.gunnys.eundunhealth.domain.model

enum class WorkoutPlanMode {
    GYM,
    HOME,
}

fun WorkoutPlanMode.toWireValue(): String = name.lowercase()

fun String.toWorkoutPlanMode(): WorkoutPlanMode = when (lowercase()) {
    "gym" -> WorkoutPlanMode.GYM
    "home" -> WorkoutPlanMode.HOME
    else -> error("지원하지 않는 운동 환경입니다: $this")
}
