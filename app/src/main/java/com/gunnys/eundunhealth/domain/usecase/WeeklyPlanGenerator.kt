package com.gunnys.eundunhealth.domain.usecase

import com.gunnys.eundunhealth.domain.model.DayPlan
import com.gunnys.eundunhealth.domain.model.Exercise
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.random.Random
import com.gunnys.eundunhealth.domain.model.normalizeRestDays as normalizeRestDayInts

private const val STRENGTH_SLOT_SIZE = 4
private const val CARDIO_SLOT_SIZE = 2
private const val MIXED_STRENGTH_SIZE = 2
private const val FINISHER_CARDIO_SIZE = 1
private const val SECONDARY_CARDIO_DROP = 2
private const val FINISHER_CARDIO_DROP = 4
private const val LAST_DAY_OFFSET = 6L

internal fun normalizeRestDays(restDays: List<Int>): Set<DayOfWeek> = normalizeRestDayInts(restDays).map(DayOfWeek::of).toSet()

/**
 * 주간 운동 계획의 결정론적 요일 배치 알고리즘 (순수 함수 — I/O 와 분리).
 *
 * seed = weekStart.toEpochDay() 라 같은 주는 항상 같은 결과. 풀(push/pull/legs/cardio)은
 * 이미 fetch·정렬된 상태로 주입받는다. 대근육 우선순위 큐:
 * `[Push -> Legs -> Pull -> Cardio A -> Mixed+Cardio -> Cardio B]`, `restDays` 요일은 휴식.
 */
object WeeklyPlanGenerator {
    fun generate(
        weekStart: LocalDate,
        restDays: List<Int>,
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
        val primaryCardio = cardioShuffled.take(CARDIO_SLOT_SIZE)
        val secondaryCardio = cardioShuffled.drop(SECONDARY_CARDIO_DROP).take(CARDIO_SLOT_SIZE).ifEmpty { primaryCardio }
        val finisherCardio = cardioShuffled.drop(FINISHER_CARDIO_DROP).take(FINISHER_CARDIO_SIZE).ifEmpty {
            cardioShuffled.take(FINISHER_CARDIO_SIZE)
        }
        val mixedStrength = (pushShuffled + pullShuffled + legsShuffled).shuffled(seed).take(MIXED_STRENGTH_SIZE)
        val workoutSlots: List<List<Exercise>> = listOf(
            pushShuffled.take(STRENGTH_SLOT_SIZE),
            legsShuffled.take(STRENGTH_SLOT_SIZE),
            pullShuffled.take(STRENGTH_SLOT_SIZE),
            primaryCardio,
            mixedStrength + finisherCardio,
            secondaryCardio,
        )
        val restDayOfWeeks = normalizeRestDays(restDays)
        var slotIdx = 0
        return (0L..LAST_DAY_OFFSET).map { offset ->
            val date = weekStart.plusDays(offset)
            if (date.dayOfWeek in restDayOfWeeks) {
                DayPlan(date, emptyList(), isRestDay = true, isCompleted = false)
            } else {
                val slot = workoutSlots.getOrElse(slotIdx) { workoutSlots.last() }
                slotIdx++
                DayPlan(date, slot, isRestDay = false, isCompleted = false)
            }
        }
    }
}
