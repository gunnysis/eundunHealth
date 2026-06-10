package com.gunnys.eundunhealth.data.healthconnect

import com.gunnys.eundunhealth.domain.model.BodyComposition
import java.time.Instant
import java.time.ZoneId

/**
 * [HealthConnectDataSource] 의 순수 변환·경계 로직 — Health Connect SDK 타입 의존이 없어
 * JVM 단위 테스트로 직접 검증 가능하다 (DataSource 본체는 HC client 호출만 담당).
 *
 * 단위변환(kcal 절사)·KST 자정 경계·"최신 기록 채택" 은 silent 하게 어긋나기 쉬운 지점이라
 * 별도 함수로 분리해 회귀를 테스트로 박제한다.
 */

/** [zone] 기준 오늘 0시 ~ [now] 범위(시작, 끝). 활동 집계 윈도우. */
internal fun todayRange(now: Instant, zone: ZoneId): Pair<Instant, Instant> {
    val start = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
    return start to now
}

/** aggregate 결과의 칼로리(kcal, Double) → Int 절사(0 방향). null 은 보존. */
internal fun kcalToInt(kcal: Double?): Int? = kcal?.toInt()

/**
 * 최근 기록들에서 시간 기준 최신 체중·체지방을 각각 독립적으로 채택한다.
 * measuredAt 은 채택된 둘 중 더 최근 시각. 둘 다 비면 모든 필드 null.
 *
 * @param weights (측정시각, kg) 목록
 * @param bodyFats (측정시각, %) 목록
 */
internal fun reduceBodyComposition(
    weights: List<Pair<Instant, Float>>,
    bodyFats: List<Pair<Instant, Float>>,
): BodyComposition {
    val latestWeight = weights.maxByOrNull { it.first }
    val latestBodyFat = bodyFats.maxByOrNull { it.first }
    return BodyComposition(
        weightKg = latestWeight?.second,
        bodyFatPercent = latestBodyFat?.second,
        measuredAt = listOfNotNull(latestWeight?.first, latestBodyFat?.first).maxOrNull(),
    )
}
