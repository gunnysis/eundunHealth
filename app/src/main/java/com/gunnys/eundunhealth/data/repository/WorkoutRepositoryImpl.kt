package com.gunnys.eundunhealth.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.gunnys.eundunhealth.data.local.dao.WeeklyPlanDao
import com.gunnys.eundunhealth.data.local.entity.WeeklyPlanEntity
import com.gunnys.eundunhealth.data.remote.api.EundunApi
import com.gunnys.eundunhealth.data.remote.api.dto.CreateWeeklyPlanRequest
import com.gunnys.eundunhealth.data.remote.api.dto.DayPlanJson
import com.gunnys.eundunhealth.data.remote.api.dto.UpdateDayCompletionRequest
import com.gunnys.eundunhealth.data.remote.exercisedb.ExerciseDbDataSource
import com.gunnys.eundunhealth.data.remote.exercisedb.toDomain
import com.gunnys.eundunhealth.domain.model.DayPlan
import com.gunnys.eundunhealth.domain.model.Exercise
import com.gunnys.eundunhealth.domain.model.ExerciseType
import com.gunnys.eundunhealth.domain.model.UserProfile
import com.gunnys.eundunhealth.domain.model.WeeklyPlan
import com.gunnys.eundunhealth.domain.repository.WorkoutRepository
import io.sentry.Sentry
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import kotlin.random.Random

class WorkoutRepositoryImpl @Inject constructor(
    private val api: EundunApi,
    private val exerciseDb: ExerciseDbDataSource,
    private val weeklyPlanDao: WeeklyPlanDao,
    private val gson: Gson
) : WorkoutRepository {

    override suspend fun getCurrentWeekPlan(): Result<WeeklyPlan?> = runCatching {
        val weekStart = LocalDate.now().with(DayOfWeek.MONDAY)
        try {
            val dto = api.getWeeklyPlan(weekStart.toString())
            if (dto != null) {
                return@runCatching WeeklyPlan(dto.id, dto.userId, LocalDate.parse(dto.weekStart), parseDayPlans(dto.dayPlans))
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            val cached = weeklyPlanDao.getPlan(weekStart.toString())
            if (cached != null) {
                return@runCatching WeeklyPlan(cached.id, cached.userId, LocalDate.parse(cached.weekStart), parseDayPlans(cached.dayPlansJson))
            }
        }
        null
    }

    override suspend fun createWeeklyPlan(profile: UserProfile): Result<WeeklyPlan> = runCatching {
        val weekStart = LocalDate.now().with(DayOfWeek.MONDAY)
        val (sets, reps) = exerciseDb.getSetsAndReps(profile.fitnessLevel)

        val strengthBodyParts = listOf("chest", "back", "upper legs", "shoulders", "upper arms")
        val strengthExercises = mutableListOf<Exercise>()
        for (bp in strengthBodyParts) {
            try {
                val exercises = exerciseDb.getStrengthExercises(bp, limit = 2)
                strengthExercises.addAll(exercises.map { it.toDomain(sets, reps, ExerciseType.STRENGTH) })
            } catch (e: Exception) {
                Sentry.captureException(e)
            }
        }

        val cardioExercises = try {
            exerciseDb.getCardioExercises(limit = 3).map { it.toDomain(1, 30, ExerciseType.CARDIO) }
        } catch (e: Exception) {
            Sentry.captureException(e)
            emptyList()
        }

        val seed = Random(weekStart.toEpochDay())
        val days = (0L..6L).map { dayOffset ->
            val date = weekStart.plusDays(dayOffset)
            when (date.dayOfWeek) {
                DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY ->
                    DayPlan(date, strengthExercises.shuffled(seed).take(4), isRestDay = false, isCompleted = false)
                DayOfWeek.TUESDAY, DayOfWeek.THURSDAY ->
                    DayPlan(date, cardioExercises.shuffled(seed).take(2), isRestDay = false, isCompleted = false)
                DayOfWeek.SATURDAY -> {
                    val mixed = strengthExercises.shuffled(seed).take(2) + cardioExercises.shuffled(seed).take(1)
                    DayPlan(date, mixed, isRestDay = false, isCompleted = false)
                }
                else -> DayPlan(date, emptyList(), isRestDay = true, isCompleted = false)
            }
        }

        val plan = WeeklyPlan(id = "", userId = profile.userId, weekStart = weekStart, days = days)
        val dayPlansJson = gson.toJson(days.map { DayPlanJson(it) })
        val response = api.createWeeklyPlan(CreateWeeklyPlanRequest(weekStart.toString(), dayPlansJson))
        val savedPlan = plan.copy(id = response.id)

        weeklyPlanDao.insertPlan(
            WeeklyPlanEntity(savedPlan.id, savedPlan.userId, weekStart.toString(), dayPlansJson)
        )
        savedPlan
    }

    override suspend fun savePlanToServer(plan: WeeklyPlan): Result<Unit> = runCatching {
        val dayPlansJson = gson.toJson(plan.days.map { DayPlanJson(it) })
        api.createWeeklyPlan(CreateWeeklyPlanRequest(plan.weekStart.toString(), dayPlansJson))
    }

    override suspend fun updateDayCompletion(planId: String, date: LocalDate, completed: Boolean): Result<Unit> = runCatching {
        api.updateDayCompletion(UpdateDayCompletionRequest(date.toString(), completed))
    }

    override suspend fun getHistory(page: Int, size: Int): Result<Pair<List<WeeklyPlan>, Int>> = runCatching {
        val response = api.getWeeklyPlanHistory(page, size)
        val plans = response.plans.map { dto ->
            WeeklyPlan(dto.id, dto.userId, LocalDate.parse(dto.weekStart), parseDayPlans(dto.dayPlans))
        }
        plans to response.totalCount
    }

    private fun parseDayPlans(json: String): List<DayPlan> {
        return try {
            val type = object : TypeToken<List<DayPlanJson>>() {}.type
            val dayJsons: List<DayPlanJson> = gson.fromJson(json, type)
            dayJsons.map { it.toDayPlan() }
        } catch (e: Exception) {
            Sentry.captureException(e)
            emptyList()
        }
    }
}
