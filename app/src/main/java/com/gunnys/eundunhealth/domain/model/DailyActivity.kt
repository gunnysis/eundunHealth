package com.gunnys.eundunhealth.domain.model

import androidx.compose.runtime.Immutable

/** 오늘의 활동 요약. HC 에 없으면 각 필드 null. */
@Immutable
data class DailyActivity(
    val steps: Long?,
    val totalCaloriesKcal: Int?,
    val avgHeartRateBpm: Long?,
) {
    val hasAny: Boolean get() = steps != null || totalCaloriesKcal != null || avgHeartRateBpm != null
}
