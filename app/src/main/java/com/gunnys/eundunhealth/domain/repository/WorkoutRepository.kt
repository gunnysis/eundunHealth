package com.gunnys.eundunhealth.domain.repository

import com.gunnys.eundunhealth.domain.model.UserProfile
import com.gunnys.eundunhealth.domain.model.WeeklyPlan
import java.time.LocalDate

interface WorkoutRepository {
    suspend fun getCurrentWeekPlan(): Result<WeeklyPlan?>
    suspend fun createWeeklyPlan(profile: UserProfile): Result<WeeklyPlan>
    suspend fun savePlanToServer(plan: WeeklyPlan): Result<Unit>
    suspend fun updateDayCompletion(planId: String, date: LocalDate, completed: Boolean): Result<Unit>
    suspend fun getHistory(page: Int, size: Int): Result<Pair<List<WeeklyPlan>, Int>>
}
