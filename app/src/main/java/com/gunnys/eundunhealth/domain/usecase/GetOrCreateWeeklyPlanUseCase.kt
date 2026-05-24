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
        if (existing != null) return@runCatching existing

        val profile = userRepo.getProfile().getOrThrow()
            ?: error("프로필이 없습니다. 신체정보를 먼저 입력해주세요.")
        workoutRepo.createWeeklyPlan(profile).getOrThrow()
    }
}
