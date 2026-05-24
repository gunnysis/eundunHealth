package com.gunnys.eundunhealth.domain.usecase

import com.gunnys.eundunhealth.domain.model.WeeklyPlan
import com.gunnys.eundunhealth.domain.repository.HealthRepository
import com.gunnys.eundunhealth.domain.repository.WorkoutRepository
import javax.inject.Inject

class SyncHealthDataUseCase @Inject constructor(
    private val healthRepo: HealthRepository,
    private val workoutRepo: WorkoutRepository,
) {
    suspend operator fun invoke(plan: WeeklyPlan): Result<WeeklyPlan> = runCatching {
        if (!healthRepo.hasPermissions()) return@runCatching plan

        val completedDates = healthRepo.getExerciseDatesThisWeek(plan.weekStart).getOrElse {
            io.sentry.Sentry.captureException(it)
            emptyList()
        }
        val updatedDays = plan.days.map { day ->
            if (!day.isRestDay && day.date in completedDates) {
                day.copy(isCompleted = true)
            } else {
                day
            }
        }
        plan.copy(days = updatedDays)
    }
}
