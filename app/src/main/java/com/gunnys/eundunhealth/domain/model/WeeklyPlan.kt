package com.gunnys.eundunhealth.domain.model

import androidx.compose.runtime.Immutable
import java.time.LocalDate

@Immutable
data class WeeklyPlan(
    val id: String,
    val userId: String,
    val weekStart: LocalDate,
    val days: List<DayPlan>,
) {
    /**
     * 어느 날에든 운동이 하나라도 배치돼 있으면 true.
     *
     * 빈 운동 풀로 생성·저장된 "껍데기" 계획(요일·휴식일은 맞지만 운동이 0개)을 감지하는 데 쓴다
     * (R8 keep 갭 / ExerciseDB 일시 장애 등). GetOrCreateWeeklyPlanUseCase 가 이 값이 false 면
     * 재생성하고, WorkoutRepositoryImpl 은 이런 계획을 애초에 저장하지 않는다.
     */
    val hasExercises: Boolean get() = days.any { it.exercises.isNotEmpty() }
}

@Immutable
data class DayPlan(
    val date: LocalDate,
    val exercises: List<Exercise>,
    val isRestDay: Boolean,
    val isCompleted: Boolean,
)
