package com.gunnys.eundunhealth.domain.usecase

import com.gunnys.eundunhealth.domain.model.CurrentWeekPlanRead
import com.gunnys.eundunhealth.domain.repository.WorkoutRepository
import javax.inject.Inject

/**
 * 현재 주의 운동 계획을 조회한다.
 *
 * 자동 생성이나 덮어쓰기를 수행하지 않으며, 원격 200/유효 캐시(`Found`), 원격 정상 404(`Missing`),
 * 파싱·불변식 오류(`Unreadable`), 네트워크·서버 실패(`Failed`)를 그대로 반환한다.
 */
class LoadCurrentWeekPlanUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
) {
    suspend operator fun invoke(): CurrentWeekPlanRead = workoutRepo.loadCurrentWeekPlan()
}
