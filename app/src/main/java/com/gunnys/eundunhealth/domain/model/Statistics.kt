package com.gunnys.eundunhealth.domain.model

import androidx.compose.runtime.Immutable
import java.time.LocalDate

@Immutable
data class WeeklyRate(
    val weekStart: LocalDate,
    val completionRate: Float,
)

@Immutable
data class Statistics(
    val weeklyRates: List<WeeklyRate>,
    val currentStreak: Int,
    val longestStreak: Int,
)
