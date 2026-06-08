package com.gunnys.eundunhealth.domain.model

import androidx.compose.runtime.Immutable
import java.time.Instant

/**
 * Health Connect 에서 읽은 최신 체성분. HC 에 한쪽만 있을 수 있어 각 필드 nullable.
 * weightKg/bodyFatPercent 둘 다 null 이면 "가져올 기록 없음".
 */
@Immutable
data class BodyComposition(
    val weightKg: Float?,
    val bodyFatPercent: Float?,
    val measuredAt: Instant?,
)
