package com.gunnys.eundunhealth.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.gunnys.eundunhealth.api.generated.api.WeeklyPlanApi
import com.gunnys.eundunhealth.api.generated.model.CompletionRequest
import com.gunnys.eundunhealth.api.generated.model.WeeklyPlanRequest
import com.gunnys.eundunhealth.api.generated.model.WeeklyPlanResponse
import com.gunnys.eundunhealth.data.local.dao.WeeklyPlanDao
import com.gunnys.eundunhealth.data.local.entity.WeeklyPlanEntity
import com.gunnys.eundunhealth.data.remote.api.dto.DayPlanJson
import com.gunnys.eundunhealth.data.remote.exercisedb.ExerciseDbDataSource
import com.gunnys.eundunhealth.data.remote.exercisedb.toDomain
import com.gunnys.eundunhealth.data.remote.util.bodyOrThrow
import com.gunnys.eundunhealth.domain.model.DayPlan
import com.gunnys.eundunhealth.domain.model.Exercise
import com.gunnys.eundunhealth.domain.model.ExerciseType
import com.gunnys.eundunhealth.domain.model.Statistics
import com.gunnys.eundunhealth.domain.model.UserProfile
import com.gunnys.eundunhealth.domain.model.WeeklyPlan
import com.gunnys.eundunhealth.domain.model.WeeklyRate
import com.gunnys.eundunhealth.domain.repository.AuthRepository
import com.gunnys.eundunhealth.domain.repository.WorkoutRepository
import com.gunnys.eundunhealth.domain.usecase.WeeklyPlanGenerator
import retrofit2.HttpException
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

class WorkoutRepositoryImpl @Inject constructor(
    private val api: WeeklyPlanApi,
    private val exerciseDb: ExerciseDbDataSource,
    private val weeklyPlanDao: WeeklyPlanDao,
    private val gson: Gson,
    private val authRepo: AuthRepository,
) : WorkoutRepository {

    override suspend fun getCurrentWeekPlan(): Result<WeeklyPlan?> = runCatching {
        val weekStart = LocalDate.now().with(DayOfWeek.MONDAY)
        try {
            val response = api.getWeeklyPlan(weekStart.toString())
            if (response.code() == 404) return@runCatching null
            val dto = response.bodyOrThrow()
            return@runCatching dto.toDomain()
        } catch (e: Exception) {
            // 네트워크 실패 → 캐시 폴백. Sentry는 ViewModel.reportToSentry()가 처리
            val userId = authRepo.getCurrentUserId()
            if (userId != null) {
                val cached = weeklyPlanDao.getPlan(userId, weekStart.toString())
                if (cached != null) {
                    return@runCatching WeeklyPlan(
                        id = cached.id,
                        userId = cached.userId,
                        weekStart = LocalDate.parse(cached.weekStart),
                        days = parseDayPlans(cached.dayPlansJson),
                    )
                }
            }
            throw e
        }
    }

    override suspend fun createWeeklyPlan(profile: UserProfile): Result<WeeklyPlan> = runCatching {
        val weekStart = LocalDate.now().with(DayOfWeek.MONDAY)
        val (sets, reps) = exerciseDb.getSetsAndReps(profile.fitnessLevel)

        // 1) 이전 주 plan에서 운동 ID를 추출 (실패해도 빈 집합으로 폴백)
        val excludeIds: Set<String> = runCatching {
            val prevResp = api.getPreviousWeeklyPlan(weekStart.toString())
            // 404나 null body → 빈 집합
            val prev = prevResp.body()
            prev?.dayPlans?.let { parseDayPlans(it) }
                ?.flatMap { it.exercises }
                ?.map { it.id }
                ?.toSet()
                .orEmpty()
        }.getOrDefault(emptySet())

        // 2) 부위·카디오 운동 풀을 한 번씩만 fetch (이전 주 ID는 후순위 정렬)
        suspend fun pool(bodyPart: String, type: ExerciseType): List<Exercise> = runCatching {
            val raw = exerciseDb.getStrengthExercises(bodyPart, limit = 6)
            val fresh = raw.filter { it.id !in excludeIds }
            val seen = raw.filter { it.id in excludeIds }
            (fresh + seen).map { it.toDomain(sets, reps, type) }
        }.getOrDefault(emptyList())

        val push = pool("chest", ExerciseType.STRENGTH) + pool("shoulders", ExerciseType.STRENGTH)
        val pull = pool("back", ExerciseType.STRENGTH) + pool("upper arms", ExerciseType.STRENGTH)
        val legs = pool("upper legs", ExerciseType.STRENGTH) + pool("lower legs", ExerciseType.STRENGTH)

        val cardioPool: List<Exercise> = runCatching {
            val raw = exerciseDb.getCardioExercises(limit = 10)
            val fresh = raw.filter { it.id !in excludeIds }
            val seen = raw.filter { it.id in excludeIds }
            (fresh + seen).map { it.toDomain(1, 30, ExerciseType.CARDIO) }
        }.getOrDefault(emptyList())

        // 3) 요일별 배치 — 순수 generator 위임(결정론적, 단위테스트는 WeeklyPlanGeneratorTest)
        val days = WeeklyPlanGenerator.generate(
            weekStart = weekStart,
            restDay = profile.restDay,
            push = push,
            pull = pull,
            legs = legs,
            cardio = cardioPool,
        )

        val dayPlansJson = gson.toJson(days.map { DayPlanJson(it) })
        val response = api.createWeeklyPlan(
            WeeklyPlanRequest(weekStart = weekStart.toString(), dayPlans = dayPlansJson),
        ).bodyOrThrow()
        val savedPlan = WeeklyPlan(
            id = response.id,
            userId = response.userId,
            weekStart = LocalDate.parse(response.weekStart),
            days = days,
        )

        weeklyPlanDao.insertPlan(
            WeeklyPlanEntity(savedPlan.id, savedPlan.userId, weekStart.toString(), dayPlansJson),
        )
        savedPlan
    }

    override suspend fun updateDayCompletion(planId: String, date: LocalDate, completed: Boolean): Result<Unit> = runCatching {
        val weekStart = date.with(DayOfWeek.MONDAY)
        val response = api.updateDayCompletion(
            CompletionRequest(
                weekStart = weekStart.toString(),
                date = date.toString(),
                completed = completed,
            ),
        )
        if (!response.isSuccessful) throw HttpException(response)
    }

    override suspend fun getHistory(page: Int, size: Int): Result<Pair<List<WeeklyPlan>, Int>> = runCatching {
        val response = api.getWeeklyPlanHistory(page, size).bodyOrThrow()
        val plans = response.plans.map { it.toDomain() }
        plans to response.totalCount
    }

    override suspend fun getStatistics(weeks: Int): Result<Statistics> = runCatching {
        val dto = api.getStatistics(weeks).bodyOrThrow()
        Statistics(
            weeklyRates = dto.weeklyRates.map {
                WeeklyRate(weekStart = LocalDate.parse(it.weekStart), completionRate = it.completionRate.toFloat())
            },
            currentStreak = dto.currentStreak,
            longestStreak = dto.longestStreak,
        )
    }

    private fun WeeklyPlanResponse.toDomain(): WeeklyPlan = WeeklyPlan(
        id = id,
        userId = userId,
        weekStart = LocalDate.parse(weekStart),
        days = parseDayPlans(dayPlans),
    )

    private fun parseDayPlans(json: String): List<DayPlan> = runCatching {
        val type = object : TypeToken<List<DayPlanJson>>() {}.type
        val dayJsons: List<DayPlanJson> = gson.fromJson(json, type)
        dayJsons.map { it.toDayPlan() }
    }.getOrDefault(emptyList())
}
