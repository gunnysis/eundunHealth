package com.gunnys.eundunhealth.domain.model

import java.time.LocalDate

data class WeeklyPlan(
    val id: String,
    val userId: String,
    val weekStart: LocalDate,
    val days: List<DayPlan>,
)

data class DayPlan(
    val date: LocalDate,
    val exercises: List<Exercise>,
    val isRestDay: Boolean,
    val isCompleted: Boolean,
)
