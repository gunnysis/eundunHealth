package com.gunnys.eundunhealth.domain.util

import com.gunnys.eundunhealth.domain.model.Exercise
import com.gunnys.eundunhealth.domain.model.ExercisePrescriptionUnit
import com.gunnys.eundunhealth.domain.model.ExerciseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutDurationEstimatorTest {

    @Test
    fun `빈 운동 리스트는 0분을 반환한다`() {
        assertEquals(0, WorkoutDurationEstimator.estimateMinutes(emptyList()))
    }

    @Test
    fun `단일 짧은 종목도 최소 5분을 보장한다`() {
        val singleShort = listOf(
            Exercise(
                id = "ex-1",
                name = "플랭크",
                bodyPart = "코어",
                equipment = "맨몸",
                gifUrl = "",
                instructions = emptyList(),
                sets = 1,
                reps = 15,
                type = ExerciseType.STRENGTH,
                prescriptionUnit = ExercisePrescriptionUnit.SECONDS,
            ),
        )
        assertEquals(5, WorkoutDurationEstimator.estimateMinutes(singleShort))
    }

    @Test
    fun `근력 3종목과 유산소 1종목의 예상 소요 시간을 정확히 산출한다`() {
        val exercises = listOf(
            Exercise(
                id = "1",
                name = "푸시업",
                bodyPart = "가슴",
                equipment = "맨몸",
                gifUrl = "",
                instructions = emptyList(),
                sets = 3,
                reps = 12,
                type = ExerciseType.STRENGTH,
                prescriptionUnit = ExercisePrescriptionUnit.REPS,
            ),
            Exercise(
                id = "2",
                name = "스쿼트",
                bodyPart = "하체",
                equipment = "맨몸",
                gifUrl = "",
                instructions = emptyList(),
                sets = 3,
                reps = 15,
                type = ExerciseType.STRENGTH,
                prescriptionUnit = ExercisePrescriptionUnit.REPS,
            ),
            Exercise(
                id = "3",
                name = "마운틴 클라이머",
                bodyPart = "전신",
                equipment = "맨몸",
                gifUrl = "",
                instructions = emptyList(),
                sets = 3,
                reps = 30,
                type = ExerciseType.CARDIO,
                prescriptionUnit = ExercisePrescriptionUnit.SECONDS,
            ),
        )
        val minutes = WorkoutDurationEstimator.estimateMinutes(exercises)
        // ex1: (3*36) + (2*60) + 30 = 258s
        // ex2: (3*45) + (2*60) + 30 = 285s
        // ex3: (3*30) + (2*30) + 30 = 180s
        // total = 723s -> ceil(723 / 60.0) = 13분
        assertEquals(13, minutes)
        assertTrue(minutes >= 5)
    }
}
