package com.gunnys.eundunhealth.domain.model

import java.time.ZoneId
import com.gunnys.eundunhealth.wear.contract.KST_ZONE as CONTRACT_KST_ZONE

/**
 * 앱 전역에서 주간 계획 시작일(월요일), 일별 기록 버킷, 미소 훈련 일자 판정에 사용하는 한국 표준시(KST) 고정 타임존.
 * 실제 SSoT는 `:core:wear-contract`의 [CONTRACT_KST_ZONE]이며 기존 `:app` 호출부 호환을 위해 재노출한다.
 */
val KST_ZONE: ZoneId = CONTRACT_KST_ZONE

/**
 * 초(seconds) 단위 시간을 한국어 요약 문자열(`"1분 20초"`, `"2분"`, `"15초"`)로 변환한다.
 */
fun formatDurationKorean(seconds: Int): String {
    val safeSec = seconds.coerceAtLeast(0)
    val mins = safeSec / 60
    val remSec = safeSec % 60
    return when {
        mins > 0 && remSec > 0 -> "${mins}분 ${remSec}초"
        mins > 0 -> "${mins}분"
        else -> "${remSec}초"
    }
}
