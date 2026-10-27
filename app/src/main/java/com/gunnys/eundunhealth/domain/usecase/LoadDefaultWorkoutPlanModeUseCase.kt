package com.gunnys.eundunhealth.domain.usecase

import com.gunnys.eundunhealth.domain.model.WorkoutPlanMode
import com.gunnys.eundunhealth.domain.repository.UserRepository
import javax.inject.Inject

/**
 * 사용자의 프로필에 설정된 기본 운동 환경(`defaultWorkoutPlanMode`)을 조회한다.
 *
 * 조회 실패 시 `null`로 폴백해 `NoPlan`(수동 선택)으로 안전하게 진입하도록 한다.
 */
class LoadDefaultWorkoutPlanModeUseCase @Inject constructor(
    private val userRepo: UserRepository,
) {
    suspend operator fun invoke(): WorkoutPlanMode? = userRepo.getProfile().getOrNull()?.defaultWorkoutPlanMode
}
