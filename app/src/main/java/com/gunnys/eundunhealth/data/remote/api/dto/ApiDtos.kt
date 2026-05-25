package com.gunnys.eundunhealth.data.remote.api.dto

data class UserProfileDto(
    val userId: String,
    val heightCm: Float,
    val weightKg: Float,
    val bodyFatPct: Float?,
    val muscleMassKg: Float?,
    val restDay: Int = 7,
)

data class UserProfileRequest(
    val heightCm: Float,
    val weightKg: Float,
    val bodyFatPct: Float?,
    val muscleMassKg: Float?,
    val restDay: Int = 7,
)

data class WeeklyPlanDto(
    val id: String,
    val userId: String,
    val weekStart: String,
    val dayPlans: String,
)

data class CreateWeeklyPlanRequest(
    val weekStart: String,
    val dayPlans: String,
)

data class UpdateDayCompletionRequest(
    val weekStart: String,
    val date: String,
    val completed: Boolean,
)

data class WeeklyPlanHistoryDto(
    val plans: List<WeeklyPlanDto>,
    val totalCount: Int,
    val page: Int,
    val size: Int,
)

data class BadgeDto(
    val id: String,
    val userId: String,
    val badgeKey: String,
    val earnedAt: String?,
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

data class GoalDto(
    val goalType: String, // "weight" | "body_fat"
    val targetValue: Float,
    val createdAt: String,
)

data class GoalRequest(
    val goalType: String,
    val targetValue: Float,
)

data class ProfileHistoryEntryDto(
    val heightCm: Float,
    val weightKg: Float,
    val bodyFatPct: Float?,
    val muscleMassKg: Float?,
    val recordedAt: String,
)
