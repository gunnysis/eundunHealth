package com.gunnys.eundunhealth.data.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class HealthConnectDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    val permissions = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
    )

    fun isAvailable(): Boolean = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun hasPermissions(): Boolean = client.permissionController.getGrantedPermissions().containsAll(permissions)

    suspend fun getExerciseDatesThisWeek(weekStart: LocalDate): List<LocalDate> {
        val zoneId = ZoneId.systemDefault()
        val startTime = weekStart.atStartOfDay(zoneId).toInstant()
        val endTime = weekStart.plusDays(7).atStartOfDay(zoneId).toInstant()

        val request = ReadRecordsRequest(
            recordType = ExerciseSessionRecord::class,
            timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
        )
        return client.readRecords(request).records
            .map { it.startTime.atZone(zoneId).toLocalDate() }
            .distinct()
    }
}
