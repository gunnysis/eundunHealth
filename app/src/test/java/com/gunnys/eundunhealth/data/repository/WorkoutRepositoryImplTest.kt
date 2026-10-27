package com.gunnys.eundunhealth.data.repository

import com.google.gson.Gson
import com.gunnys.eundunhealth.api.generated.api.WeeklyPlanV2Api
import com.gunnys.eundunhealth.api.generated.model.StatisticsResponse
import com.gunnys.eundunhealth.api.generated.model.WeeklyPlanHistoryResponse
import com.gunnys.eundunhealth.api.generated.model.WeeklyPlanResponse
import com.gunnys.eundunhealth.api.generated.model.WeeklyPlanV2Request
import com.gunnys.eundunhealth.api.generated.model.WeeklyRateDto
import com.gunnys.eundunhealth.data.local.dao.WeeklyPlanDao
import com.gunnys.eundunhealth.data.local.entity.WeeklyPlanEntity
import com.gunnys.eundunhealth.data.remote.api.dto.DayPlanJson
import com.gunnys.eundunhealth.data.remote.exercisedb.ExerciseDbDataSource
import com.gunnys.eundunhealth.data.remote.exercisedb.ExerciseDto
import com.gunnys.eundunhealth.data.translation.ExerciseTranslationProvider
import com.gunnys.eundunhealth.domain.model.CurrentWeekPlanRead
import com.gunnys.eundunhealth.domain.model.DayPlan
import com.gunnys.eundunhealth.domain.model.Exercise
import com.gunnys.eundunhealth.domain.model.ExerciseType
import com.gunnys.eundunhealth.domain.model.FitnessLevel
import com.gunnys.eundunhealth.domain.model.Gender
import com.gunnys.eundunhealth.domain.model.PlanSource
import com.gunnys.eundunhealth.domain.model.UserProfile
import com.gunnys.eundunhealth.domain.model.WeeklyPlan
import com.gunnys.eundunhealth.domain.model.WeeklyPlanAlreadyExistsException
import com.gunnys.eundunhealth.domain.model.WorkoutPlanMode
import com.gunnys.eundunhealth.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import com.gunnys.eundunhealth.api.generated.model.WorkoutPlanMode as ApiWorkoutPlanMode

class WorkoutRepositoryImplTest {

    private lateinit var api: WeeklyPlanV2Api
    private lateinit var exerciseDb: ExerciseDbDataSource
    private lateinit var dao: WeeklyPlanDao
    private lateinit var authRepo: AuthRepository
    private lateinit var translationProvider: ExerciseTranslationProvider
    private lateinit var repo: WorkoutRepositoryImpl

    private val gson = Gson()
    private val currentWeekStart: LocalDate
        get() = LocalDate.now(ZoneId.of("Asia/Seoul")).with(DayOfWeek.MONDAY)

    private fun validDayPlansJson(
        weekStart: LocalDate = currentWeekStart,
        exerciseName: String = "push-up",
    ): String {
        val ex = Exercise("e1", exerciseName, "chest", "bodyweight", "", listOf("Step 1"), 3, 10, ExerciseType.STRENGTH)
        val days = (0L..6L).map { offset ->
            val date = weekStart.plusDays(offset)
            val isRest = offset == 6L
            DayPlanJson(
                DayPlan(
                    date = date,
                    exercises = if (isRest) emptyList() else listOf(ex),
                    isRestDay = isRest,
                    isCompleted = false,
                ),
            )
        }
        return gson.toJson(days)
    }

    @Before
    fun setup() {
        api = mockk(relaxed = true)
        exerciseDb = mockk()
        dao = mockk(relaxed = true)
        authRepo = mockk(relaxed = true)
        translationProvider = mockk()
        coEvery { translationProvider.localize(any<Exercise>()) } answers { firstArg() }
        coEvery { translationProvider.localize(any<WeeklyPlan>()) } answers { firstArg() }
        repo = WorkoutRepositoryImpl(api, exerciseDb, dao, gson, authRepo, translationProvider)
    }

    @Test
    fun `createWeeklyPlan GYM 은 모든 운동 풀이 비면 빈 계획을 저장하지 않고 실패한다`() = runTest {
        every { exerciseDb.getSetsAndReps(any()) } returns (3 to 10)
        coEvery { exerciseDb.getStrengthExercises(any(), any()) } returns emptyList()
        coEvery { exerciseDb.getCardioExercises(any()) } returns emptyList()
        coEvery { api.getPreviousWeeklyPlanV2(any()) } returns
            mockk<Response<WeeklyPlanResponse>> { every { body() } returns null }

        val profile = mockk<UserProfile>(relaxed = true)
        every { profile.fitnessLevel } returns FitnessLevel.BEGINNER
        every { profile.restDay } returns 7

        val result = repo.createWeeklyPlan(profile, WorkoutPlanMode.GYM)

        assertTrue("빈 풀이면 실패해야 한다", result.isFailure)
        coVerify(exactly = 0) { api.createWeeklyPlanV2(any()) }
    }

    @Test
    fun `createWeeklyPlan HOME 은 외부 ExerciseDB 호출 없이 7일 무장비 계획을 생성하고 저장한다`() = runTest {
        val requestSlot = slot<WeeklyPlanV2Request>()
        coEvery { api.createWeeklyPlanV2(capture(requestSlot)) } answers {
            val req = requestSlot.captured
            Response.success(
                201,
                WeeklyPlanResponse(
                    dayPlans = req.dayPlans,
                    id = "home-plan-1",
                    planMode = ApiWorkoutPlanMode.home,
                    userId = "u",
                    weekStart = req.weekStart,
                    createdAt = null,
                ),
            )
        }
        val profile = UserProfile("u", 170f, 65f, Gender.UNSPECIFIED, null, null, restDay = 7)

        val result = repo.createWeeklyPlan(profile, WorkoutPlanMode.HOME).getOrThrow()

        assertEquals(WorkoutPlanMode.HOME, result.mode)
        assertEquals(7, result.days.size)
        assertTrue(result.hasExercises)
        coVerify(exactly = 0) { exerciseDb.getStrengthExercises(any(), any()) }
        coVerify(exactly = 0) { exerciseDb.getCardioExercises(any()) }
        coVerify(exactly = 0) { translationProvider.localize(any<WeeklyPlan>()) }
    }

    @Test
    fun `createWeeklyPlan 은 409 응답 시 WeeklyPlanAlreadyExistsException 으로 실패한다`() = runTest {
        coEvery { api.createWeeklyPlanV2(any()) } returns Response.error(
            409,
            """{"code":"weekly_plan_exists","message":"이미 존재합니다"}"""
                .toResponseBody("application/json".toMediaTypeOrNull()),
        )
        val profile = UserProfile("u", 170f, 65f, Gender.UNSPECIFIED, null, null, restDay = 7)

        val result = repo.createWeeklyPlan(profile, WorkoutPlanMode.HOME)

        assertTrue(result.exceptionOrNull() is WeeklyPlanAlreadyExistsException)
    }

    @Test
    fun `getExerciseById 는 Room 캐시에서 네트워크 없이 운동을 반환한다`() = runTest {
        val json = validDayPlansJson()
        coEvery { authRepo.getCurrentUserId() } returns "u"
        coEvery { dao.getPlan(any(), any()) } returns
            WeeklyPlanEntity("p", "u", currentWeekStart.toString(), "gym", json)
        coEvery { translationProvider.localize(any<Exercise>()) } answers {
            firstArg<Exercise>().copy(name = "푸시업")
        }

        val result = repo.getExerciseById("e1")

        assertEquals("e1", result.getOrThrow()?.id)
        assertEquals("푸시업", result.getOrThrow()?.name)
        coVerify(exactly = 1) { translationProvider.localize(any<Exercise>()) }
        coVerify(exactly = 0) { api.getWeeklyPlanV2(any()) }
    }

    @Test
    fun `loadCurrentWeekPlan 은 원격 404 시 stale Room 캐시를 삭제하고 Missing 을 반환한다`() = runTest {
        coEvery { authRepo.getCurrentUserId() } returns "u"
        coEvery { api.getWeeklyPlanV2(any()) } returns Response.error(
            404,
            """{"code":"NOT_FOUND","message":"not found"}""".toResponseBody("application/json".toMediaTypeOrNull()),
        )

        val read = repo.loadCurrentWeekPlan()

        assertEquals(CurrentWeekPlanRead.Missing, read)
        coVerify(exactly = 1) { dao.deleteByUserAndWeek("u", currentWeekStart.toString()) }
    }

    @Test
    fun `loadCurrentWeekPlan 네트워크 실패 시 유효한 Room plan 을 한글화해 Found 로 반환한다`() = runTest {
        val json = validDayPlansJson()
        coEvery { api.getWeeklyPlanV2(any()) } throws IOException("offline")
        coEvery { authRepo.getCurrentUserId() } returns "u"
        coEvery { dao.getPlan(any(), any()) } returns
            WeeklyPlanEntity("p", "u", currentWeekStart.toString(), "gym", json)
        coEvery { translationProvider.localize(any<WeeklyPlan>()) } answers {
            firstArg<WeeklyPlan>().let { plan ->
                plan.copy(
                    days = plan.days.map { day ->
                        day.copy(exercises = day.exercises.map { it.copy(name = "푸시업") })
                    },
                )
            }
        }

        val read = repo.loadCurrentWeekPlan()

        assertTrue(read is CurrentWeekPlanRead.Found)
        val plan = (read as CurrentWeekPlanRead.Found).plan
        assertEquals(WorkoutPlanMode.GYM, plan.mode)
        assertEquals("푸시업", plan.days.first().exercises.single().name)
        coVerify(exactly = 1) { translationProvider.localize(any<WeeklyPlan>()) }
    }

    @Test
    fun `loadCurrentWeekPlan 은 손상된 원격 JSON 또는 빈 운동 계획을 Unreadable 로 반환한다`() = runTest {
        val resp = WeeklyPlanResponse(
            dayPlans = "[]",
            id = "p",
            planMode = ApiWorkoutPlanMode.gym,
            userId = "u",
            weekStart = currentWeekStart.toString(),
            createdAt = null,
        )
        coEvery { api.getWeeklyPlanV2(any()) } returns Response.success(resp)

        val read = repo.loadCurrentWeekPlan()

        assertTrue(read is CurrentWeekPlanRead.Unreadable)
        assertEquals(PlanSource.REMOTE, (read as CurrentWeekPlanRead.Unreadable).source)
        coVerify(exactly = 0) { dao.insertPlan(any()) }
    }

    @Test
    fun `loadCurrentWeekPlan 은 네트워크 실패 후 손상된 캐시를 Unreadable CACHE 로 반환한다`() = runTest {
        coEvery { api.getWeeklyPlanV2(any()) } throws IOException("offline")
        coEvery { authRepo.getCurrentUserId() } returns "u"
        coEvery { dao.getPlan(any(), any()) } returns
            WeeklyPlanEntity("p", "u", currentWeekStart.toString(), "gym", "{broken")

        val read = repo.loadCurrentWeekPlan()

        assertTrue(read is CurrentWeekPlanRead.Unreadable)
        assertEquals(PlanSource.CACHE, (read as CurrentWeekPlanRead.Unreadable).source)
    }

    @Test
    fun `createWeeklyPlan GYM 은 영문 정본을 전송 저장하고 한글화한 결과만 반환한다`() = runTest {
        val raw = ExerciseDto(
            id = "e1",
            name = "push-up",
            gifUrl = "gif",
            bodyParts = listOf("chest"),
            equipments = listOf("bodyweight"),
            instructions = listOf("Step: 1 Lower your body."),
        )
        every { exerciseDb.getSetsAndReps(any()) } returns (3 to 10)
        coEvery { exerciseDb.getStrengthExercises(any(), any()) } returns listOf(raw)
        coEvery { exerciseDb.getCardioExercises(any()) } returns listOf(raw.copy(id = "c1", name = "burpee"))
        coEvery { api.getPreviousWeeklyPlanV2(any()) } returns mockk { every { body() } returns null }
        val request = slot<WeeklyPlanV2Request>()
        coEvery { api.createWeeklyPlanV2(capture(request)) } answers {
            val req = request.captured
            Response.success(
                201,
                WeeklyPlanResponse(
                    dayPlans = req.dayPlans,
                    id = "p",
                    planMode = ApiWorkoutPlanMode.gym,
                    userId = "u",
                    weekStart = req.weekStart,
                    createdAt = null,
                ),
            )
        }
        val entity = slot<WeeklyPlanEntity>()
        coEvery { dao.insertPlan(capture(entity)) } returns Unit
        coEvery { translationProvider.localize(any<WeeklyPlan>()) } answers {
            firstArg<WeeklyPlan>().let { plan ->
                plan.copy(
                    days = plan.days.map { day ->
                        day.copy(exercises = day.exercises.map { it.copy(name = "푸시업") })
                    },
                )
            }
        }
        val profile = UserProfile("u", 170f, 65f, Gender.UNSPECIFIED, null, null, restDay = 7)

        val result = repo.createWeeklyPlan(profile, WorkoutPlanMode.GYM).getOrThrow()

        assertTrue(request.captured.dayPlans.contains("push-up"))
        assertTrue(!request.captured.dayPlans.contains("푸시업"))
        assertEquals("gym", entity.captured.planMode)
        assertTrue(entity.captured.dayPlansJson.contains("push-up"))
        assertTrue(result.days.flatMap { it.exercises }.all { it.name == "푸시업" })
        coVerify(exactly = 1) { translationProvider.localize(any<WeeklyPlan>()) }
    }

    @Test
    fun `getHistory 와 getStatistics 는 planMode 를 보존한다`() = runTest {
        val responsePlan = WeeklyPlanResponse(
            dayPlans = validDayPlansJson(),
            id = "p",
            planMode = ApiWorkoutPlanMode.home,
            userId = "u",
            weekStart = currentWeekStart.toString(),
            createdAt = null,
        )
        coEvery { api.getWeeklyPlanHistoryV2(0, 10) } returns Response.success(
            WeeklyPlanHistoryResponse(page = 0, plans = listOf(responsePlan), propertySize = 10, totalCount = 1),
        )
        coEvery { api.getStatisticsV2(12) } returns Response.success(
            StatisticsResponse(
                currentStreak = 1,
                longestStreak = 2,
                weeklyRates = listOf(
                    WeeklyRateDto(
                        completionRate = BigDecimal.valueOf(0.8),
                        planMode = ApiWorkoutPlanMode.home,
                        weekStart = currentWeekStart.toString(),
                    ),
                ),
            ),
        )

        val history = repo.getHistory(0, 10).getOrThrow()
        val stats = repo.getStatistics(12).getOrThrow()

        assertEquals(WorkoutPlanMode.HOME, history.first.single().mode)
        assertEquals(WorkoutPlanMode.HOME, stats.weeklyRates.single().mode)
    }

    @Test
    fun `deleteCurrentWeekPlan 은 원격 v2 DELETE 성공 후 로컬 캐시를 삭제한다`() = runTest {
        coEvery { authRepo.getCurrentUserId() } returns "u"
        coEvery { api.deleteWeeklyPlanV2(currentWeekStart.toString()) } returns Response.success(Unit)

        val result = repo.deleteCurrentWeekPlan()

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { api.deleteWeeklyPlanV2(currentWeekStart.toString()) }
        coVerify(exactly = 1) { dao.deleteByUserAndWeek("u", currentWeekStart.toString()) }
    }

    @Test
    fun `번역 중 coroutine 취소는 삼키지 않고 전파한다`() = runTest {
        val resp = WeeklyPlanResponse(
            dayPlans = validDayPlansJson(),
            id = "p",
            planMode = ApiWorkoutPlanMode.gym,
            userId = "u",
            weekStart = currentWeekStart.toString(),
            createdAt = null,
        )
        coEvery { api.getWeeklyPlanV2(any()) } returns Response.success(resp)
        coEvery { translationProvider.localize(any<WeeklyPlan>()) } throws CancellationException("cancelled")

        try {
            repo.loadCurrentWeekPlan()
            throw AssertionError("CancellationException이 전파되어야 합니다.")
        } catch (_: CancellationException) {
            // expected
        }
    }
}
