package com.gunnys.eundunhealth.data.healthconnect

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
