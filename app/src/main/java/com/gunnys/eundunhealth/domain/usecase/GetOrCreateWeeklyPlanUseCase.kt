package com.gunnys.eundunhealth.domain.usecase

import com.gunnys.eundunhealth.domain.model.WeeklyPlan
import com.gunnys.eundunhealth.domain.repository.UserRepository
import com.gunnys.eundunhealth.domain.repository.WorkoutRepository
import javax.inject.Inject

class GetOrCreateWeeklyPlanUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
    private val userRepo: UserRepository,
) {
    suspend operator fun invoke(): Result<WeeklyPlan> = runCatching {
        val existing = workoutRepo.getCurrentWeekPlan().getOrNull()
        // 운동이 하나라도 있는 정상 계획만 그대로 사용. 빈 풀로 생성·저장된 "껍데기" 계획(운동 0개)은
        // 재생성해 자가치유한다 — R8 keep 갭/ExerciseDB 일시 장애로 만들어진 빈 계획이 그 주 내내
        // 고착되던 회귀 방지. createWeeklyPlan 의 upsert 가 빈 계획을 정상 계획으로 덮어쓴다.
        if (existing != null && existing.hasExercises) return@runCatching existing

        val profile = userRepo.getProfile().getOrThrow()
            ?: error("프로필이 없습니다. 신체정보를 먼저 입력해주세요.")
        workoutRepo.createWeeklyPlan(profile).getOrThrow()
    }
}
