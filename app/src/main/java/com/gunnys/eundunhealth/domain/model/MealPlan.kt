package com.gunnys.eundunhealth.domain.model

data class WeeklyMealPlan(
    val weeklyPlan: List<DailyMealPlan>,
    val summary: String,
)

data class DailyMealPlan(
    val day: String,
    val isRestDay: Boolean,
    val meals: List<MealInfo>,
)

data class MealInfo(
    val mealType: String,
    val menuName: String,
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
)
