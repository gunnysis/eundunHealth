package com.gunnys.eundunhealth.data.remote.api.dto

import com.gunnys.eundunhealth.domain.model.ExerciseType
import org.junit.Assert.assertEquals
import org.junit.Test

class PlanJsonModelsTest {

    private fun exerciseJson(type: String) = ExerciseJson(
        id = "e1",
        name = "X",
        bodyPart = "chest",
        equipment = "bw",
        gifUrl = "",
        instructions = emptyList(),
        sets = 3,
        reps = 10,
        type = type,
    )

    @Test
    fun `알 수 없는 type 은 STRENGTH 로 기본 처리되어 예외를 던지지 않는다`() {
        // 서버/레거시 row 에 알 수 없는 enum 값이 있어도, 운동 1개가 아니라 주 전체 plan 이 통째로
        // 비던 회귀(ExerciseType.valueOf 예외 → parseDayPlans getOrDefault(emptyList))를 방지.
        val exercise = exerciseJson("FUTURE_TYPE").toExercise()
        assertEquals(ExerciseType.STRENGTH, exercise.type)
    }

    @Test
    fun `정상 type 은 그대로 매핑된다`() {
        assertEquals(ExerciseType.CARDIO, exerciseJson("CARDIO").toExercise().type)
        assertEquals(ExerciseType.STRENGTH, exerciseJson("STRENGTH").toExercise().type)
    }
}
