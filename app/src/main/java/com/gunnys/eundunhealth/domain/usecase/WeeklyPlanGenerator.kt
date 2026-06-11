package com.gunnys.eundunhealth.domain.usecase

import com.gunnys.eundunhealth.domain.model.DayPlan
import com.gunnys.eundunhealth.domain.model.Exercise
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.random.Random

/**
 * 주간 운동 계획의 결정론적 요일 배치 알고리즘 (순수 함수 — I/O 와 분리).
 *
 * seed = weekStart.toEpochDay() 라 같은 주는 항상 같은 결과. 풀(push/pull/legs/cardio)은
 * 이미 fetch·정렬된 상태로 주입받는다. 배치: 월 push / 화 cardio / 수 pull / 목 cardio /
 * 금 legs / 토 mixed+cardio, restDay 요일은 휴식. (WorkoutRepositoryImpl 에서 이전)
 */
object WeeklyPlanGenerator {
    fun generate(
        weekStart: LocalDate,
        restDay: Int,
        push: List<Exercise>,
        pull: List<Exercise>,
        legs: List<Exercise>,
        cardio: List<Exercise>,
    ): List<DayPlan> {
        val seed = Random(weekStart.toEpochDay())
        val pushShuffled = push.shuffled(seed)
        val pullShuffled = pull.shuffled(seed)
        val legsShuffled = legs.shuffled(seed)
        val cardioShuffled = cardio.shuffled(seed)
        val tueCardio = cardioShuffled.take(2)
        val thuCardio = cardioShuffled.drop(2).take(2)
        val satCardio = cardioShuffled.drop(4).take(1)
        val restDayOfWeek = DayOfWeek.of(restDay.coerceIn(1, 7))
        val mixedStrength = (pushShuffled + pullShuffled + legsShuffled).shuffled(seed).take(2)
        val workoutSlots: List<List<Exercise>> = listOf(
            pushShuffled.take(4),
            tueCardio,
            pullShuffled.take(4),
            thuCardio,
            legsShuffled.take(4),
            mixedStrength + satCardio,
        )
        var slotIdx = 0
        return (0L..6L).map { offset ->
            val date = weekStart.plusDays(offset)
            if (date.dayOfWeek == restDayOfWeek) {
                DayPlan(date, emptyList(), isRestDay = true, isCompleted = false)
            } else {
                val slot = workoutSlots.getOrElse(slotIdx) { emptyList() }
                slotIdx++
                DayPlan(date, slot, isRestDay = false, isCompleted = false)
            }
        }
    }
}
