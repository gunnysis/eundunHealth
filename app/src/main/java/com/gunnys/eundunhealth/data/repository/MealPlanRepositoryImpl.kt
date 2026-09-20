package com.gunnys.eundunhealth.data.repository

import com.gunnys.eundunhealth.api.generated.api.MealsApi
import com.gunnys.eundunhealth.data.remote.util.bodyOrNull404
import com.gunnys.eundunhealth.domain.model.DailyMealPlan
import com.gunnys.eundunhealth.domain.model.MealInfo
import com.gunnys.eundunhealth.domain.model.WeeklyMealPlan
import com.gunnys.eundunhealth.domain.repository.MealPlanRepository
import javax.inject.Inject
import com.gunnys.eundunhealth.api.generated.model.DailyMealPlan as ApiDailyMealPlan
import com.gunnys.eundunhealth.api.generated.model.MealInfo as ApiMealInfo
import com.gunnys.eundunhealth.api.generated.model.WeeklyMealPlanResponse as ApiWeeklyMealPlanResponse

class MealPlanRepositoryImpl @Inject constructor(
    private val api: MealsApi,
) : MealPlanRepository {

    override suspend fun getCurrentPlan(): Result<WeeklyMealPlan?> = runCatching {
        val dto: ApiWeeklyMealPlanResponse = api.getCurrentMealPlanMealsCurrentGet().bodyOrNull404() ?: return@runCatching null

        WeeklyMealPlan(
            summary = dto.summary,
            weeklyPlan = dto.weeklyPlan.map { dailyDto: ApiDailyMealPlan ->
                DailyMealPlan(
                    day = dailyDto.day,
                    isRestDay = dailyDto.isRestDay,
                    meals = dailyDto.meals.map { mealDto: ApiMealInfo ->
                        MealInfo(
                            mealType = mealDto.mealType,
                            menuName = mealDto.menuName,
                            calories = mealDto.calories,
                            proteinG = mealDto.proteinG,
                            carbsG = mealDto.carbsG,
                            fatG = mealDto.fatG,
                        )
                    },
                )
            },
        )
    }

    override suspend fun generatePlan(): Result<WeeklyMealPlan> = runCatching {
        val response = api.generateMealPlanMealsGeneratePost()
        if (!response.isSuccessful) throw retrofit2.HttpException(response)

        val dto: ApiWeeklyMealPlanResponse = response.body() ?: error("Empty body")

        WeeklyMealPlan(
            summary = dto.summary,
            weeklyPlan = dto.weeklyPlan.map { dailyDto: ApiDailyMealPlan ->
                DailyMealPlan(
                    day = dailyDto.day,
                    isRestDay = dailyDto.isRestDay,
                    meals = dailyDto.meals.map { mealDto: ApiMealInfo ->
                        MealInfo(
                            mealType = mealDto.mealType,
                            menuName = mealDto.menuName,
                            calories = mealDto.calories,
                            proteinG = mealDto.proteinG,
                            carbsG = mealDto.carbsG,
                            fatG = mealDto.fatG,
                        )
                    },
                )
            },
        )
    }
}
