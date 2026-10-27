package com.gunnys.eundunhealth.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.gunnys.eundunhealth.api.generated.api.WeeklyPlanV2Api
import com.gunnys.eundunhealth.api.generated.model.CompletionRequest
import com.gunnys.eundunhealth.api.generated.model.WeeklyPlanResponse
import com.gunnys.eundunhealth.api.generated.model.WeeklyPlanV2Request
import com.gunnys.eundunhealth.data.local.dao.WeeklyPlanDao
import com.gunnys.eundunhealth.data.local.entity.WeeklyPlanEntity
import com.gunnys.eundunhealth.data.remote.api.dto.DayPlanJson
import com.gunnys.eundunhealth.data.remote.exercisedb.ExerciseDbDataSource
import com.gunnys.eundunhealth.data.remote.exercisedb.ExerciseDto
import com.gunnys.eundunhealth.data.remote.exercisedb.toDomain
import com.gunnys.eundunhealth.data.remote.util.bodyOrThrow
import com.gunnys.eundunhealth.data.translation.ExerciseTranslationProvider
import com.gunnys.eundunhealth.domain.model.CurrentWeekPlanRead
import com.gunnys.eundunhealth.domain.model.DayPlan
import com.gunnys.eundunhealth.domain.model.Exercise
import com.gunnys.eundunhealth.domain.model.ExerciseType
import com.gunnys.eundunhealth.domain.model.PlanSource
import com.gunnys.eundunhealth.domain.model.Statistics
import com.gunnys.eundunhealth.domain.model.UserProfile
import com.gunnys.eundunhealth.domain.model.WeeklyPlan
import com.gunnys.eundunhealth.domain.model.WeeklyPlanAlreadyExistsException
import com.gunnys.eundunhealth.domain.model.WeeklyRate
import com.gunnys.eundunhealth.domain.model.WorkoutPlanMode
import com.gunnys.eundunhealth.domain.model.toWorkoutPlanMode
import com.gunnys.eundunhealth.domain.repository.AuthRepository
import com.gunnys.eundunhealth.domain.repository.WorkoutRepository
import com.gunnys.eundunhealth.domain.usecase.HomeWeeklyPlanGenerator
import com.gunnys.eundunhealth.domain.usecase.WeeklyPlanGenerator
import io.sentry.Sentry
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import com.gunnys.eundunhealth.api.generated.model.WorkoutPlanMode as ApiWorkoutPlanMode

class WorkoutRepositoryImpl @Inject constructor(
    private val api: WeeklyPlanV2Api,
    private val exerciseDb: ExerciseDbDataSource,
    private val weeklyPlanDao: WeeklyPlanDao,
    private val gson: Gson,
    private val authRepo: AuthRepository,
    private val translationProvider: ExerciseTranslationProvider,
) : WorkoutRepository {

    // 주 시작(월요일)은 KST 고정 — 디바이스 타임존과 무관하게 plan/통계 key(weekStart)가 흔들리지 않도록.
    private fun currentWeekStart(): LocalDate = LocalDate.now(ZoneId.of("Asia/Seoul")).with(DayOfWeek.MONDAY)

    @Suppress("TooGenericExceptionCaught")
    override suspend fun loadCurrentWeekPlan(): CurrentWeekPlanRead {
        val weekStart = currentWeekStart()
        try {
            val response = api.getWeeklyPlanV2(weekStart.toString())
            if (response.code() == 404) {
                authRepo.getCurrentUserId()?.let { weeklyPlanDao.deleteByUserAndWeek(it, weekStart.toString()) }
                return CurrentWeekPlanRead.Missing
            }
            val dto = response.bodyOrThrow()
            val plan = runCatching { dto.toDomainStrict() }.getOrElse {
                return CurrentWeekPlanRead.Unreadable(PlanSource.REMOTE, it)
            }
            weeklyPlanDao.insertPlan(plan.toEntity(dto.dayPlans))
            return CurrentWeekPlanRead.Found(localizePlan(plan))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val userId = authRepo.getCurrentUserId()
            if (userId != null) {
                val cached = weeklyPlanDao.getPlan(userId, weekStart.toString())
                if (cached != null) {
                    return runCatching {
                        WeeklyPlan(
                            id = cached.id,
                            userId = cached.userId,
                            weekStart = LocalDate.parse(cached.weekStart),
                            days = parseDayPlansStrict(cached.dayPlansJson),
                            mode = cached.planMode.toWorkoutPlanMode(),
                        ).also(::validatePlan)
                    }.fold(
                        onSuccess = { CurrentWeekPlanRead.Found(localizePlan(it)) },
                        onFailure = { CurrentWeekPlanRead.Unreadable(PlanSource.CACHE, it) },
                    )
                }
            }
            return CurrentWeekPlanRead.Failed(e)
        }
    }

    override suspend fun getExerciseById(exerciseId: String): Result<Exercise?> = runSuspendCatching {
        // 1) 로컬 캐시(현재 주) 우선 — 네트워크 없이 즉시. 운동 콘텐츠는 주중 불변이라 캐시로 충분하고,
        //    상세 진입마다 전체 주간계획을 재 GET 하던 비효율/오프라인 취약을 제거한다.
        val weekStart = currentWeekStart()
        val userId = authRepo.getCurrentUserId()
        val cached = userId?.let { weeklyPlanDao.getPlan(it, weekStart.toString()) }
            ?.let { runCatching { parseDayPlansStrict(it.dayPlansJson) }.getOrNull() }
            ?.flatMap { it.exercises }
            ?.find { it.id == exerciseId }
        if (cached != null) return@runSuspendCatching translationProvider.localize(cached)

        // 2) 캐시 미스 → 네트워크 폴백 (현재 주 계획)
        (loadCurrentWeekPlan() as? CurrentWeekPlanRead.Found)
            ?.plan?.days?.flatMap { it.exercises }?.find { it.id == exerciseId }
    }

    override suspend fun createWeeklyPlan(profile: UserProfile, mode: WorkoutPlanMode): Result<WeeklyPlan> = runSuspendCatching {
        val weekStart = currentWeekStart()
        val days = when (mode) {
            WorkoutPlanMode.GYM -> generateGymPlan(profile, weekStart)
            WorkoutPlanMode.HOME -> HomeWeeklyPlanGenerator.generate(weekStart, profile.restDay)
        }
        validateDays(days, weekStart)

        val dayPlansJson = gson.toJson(days.map { DayPlanJson(it) })
        val response = api.createWeeklyPlanV2(
            WeeklyPlanV2Request(
                weekStart = weekStart.toString(),
                planMode = mode.toApi(),
                dayPlans = dayPlansJson,
            ),
        )
        if (response.code() == 409) throw WeeklyPlanAlreadyExistsException()
        val dto = response.bodyOrThrow()
        val savedPlan = dto.toDomainStrict()
        weeklyPlanDao.insertPlan(savedPlan.toEntity(dto.dayPlans))
        localizePlan(savedPlan)
    }

    override suspend fun deleteCurrentWeekPlan(): Result<Unit> = runSuspendCatching {
        val weekStart = currentWeekStart()
        api.deleteWeeklyPlanV2(weekStart.toString()).bodyOrThrow()
        authRepo.getCurrentUserId()?.let { weeklyPlanDao.deleteByUserAndWeek(it, weekStart.toString()) }
    }

    override suspend fun updateDayCompletion(
        planId: String,
        date: LocalDate,
        completed: Boolean,
        manual: Boolean,
    ): Result<Unit> = runSuspendCatching {
        val weekStart = date.with(DayOfWeek.MONDAY)
        val response = api.updateDayCompletionV2(
            CompletionRequest(
                weekStart = weekStart.toString(),
                date = date.toString(),
                completed = completed,
                manual = manual,
            ),
        )
        if (!response.isSuccessful) throw HttpException(response)
    }

    override suspend fun getHistory(page: Int, size: Int): Result<Pair<List<WeeklyPlan>, Int>> = runSuspendCatching {
        val response = api.getWeeklyPlanHistoryV2(page, size).bodyOrThrow()
        val plans = response.plans.map { localizePlan(it.toDomainStrict()) }
        plans to response.totalCount
    }

    override suspend fun getStatistics(weeks: Int): Result<Statistics> = runSuspendCatching {
        val dto = api.getStatisticsV2(weeks).bodyOrThrow()
        Statistics(
            weeklyRates = dto.weeklyRates.map {
                WeeklyRate(
                    weekStart = LocalDate.parse(it.weekStart),
                    completionRate = it.completionRate.toFloat(),
                    mode = it.planMode.value.toWorkoutPlanMode(),
                )
            },
            currentStreak = dto.currentStreak,
            longestStreak = dto.longestStreak,
        )
    }

    // 이전 주에 나온 운동(excludeIds)을 뒤로 미뤄 신선한 운동을 우선 배치한다(주간 다양성).
    // strength·cardio 3곳에 복붙돼 있던 fresh/seen 정렬을 단일화. raw 가 비면(ExerciseDB 부분 장애)
    // breadcrumb 로 부분 저하를 남겨, 한 부위만 실패해 thin 한 계획이 나가는 silent degradation 을 추적한다.
    private fun reorderExcludingPrevious(
        raw: List<ExerciseDto>,
        excludeIds: Set<String>,
        label: String,
        map: (ExerciseDto) -> Exercise,
    ): List<Exercise> {
        if (raw.isEmpty()) {
            Sentry.addBreadcrumb("exercisedb pool empty: $label")
        }
        val fresh = raw.filter { it.id !in excludeIds }
        val seen = raw.filter { it.id in excludeIds }
        return (fresh + seen).map(map)
    }

    private suspend fun generateGymPlan(profile: UserProfile, weekStart: LocalDate): List<DayPlan> {
        val (sets, reps) = exerciseDb.getSetsAndReps(profile.fitnessLevel)
        val excludeIds = runSuspendCatching {
            api.getPreviousWeeklyPlanV2(weekStart.toString()).body()?.dayPlans
                ?.let(::parseDayPlansStrict)?.flatMap { it.exercises }?.map { it.id }?.toSet().orEmpty()
        }.getOrDefault(emptySet())
        suspend fun pool(bodyPart: String, type: ExerciseType) = runSuspendCatching {
            reorderExcludingPrevious(exerciseDb.getStrengthExercises(bodyPart, 6), excludeIds, bodyPart) {
                it.toDomain(sets, reps, type)
            }
        }.getOrDefault(emptyList())
        val push = pool("chest", ExerciseType.STRENGTH) + pool("shoulders", ExerciseType.STRENGTH)
        val pull = pool("back", ExerciseType.STRENGTH) + pool("upper arms", ExerciseType.STRENGTH)
        val legs = pool("upper legs", ExerciseType.STRENGTH) + pool("lower legs", ExerciseType.STRENGTH)
        val cardio = runSuspendCatching {
            reorderExcludingPrevious(exerciseDb.getCardioExercises(10), excludeIds, "cardio") {
                it.toDomain(1, 30, ExerciseType.CARDIO)
            }
        }.getOrDefault(emptyList())
        return WeeklyPlanGenerator.generate(weekStart, profile.restDay, push, pull, legs, cardio)
    }

    private fun WeeklyPlanResponse.toDomainStrict(): WeeklyPlan = WeeklyPlan(
        id = id,
        userId = userId,
        weekStart = LocalDate.parse(weekStart),
        days = parseDayPlansStrict(dayPlans),
        mode = planMode.value.toWorkoutPlanMode(),
    ).also(::validatePlan)

    private fun parseDayPlansStrict(json: String): List<DayPlan> {
        val type = object : TypeToken<List<DayPlanJson>>() {}.type
        val dayJsons: List<DayPlanJson> = gson.fromJson(json, type)
        return dayJsons.map { it.toDayPlan() }
    }

    private fun validatePlan(plan: WeeklyPlan) = validateDays(plan.days, plan.weekStart)

    private fun validateDays(days: List<DayPlan>, weekStart: LocalDate) {
        require(days.size == 7) { "주간 계획은 정확히 7일이어야 합니다" }
        require(days.map { it.date } == (0L..6L).map(weekStart::plusDays)) { "계획 날짜 범위가 올바르지 않습니다" }
        require(days.filterNot { it.isRestDay }.all { it.exercises.isNotEmpty() }) { "운동일에 운동이 없습니다" }
    }

    private fun WeeklyPlan.toEntity(dayPlansJson: String) = WeeklyPlanEntity(
        id = id,
        userId = userId,
        weekStart = weekStart.toString(),
        planMode = mode.name.lowercase(),
        dayPlansJson = dayPlansJson,
    )

    private fun WorkoutPlanMode.toApi() = when (this) {
        WorkoutPlanMode.GYM -> ApiWorkoutPlanMode.gym
        WorkoutPlanMode.HOME -> ApiWorkoutPlanMode.home
    }

    private suspend fun localizePlan(plan: WeeklyPlan): WeeklyPlan = when (plan.mode) {
        WorkoutPlanMode.HOME -> plan
        WorkoutPlanMode.GYM -> translationProvider.localize(plan)
    }
}

/** Kotlin runCatching은 CancellationException도 실패 값으로 바꾸므로 suspend 경계에서는 취소를 명시적으로 보존한다. */
@Suppress("TooGenericExceptionCaught") // Repository Result 계약은 취소 외 모든 작업 실패를 Result.failure로 반환한다.
private suspend fun <T> runSuspendCatching(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
