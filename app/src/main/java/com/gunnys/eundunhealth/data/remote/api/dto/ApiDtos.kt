package com.gunnys.eundunhealth.data.remote.api.dto

data class UserProfileDto(
    val userId: String,
    val heightCm: Float,
    val weightKg: Float,
    val bodyFatPct: Float?,
    val muscleMassKg: Float?
)

data class UserProfileRequest(
    val heightCm: Float,
    val weightKg: Float,
    val bodyFatPct: Float?,
    val muscleMassKg: Float?
)

data class WeeklyPlanDto(
    val id: String,
    val userId: String,
    val weekStart: String,
    val dayPlans: String
)

data class CreateWeeklyPlanRequest(
    val weekStart: String,
    val dayPlans: String
)

data class UpdateDayCompletionRequest(
    val date: String,
    val completed: Boolean
)

data class WeeklyPlanHistoryDto(
    val plans: List<WeeklyPlanDto>,
    val totalCount: Int,
    val page: Int,
    val size: Int
)

data class BadgeDto(
    val id: String,
    val userId: String,
    val badgeKey: String,
    val earnedAt: String?
)

data class WeeklyRateDto(
    val weekStart: String,
    val completionRate: Float,
)

data class StatisticsDto(
    val weeklyRates: List<WeeklyRateDto>,
    val currentStreak: Int,
    val longestStreak: Int,
)
