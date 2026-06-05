package com.gunnys.eundunhealth.domain.model

import androidx.compose.runtime.Immutable
import java.time.LocalDate

@Immutable
data class WeeklyPlan(
    val id: String,
    val userId: String,
    val weekStart: LocalDate,
    val days: List<DayPlan>,
)

@Immutable
data class DayPlan(
    val date: LocalDate,
    val exercises: List<Exercise>,
    val isRestDay: Boolean,
    val isCompleted: Boolean,
)
