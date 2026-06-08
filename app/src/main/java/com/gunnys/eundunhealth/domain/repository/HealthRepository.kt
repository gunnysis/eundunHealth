package com.gunnys.eundunhealth.domain.repository

import java.time.LocalDate

interface HealthRepository {
    /** Health Connect SDK 설치/가용 여부. false면 권한 요청·동기화 자체가 불가능. */
    suspend fun isAvailable(): Boolean

    suspend fun hasPermissions(): Boolean

    suspend fun getExerciseDatesThisWeek(weekStart: LocalDate): Result<List<LocalDate>>
}
