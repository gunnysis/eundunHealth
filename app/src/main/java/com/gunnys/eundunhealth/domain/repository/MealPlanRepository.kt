package com.gunnys.eundunhealth.domain.repository

import com.gunnys.eundunhealth.domain.model.WeeklyMealPlan

interface MealPlanRepository {
    suspend fun getCurrentPlan(): Result<WeeklyMealPlan?>
    suspend fun generatePlan(): Result<WeeklyMealPlan>
}
