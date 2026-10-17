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
    // 타입을 명시할 것 — `by lazy` 의 추론 타입만 두면 detekt 가 이 프로퍼티를 통한 호출의
    // 수신자를 못 풀어, 아래 suspend 함수들을 RedundantSuspendModifier 로 오탐한다
    // (MEASURED 2026-09-01: 명시 전 4건 → 명시 후 0건).
    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    fun isAvailable(): Boolean = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun hasPermissions(): Boolean = client.permissionController.getGrantedPermissions().containsAll(PERMISSIONS)

    suspend fun getExerciseDatesThisWeek(weekStart: LocalDate): List<LocalDate> {
        // KST 고정 — weekStart 계산(WorkoutRepository)과 동일 타임존이어야 날짜 버킷이 어긋나지 않음.
        val zoneId = ZoneId.of("Asia/Seoul")
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

    companion object {
        /** 권한 set 의 단일 출처 — DataSource(권한 확인)와 MainActivity(권한 요청)가 공유. */
        val PERMISSIONS = setOf(
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        )
    }
}
