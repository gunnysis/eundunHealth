package com.gunnys.eundunhealth.data.remote.api

import com.gunnys.eundunhealth.data.remote.api.dto.BadgeDto
import com.gunnys.eundunhealth.data.remote.api.dto.CreateWeeklyPlanRequest
import com.gunnys.eundunhealth.data.remote.api.dto.GoalDto
import com.gunnys.eundunhealth.data.remote.api.dto.GoalRequest
import com.gunnys.eundunhealth.data.remote.api.dto.ProfileHistoryEntryDto
import com.gunnys.eundunhealth.data.remote.api.dto.StatisticsDto
import com.gunnys.eundunhealth.data.remote.api.dto.UpdateDayCompletionRequest
import com.gunnys.eundunhealth.data.remote.api.dto.UserProfileDto
import com.gunnys.eundunhealth.data.remote.api.dto.UserProfileRequest
import com.gunnys.eundunhealth.data.remote.api.dto.WeeklyPlanDto
import com.gunnys.eundunhealth.data.remote.api.dto.WeeklyPlanHistoryDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface EundunApi {
    @GET("profile")
    suspend fun getProfile(): UserProfileDto

    @PUT("profile")
    suspend fun updateProfile(@Body req: UserProfileRequest): Response<Unit>

    @GET("weekly-plan")
    suspend fun getWeeklyPlan(@Query("weekStart") weekStart: String): WeeklyPlanDto?

    /** 기준 주 직전 plan. 없으면 body가 null. */
    @GET("weekly-plan/previous")
    suspend fun getPreviousWeeklyPlan(@Query("weekStart") weekStart: String): WeeklyPlanDto?

    @POST("weekly-plan")
    suspend fun createWeeklyPlan(@Body req: CreateWeeklyPlanRequest): WeeklyPlanDto

    @PATCH("weekly-plan/complete")
    suspend fun updateDayCompletion(@Body req: UpdateDayCompletionRequest): Response<Unit>

    @GET("weekly-plan/history")
    suspend fun getWeeklyPlanHistory(
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): WeeklyPlanHistoryDto

    @GET("weekly-plan/statistics")
    suspend fun getStatistics(@Query("weeks") weeks: Int = 12): StatisticsDto

    @GET("profile/history")
    suspend fun getProfileHistory(@Query("limit") limit: Int = 50): List<ProfileHistoryEntryDto>

    @GET("goals")
    suspend fun getGoals(): List<GoalDto>

    @PUT("goals")
    suspend fun upsertGoal(@Body req: GoalRequest): GoalDto

    @GET("badges")
    suspend fun getBadges(): List<BadgeDto>

    @POST("badges/{key}")
    suspend fun awardBadge(@Path("key") key: String): BadgeDto

    @DELETE("account")
    suspend fun deleteAccount(): Response<Unit>
}
