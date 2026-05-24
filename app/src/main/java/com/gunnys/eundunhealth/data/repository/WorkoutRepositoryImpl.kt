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
import com.gunnys.eundunhealth.domain.model.Statistics
import com.gunnys.eundunhealth.domain.model.UserProfile
import com.gunnys.eundunhealth.domain.model.WeeklyPlan
import com.gunnys.eundunhealth.domain.model.WeeklyRate
import com.gunnys.eundunhealth.domain.repository.AuthRepository
import com.gunnys.eundunhealth.domain.repository.WorkoutRepository
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import kotlin.random.Random

class WorkoutRepositoryImpl @Inject constructor(
    private val api: EundunApi,
    private val exerciseDb: ExerciseDbDataSource,
    private val weeklyPlanDao: WeeklyPlanDao,
    private val gson: Gson,
    private val authRepo: AuthRepository,
) : WorkoutRepository {

    override suspend fun getCurrentWeekPlan(): Result<WeeklyPlan?> = runCatching {
        val weekStart = LocalDate.now().with(DayOfWeek.MONDAY)
        try {
            val dto = api.getWeeklyPlan(weekStart.toString())
            if (dto != null) {
                return@runCatching WeeklyPlan(dto.id, dto.userId, LocalDate.parse(dto.weekStart), parseDayPlans(dto.dayPlans))
            }
        } catch (e: Exception) {
            // 네트워크 실패 → 캐시 폴백. Sentry는 ViewModel.reportToSentry()가 처리
            val userId = authRepo.getCurrentUserId()
            if (userId != null) {
                val cached = weeklyPlanDao.getPlan(userId, weekStart.toString())
                if (cached != null) {
                    return@runCatching WeeklyPlan(cached.id, cached.userId, LocalDate.parse(cached.weekStart), parseDayPlans(cached.dayPlansJson))
                }
            }
            // 캐시도 없으면 원래 예외를 그대로 전파
            throw e
        }
        null
    }

    override suspend fun createWeeklyPlan(profile: UserProfile): Result<WeeklyPlan> = runCatching {
        val weekStart = LocalDate.now().with(DayOfWeek.MONDAY)
        val (sets, reps) = exerciseDb.getSetsAndReps(profile.fitnessLevel)

        // 1) 이전 주 plan에서 운동 ID를 추출 (실패해도 빈 집합으로 폴백)
        val excludeIds: Set<String> = runCatching {
            val prev = api.getPreviousWeeklyPlan(weekStart.toString())
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

        // 3) 요일별 배치 — 결정론적 셔플(weekStart seed)로 같은 주는 같은 결과
        val seed = Random(weekStart.toEpochDay())
        val pushShuffled = push.shuffled(seed)
        val pullShuffled = pull.shuffled(seed)
        val legsShuffled = legs.shuffled(seed)
        val cardioShuffled = cardioPool.shuffled(seed)
        // 화/목/토 카디오 자리에 사용. 자리별로 겹치지 않게 슬라이스.
        val tueCardio = cardioShuffled.take(2)
        val thuCardio = cardioShuffled.drop(2).take(2)
        val satCardio = cardioShuffled.drop(4).take(1)

        val days = (0L..6L).map { offset ->
            val date = weekStart.plusDays(offset)
            when (date.dayOfWeek) {
                DayOfWeek.MONDAY -> // PUSH 4종
                    DayPlan(date, pushShuffled.take(4), isRestDay = false, isCompleted = false)
                DayOfWeek.TUESDAY -> // 유산소 2종
                    DayPlan(date, tueCardio, isRestDay = false, isCompleted = false)
                DayOfWeek.WEDNESDAY -> // PULL 4종
                    DayPlan(date, pullShuffled.take(4), isRestDay = false, isCompleted = false)
                DayOfWeek.THURSDAY -> // 유산소 2종
                    DayPlan(date, thuCardio, isRestDay = false, isCompleted = false)
                DayOfWeek.FRIDAY -> // LEGS 4종
                    DayPlan(date, legsShuffled.take(4), isRestDay = false, isCompleted = false)
                DayOfWeek.SATURDAY -> { // 혼합 3종 (PUSH/PULL/LEGS에서 strength 2 + cardio 1)
                    val mixedStrength = (pushShuffled + pullShuffled + legsShuffled).shuffled(seed).take(2)
                    DayPlan(date, mixedStrength + satCardio, isRestDay = false, isCompleted = false)
                }
                DayOfWeek.SUNDAY -> // 휴식 (v0.3에서 profile.restDay로 동적 변경 예정)
                    DayPlan(date, emptyList(), isRestDay = true, isCompleted = false)
            }
        }

        val plan = WeeklyPlan(id = "", userId = profile.userId, weekStart = weekStart, days = days)
        val dayPlansJson = gson.toJson(days.map { DayPlanJson(it) })
        val response = api.createWeeklyPlan(CreateWeeklyPlanRequest(weekStart.toString(), dayPlansJson))
        val savedPlan = plan.copy(id = response.id)

        weeklyPlanDao.insertPlan(
            WeeklyPlanEntity(savedPlan.id, savedPlan.userId, weekStart.toString(), dayPlansJson),
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

    override suspend fun getStatistics(weeks: Int): Result<Statistics> = runCatching {
        val dto = api.getStatistics(weeks)
        Statistics(
            weeklyRates = dto.weeklyRates.map {
                WeeklyRate(weekStart = LocalDate.parse(it.weekStart), completionRate = it.completionRate)
            },
            currentStreak = dto.currentStreak,
            longestStreak = dto.longestStreak,
        )
    }

    private fun parseDayPlans(json: String): List<DayPlan> {
        // 파싱 실패는 빈 리스트로 폴백 — 도메인 모델은 비어있어도 안전하게 표시 가능.
        // Unknown 예외로 Sentry 전송이 필요하면 호출 ViewModel에서 reportToSentry() 사용.
        return runCatching {
            val type = object : TypeToken<List<DayPlanJson>>() {}.type
            val dayJsons: List<DayPlanJson> = gson.fromJson(json, type)
            dayJsons.map { it.toDayPlan() }
        }.getOrDefault(emptyList())
    }
}
