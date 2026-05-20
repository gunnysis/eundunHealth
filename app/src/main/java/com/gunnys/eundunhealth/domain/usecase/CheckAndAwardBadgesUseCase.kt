package com.gunnys.eundunhealth.domain.usecase

import com.gunnys.eundunhealth.domain.model.Badge
import com.gunnys.eundunhealth.domain.model.BadgeKeys
import com.gunnys.eundunhealth.domain.model.WeeklyPlan
import com.gunnys.eundunhealth.domain.repository.BadgeRepository
import javax.inject.Inject

class CheckAndAwardBadgesUseCase @Inject constructor(
    private val badgeRepo: BadgeRepository
) {
    suspend operator fun invoke(plan: WeeklyPlan): Result<List<Badge>> = runCatching {
        val completedCount = plan.days.count { it.isCompleted }
        val totalWorkoutDays = plan.days.count { !it.isRestDay }
        val awarded = mutableListOf<Badge>()

        if (totalWorkoutDays > 0 && completedCount >= totalWorkoutDays) {
            val key = BadgeKeys.WEEK_1_COMPLETE
            if (!badgeRepo.hasBadge(key)) {
                badgeRepo.awardBadge(key).onSuccess { awarded += it }
            }
        }
        awarded
    }
}
