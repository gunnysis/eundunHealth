package com.gunnys.eundunhealth.models

import kotlinx.serialization.Serializable

@Serializable
data class UserProfileRequest(
    val heightCm: Float,
    val weightKg: Float,
    val bodyFatPct: Float? = null,
    val muscleMassKg: Float? = null
)

@Serializable
data class UserProfileResponse(
    val userId: String,
    val heightCm: Float,
    val weightKg: Float,
    val bodyFatPct: Float?,
    val muscleMassKg: Float?
)

@Serializable
data class WeeklyPlanResponse(
    val id: String,
    val userId: String,
    val weekStart: String,
    val dayPlans: String  // JSON string of day plans
)

@Serializable
data class CreateWeeklyPlanRequest(
    val weekStart: String,
    val dayPlans: String  // JSON string
)

@Serializable
data class UpdateDayCompletionRequest(
    val date: String,
    val completed: Boolean
)

@Serializable
data class BadgeResponse(
    val id: String,
    val userId: String,
    val badgeKey: String,
    val earnedAt: String?
)

@Serializable
data class WeeklyPlanHistoryResponse(
    val plans: List<WeeklyPlanResponse>,
    val totalCount: Int,
    val page: Int,
    val size: Int
)

@Serializable
data class ErrorResponse(val error: String)

@Serializable
data class MessageResponse(val message: String)
