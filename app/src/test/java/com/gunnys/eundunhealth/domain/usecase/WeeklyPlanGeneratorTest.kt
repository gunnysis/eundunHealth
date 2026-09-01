package com.gunnys.eundunhealth.domain.usecase

import com.gunnys.eundunhealth.domain.model.Exercise
import com.gunnys.eundunhealth.domain.model.ExerciseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class WeeklyPlanGeneratorTest {
    private val monday = LocalDate.of(2026, 6, 8) // 월요일

    // ktlint `function-signature` ↔ detekt `MaxLineLength` 충돌 — 사유는 `di/DatabaseModule.kt` 주석 참조.
    // (detektDebug 는 테스트 소스를 분석하지 않지만 "app/src 전체 140자 초과 0" 불변식을 유지한다)
    @Suppress("ktlint:standard:function-signature")
    private fun ex(id: String, type: ExerciseType = ExerciseType.STRENGTH) =
        Exercise(id, "n$id", "chest", "body weight", "", emptyList(), 3, 10, type)

    private fun pool(prefix: String, n: Int, type: ExerciseType = ExerciseType.STRENGTH) = (1..n).map { ex("$prefix$it", type) }

    @Test
    fun `결정성 — 같은 입력은 같은 결과`() {
        val a = WeeklyPlanGenerator.generate(monday, 7, pool("p", 6), pool("u", 6), pool("l", 6), pool("c", 10, ExerciseType.CARDIO))
        val b = WeeklyPlanGenerator.generate(monday, 7, pool("p", 6), pool("u", 6), pool("l", 6), pool("c", 10, ExerciseType.CARDIO))
        assertEquals(a, b)
    }

    @Test
    fun `7일 반환 + restDay 위치`() {
        val days = WeeklyPlanGenerator.generate(monday, 3, pool("p", 6), pool("u", 6), pool("l", 6), pool("c", 10, ExerciseType.CARDIO))
        assertEquals(7, days.size)
        assertTrue(days[2].isRestDay) // restDay=3(수) → index 2
        assertEquals(DayOfWeek.WEDNESDAY, days[2].date.dayOfWeek)
        assertEquals(1, days.count { it.isRestDay })
    }

    @Test
    fun `restDay 범위 밖이면 coerce — 0은 월요일`() {
        val days = WeeklyPlanGenerator.generate(monday, 0, pool("p", 6), pool("u", 6), pool("l", 6), pool("c", 10, ExerciseType.CARDIO))
        assertTrue(days[0].isRestDay) // coerceIn(1,7) → 1(월)
    }

    @Test
    fun `빈 풀이어도 7일 생성 + 크래시 없음`() {
        val days = WeeklyPlanGenerator.generate(monday, 7, emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(7, days.size)
        assertTrue(days.filter { !it.isRestDay }.all { it.exercises.isEmpty() })
    }

    @Test
    fun `운동일은 최대 4개 strength 슬롯`() {
        val days = WeeklyPlanGenerator.generate(monday, 7, pool("p", 6), pool("u", 6), pool("l", 6), pool("c", 10, ExerciseType.CARDIO))
        assertTrue(days[0].exercises.size <= 4) // 월 = pushShuffled.take(4)
        assertTrue(days[0].exercises.all { it.id.startsWith("p") })
    }
}
