package com.gunnys.eundunhealth.domain.repository

import java.time.LocalDate

interface HealthRepository {
    suspend fun hasPermissions(): Boolean
    suspend fun getExerciseDatesThisWeek(weekStart: LocalDate): Result<List<LocalDate>>
}
