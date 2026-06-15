package com.gunnys.eundunhealth.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeeklyPlanTest {

    private val monday = LocalDate.of(2026, 6, 15)

    private fun exercise() = Exercise(
        id = "e1",
        name = "푸시업",
        bodyPart = "chest",
        equipment = "body weight",
        gifUrl = "",
        instructions = emptyList(),
        sets = 3,
        reps = 10,
        type = ExerciseType.STRENGTH,
    )

    @Test
    fun `hasExercises 는 어떤 날에든 운동이 하나라도 있으면 true`() {
        val plan = WeeklyPlan(
            "p",
            "u",
            monday,
            listOf(
                DayPlan(monday, emptyList(), isRestDay = true, isCompleted = false),
                DayPlan(monday.plusDays(1), listOf(exercise()), isRestDay = false, isCompleted = false),
            ),
        )
        assertTrue(plan.hasExercises)
    }

    @Test
    fun `hasExercises 는 모든 날의 운동이 비어 있으면 false`() {
        val plan = WeeklyPlan(
            "p",
            "u",
            monday,
            listOf(
                DayPlan(monday, emptyList(), isRestDay = false, isCompleted = false),
                DayPlan(monday.plusDays(1), emptyList(), isRestDay = false, isCompleted = false),
                DayPlan(monday.plusDays(2), emptyList(), isRestDay = true, isCompleted = false),
            ),
        )
        assertFalse(plan.hasExercises)
    }
}
