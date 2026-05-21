package com.gunnys.eundunhealth.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.gunnys.eundunhealth.data.local.dao.WeeklyPlanDao
import com.gunnys.eundunhealth.data.local.entity.WeeklyPlanEntity
import com.gunnys.eundunhealth.data.remote.api.EundunApi
import com.gunnys.eundunhealth.data.remote.api.dto.CreateWeeklyPlanRequest
import com.gunnys.eundunhealth.data.remote.exercisedb.ExerciseDbDataSource
import com.gunnys.eundunhealth.data.remote.exercisedb.toDomain
import com.gunnys.eundunhealth.domain.model.DayPlan
import com.gunnys.eundunhealth.domain.model.Exercise
import com.gunnys.eundunhealth.domain.model.ExerciseType
import com.gunnys.eundunhealth.domain.model.UserProfile
import com.gunnys.eundunhealth.domain.model.WeeklyPlan
import com.gunnys.eundunhealth.domain.repository.WorkoutRepository
import android.util.Log
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
        // Try server first
        try {
            val dto = api.getWeeklyPlan(weekStart.toString())
            if (dto != null) {
                val days = parseDayPlans(dto.dayPlans)
                return@runCatching WeeklyPlan(dto.id, dto.userId, LocalDate.parse(dto.weekStart), days)
            }
        } catch (e: Exception) {
            Log.w("WorkoutRepo", "Server fetch failed, trying cache", e)
            // Try local cache
            val cached = weeklyPlanDao.getPlan("", weekStart.toString())
            if (cached != null) {
                val days = parseDayPlans(cached.dayPlansJson)
                return@runCatching WeeklyPlan(cached.id, cached.userId, LocalDate.parse(cached.weekStart), days)
            }
        }
        null
    }

    override suspend fun createWeeklyPlan(profile: UserProfile): Result<WeeklyPlan> = runCatching {
        val weekStart = LocalDate.now().with(DayOfWeek.MONDAY)
        val (sets, reps) = exerciseDb.getSetsAndReps(profile.fitnessLevel)

        // Fetch exercises from ExerciseDB
        val strengthBodyParts = listOf("chest", "back", "upper legs", "shoulders", "upper arms")
        val strengthExercises = mutableListOf<Exercise>()
        for (bp in strengthBodyParts) {
            try {
                val exercises = exerciseDb.getStrengthExercises(bp, limit = 2)
                strengthExercises.addAll(exercises.map { it.toDomain(sets, reps, ExerciseType.STRENGTH) })
            } catch (e: Exception) {
                Log.w("WorkoutRepo", "Failed to fetch exercises for $bp", e)
            }
        }

        val cardioExercises = try {
            exerciseDb.getCardioExercises(limit = 3).map { it.toDomain(1, 30, ExerciseType.CARDIO) }
        } catch (e: Exception) {
            Log.w("WorkoutRepo", "Failed to fetch cardio exercises", e)
            emptyList()
        }

        // Build 7-day plan (deterministic shuffle per week)
        val seed = Random(weekStart.toEpochDay())
        val days = (0L..6L).map { dayOffset ->
            val date = weekStart.plusDays(dayOffset)
            val dayOfWeek = date.dayOfWeek
            when (dayOfWeek) {
                DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY -> {
                    val dayExercises = strengthExercises.shuffled(seed).take(4)
                    DayPlan(date, dayExercises, isRestDay = false, isCompleted = false)
                }
                DayOfWeek.TUESDAY, DayOfWeek.THURSDAY -> {
                    DayPlan(date, cardioExercises.shuffled(seed).take(2), isRestDay = false, isCompleted = false)
                }
                DayOfWeek.SATURDAY -> {
                    val mixed = (strengthExercises.shuffled(seed).take(2) + cardioExercises.shuffled(seed).take(1))
                    DayPlan(date, mixed, isRestDay = false, isCompleted = false)
                }
                else -> DayPlan(date, emptyList(), isRestDay = true, isCompleted = false) // Sunday
            }
        }

        val plan = WeeklyPlan(id = "", userId = profile.userId, weekStart = weekStart, days = days)

        // Save to server
        val dayPlansJson = gson.toJson(days.map { DayPlanJson(it) })
        val response = api.createWeeklyPlan(CreateWeeklyPlanRequest(weekStart.toString(), dayPlansJson))
        val savedPlan = plan.copy(id = response.id)

        // Cache locally
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
        api.updateDayCompletion(
            com.gunnys.eundunhealth.data.remote.api.dto.UpdateDayCompletionRequest(date.toString(), completed)
        )
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
            Log.e("WorkoutRepo", "Failed to parse day plans JSON", e)
            emptyList()
        }
    }
}

// JSON serialization helper for DayPlan
data class DayPlanJson(
    val date: String,
    val exercises: List<ExerciseJson>,
    val isRestDay: Boolean,
    val isCompleted: Boolean
) {
    constructor(dayPlan: DayPlan) : this(
        date = dayPlan.date.toString(),
        exercises = dayPlan.exercises.map { ExerciseJson(it) },
        isRestDay = dayPlan.isRestDay,
        isCompleted = dayPlan.isCompleted
    )

    fun toDayPlan() = DayPlan(
        date = LocalDate.parse(date),
        exercises = exercises.map { it.toExercise() },
        isRestDay = isRestDay,
        isCompleted = isCompleted
    )
}

data class ExerciseJson(
    val id: String,
    val name: String,
    val bodyPart: String,
    val equipment: String,
    val gifUrl: String,
    val instructions: List<String>,
    val sets: Int,
    val reps: Int,
    val type: String
) {
    constructor(exercise: Exercise) : this(
        id = exercise.id, name = exercise.name, bodyPart = exercise.bodyPart,
        equipment = exercise.equipment, gifUrl = exercise.gifUrl,
        instructions = exercise.instructions, sets = exercise.sets,
        reps = exercise.reps, type = exercise.type.name
    )

    fun toExercise() = Exercise(
        id = id, name = name, bodyPart = bodyPart, equipment = equipment,
        gifUrl = gifUrl, instructions = instructions, sets = sets, reps = reps,
        type = ExerciseType.valueOf(type)
    )
}
