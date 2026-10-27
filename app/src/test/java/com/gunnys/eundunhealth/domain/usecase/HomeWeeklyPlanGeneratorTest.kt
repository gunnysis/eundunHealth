package com.gunnys.eundunhealth.domain.usecase

import com.gunnys.eundunhealth.domain.model.ExercisePrescriptionUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class HomeWeeklyPlanGeneratorTest {

    private val monday = LocalDate.of(2026, 6, 8)

    @Test
    fun `결정론성 — 같은 weekStart 와 restDay 는 항상 동일한 7일 계획을 생성한다`() {
        val first = HomeWeeklyPlanGenerator.generate(monday, restDay = 7)
        val second = HomeWeeklyPlanGenerator.generate(monday, restDay = 7)
        assertEquals(first, second)
    }

    @Test
    fun `7일 불변식 및 지정 휴식일 외 모든 날에 최소 1개 이상의 무장비 운동이 배치된다`() {
        for (restDay in 1..7) {
            val days = HomeWeeklyPlanGenerator.generate(monday, restDay)
            assertEquals(7, days.size)
            assertEquals((0L..6L).map(monday::plusDays), days.map { it.date })
            assertEquals(1, days.count { it.isRestDay })
            val restPlan = days.single { it.isRestDay }
            assertEquals(DayOfWeek.of(restDay), restPlan.date.dayOfWeek)
            assertTrue(restPlan.exercises.isEmpty())

            val workoutDays = days.filterNot { it.isRestDay }
            assertEquals(6, workoutDays.size)
            assertTrue(workoutDays.all { it.exercises.isNotEmpty() })
            workoutDays.flatMap { it.exercises }.forEach { ex ->
                assertTrue(ex.id.startsWith("local:home:") && ex.id.endsWith(":v2.0"))
                assertEquals("없음", ex.equipment)
                assertEquals("", ex.gifUrl)
                assertTrue(ex.instructions.isNotEmpty())
            }
        }
    }

    @Test
    fun `HomeExerciseCatalog 는 확정된 18종으로 구성되며 플랭크는 SECONDS 단위를 사용한다`() {
        assertEquals(18, HomeExerciseCatalog.all.size)
        assertEquals(18, HomeExerciseCatalog.all.map { it.id }.toSet().size)
        val plank = HomeExerciseCatalog.all.single { it.id == "local:home:forearm-plank:v2.0" }
        assertEquals(ExercisePrescriptionUnit.SECONDS, plank.prescriptionUnit)
        assertEquals(45, plank.reps)
    }
}
