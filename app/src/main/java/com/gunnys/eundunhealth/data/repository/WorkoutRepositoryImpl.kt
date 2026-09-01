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
import com.gunnys.eundunhealth.data.remote.exercisedb.ExerciseDto
import com.gunnys.eundunhealth.data.remote.exercisedb.toDomain
import com.gunnys.eundunhealth.data.remote.util.bodyOrThrow
import com.gunnys.eundunhealth.domain.model.DayPlan
import com.gunnys.eundunhealth.domain.model.Exercise
import com.gunnys.eundunhealth.domain.model.ExerciseType
import com.gunnys.eundunhealth.domain.model.Statistics
import com.gunnys.eundunhealth.domain.model.UserProfile
import com.gunnys.eundunhealth.domain.model.WeeklyPlan
import com.gunnys.eundunhealth.domain.model.WeeklyRate
import com.gunnys.eundunhealth.domain.model.reportToSentry
import com.gunnys.eundunhealth.domain.model.toAppError
import com.gunnys.eundunhealth.domain.repository.AuthRepository
import com.gunnys.eundunhealth.domain.repository.WorkoutRepository
import com.gunnys.eundunhealth.domain.usecase.WeeklyPlanGenerator
import io.sentry.Sentry
import retrofit2.HttpException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class WorkoutRepositoryImpl @Inject constructor(
    private val api: WeeklyPlanApi,
    private val exerciseDb: ExerciseDbDataSource,
    private val weeklyPlanDao: WeeklyPlanDao,
    private val gson: Gson,
    private val authRepo: AuthRepository,
) : WorkoutRepository {

    // 주 시작(월요일)은 KST 고정 — 디바이스 타임존과 무관하게 plan/통계 key(weekStart)가 흔들리지 않도록.
    private fun currentWeekStart(): LocalDate = LocalDate.now(ZoneId.of("Asia/Seoul")).with(DayOfWeek.MONDAY)

    // 광역 catch 는 의도다 — 원격 조회가 *어떤* 이유로 실패하든(IO·HTTP·JSON 파싱) 캐시로
    // 폴백해야 하고, 예외 종류를 좁히면 새 실패 유형이 생길 때 조용히 폴백을 잃는다.
    // 삼키지 않는다: 캐시가 없으면 원래 예외를 그대로 rethrow 한다(아래).
    @Suppress("TooGenericExceptionCaught")
    override suspend fun getCurrentWeekPlan(): Result<WeeklyPlan?> = runCatching {
        val weekStart = currentWeekStart()
        try {
            val response = api.getWeeklyPlan(weekStart.toString())
            if (response.code() == 404) return@runCatching null
            val dto = response.bodyOrThrow()
            // read 경로에서도 캐시를 갱신 → 오프라인 시 stale(미완료) plan 대신 최신 완료상태를 보인다(F3).
            weeklyPlanDao.insertPlan(WeeklyPlanEntity(dto.id, dto.userId, weekStart.toString(), dto.dayPlans))
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

    override suspend fun getExerciseById(exerciseId: String): Result<Exercise?> = runCatching {
        // 1) 로컬 캐시(현재 주) 우선 — 네트워크 없이 즉시. 운동 콘텐츠는 주중 불변이라 캐시로 충분하고,
        //    상세 진입마다 전체 주간계획을 재 GET 하던 비효율/오프라인 취약을 제거한다.
        val weekStart = currentWeekStart()
        val userId = authRepo.getCurrentUserId()
        val cached = userId?.let { weeklyPlanDao.getPlan(it, weekStart.toString()) }
            ?.let { parseDayPlans(it.dayPlansJson) }
            ?.flatMap { it.exercises }
            ?.find { it.id == exerciseId }
        if (cached != null) return@runCatching cached

        // 2) 캐시 미스 → 네트워크 폴백 (현재 주 계획)
        getCurrentWeekPlan().getOrNull()
            ?.days?.flatMap { it.exercises }?.find { it.id == exerciseId }
    }

    override suspend fun createWeeklyPlan(profile: UserProfile): Result<WeeklyPlan> = runCatching {
        val weekStart = currentWeekStart()
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

        // 2) 부위·카디오 운동 풀을 한 번씩만 fetch (이전 주 ID는 reorderExcludingPrevious 로 후순위 정렬)
        suspend fun pool(bodyPart: String, type: ExerciseType): List<Exercise> = runCatching {
            reorderExcludingPrevious(exerciseDb.getStrengthExercises(bodyPart, limit = 6), excludeIds, bodyPart) {
                it.toDomain(sets, reps, type)
            }
        }.getOrDefault(emptyList())

        val push = pool("chest", ExerciseType.STRENGTH) + pool("shoulders", ExerciseType.STRENGTH)
        val pull = pool("back", ExerciseType.STRENGTH) + pool("upper arms", ExerciseType.STRENGTH)
        val legs = pool("upper legs", ExerciseType.STRENGTH) + pool("lower legs", ExerciseType.STRENGTH)

        val cardioPool: List<Exercise> = runCatching {
            reorderExcludingPrevious(exerciseDb.getCardioExercises(limit = 10), excludeIds, "cardio") {
                it.toDomain(1, 30, ExerciseType.CARDIO)
            }
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

        // 모든 풀이 비어 운동 0개짜리 "껍데기" 계획이 나오면 저장하지 않고 실패시킨다 (ExerciseDB
        // 일시 장애 등). 빈 계획을 백엔드에 저장하면 그 주 내내 고착되므로, 차라리 에러로 표면화해
        // 사용자가 재시도(ExerciseDB 복구 후 정상 생성)하도록 한다. AppError.Unknown → Sentry 보고로
        // 빈 풀 빈도도 관측한다.
        if (days.none { it.exercises.isNotEmpty() }) {
            error("운동 목록을 불러오지 못했습니다. 네트워크 상태를 확인한 뒤 다시 시도해주세요.")
        }

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

    override suspend fun updateDayCompletion(
        planId: String,
        date: LocalDate,
        completed: Boolean,
        manual: Boolean,
    ): Result<Unit> = runCatching {
        val weekStart = date.with(DayOfWeek.MONDAY)
        val response = api.updateDayCompletion(
            CompletionRequest(
                weekStart = weekStart.toString(),
                date = date.toString(),
                completed = completed,
                manual = manual,
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
    }.getOrElse { e ->
        // 손상된 day_plans JSON 을 silent 하게 빈 계획으로 폴백하면 R8 keep 갭(INC-2026-06-15-25)과
        // 같은 무관측 데이터 손실이 된다 → 폴백 전에 Sentry 로 보고해 관측 가능하게 둔다.
        // (정상적으로 빈 "[]" 는 예외가 없어 여기 오지 않으므로 노이즈가 되지 않는다.)
        e.toAppError().reportToSentry()
        emptyList()
    }
}
