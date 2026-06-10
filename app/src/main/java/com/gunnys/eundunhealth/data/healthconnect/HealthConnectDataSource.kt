package com.gunnys.eundunhealth.data.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.gunnys.eundunhealth.domain.model.DailyActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class HealthConnectDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    fun isAvailable(): Boolean = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun hasPermissions(): Boolean = client.permissionController.getGrantedPermissions().containsAll(PERMISSIONS)

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

    suspend fun hasDailyActivityPermissions(): Boolean {
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(DAILY_ACTIVITY_PERMISSIONS)
    }

    /** 오늘 0시~현재 걸음·칼로리·평균심박 집계 (aggregate 1회). 변환/경계는 순수 매퍼에 위임. */
    suspend fun readTodayActivity(): DailyActivity {
        val (start, end) = todayRange(Instant.now(), ZoneId.systemDefault())
        val res = client.aggregate(
            AggregateRequest(
                metrics = setOf(
                    StepsRecord.COUNT_TOTAL,
                    TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                    HeartRateRecord.BPM_AVG,
                ),
                timeRangeFilter = TimeRangeFilter.between(start, end),
            ),
        )
        return DailyActivity(
            steps = res[StepsRecord.COUNT_TOTAL],
            totalCaloriesKcal = kcalToInt(res[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories),
            avgHeartRateBpm = res[HeartRateRecord.BPM_AVG],
        )
    }

    companion object {
        /** 권한 set 의 단일 출처 — DataSource(권한 확인)와 MainActivity(권한 요청)가 공유. */
        val PERMISSIONS = setOf(
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        )

        val DAILY_ACTIVITY_PERMISSIONS = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
        )
    }
}
