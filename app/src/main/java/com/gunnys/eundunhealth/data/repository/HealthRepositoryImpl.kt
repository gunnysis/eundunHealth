package com.gunnys.eundunhealth.data.repository

import com.gunnys.eundunhealth.data.healthconnect.HealthConnectDataSource
import com.gunnys.eundunhealth.domain.repository.HealthRepository
import java.time.LocalDate
import javax.inject.Inject

class HealthRepositoryImpl @Inject constructor(
    private val healthConnect: HealthConnectDataSource,
) : HealthRepository {

    override suspend fun isAvailable(): Boolean = try {
        healthConnect.isAvailable()
    } catch (_: Exception) {
        false
    }

    override suspend fun hasPermissions(): Boolean = try {
        healthConnect.hasPermissions()
    } catch (_: Exception) {
        false
    }

    override suspend fun getExerciseDatesThisWeek(weekStart: LocalDate): Result<List<LocalDate>> = runCatching { healthConnect.getExerciseDatesThisWeek(weekStart) }
}
