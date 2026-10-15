package com.gunnys.eundunhealth.data.repository

import com.google.gson.Gson
import com.gunnys.eundunhealth.api.generated.api.WeeklyPlanApi
import com.gunnys.eundunhealth.api.generated.model.WeeklyPlanHistoryResponse
import com.gunnys.eundunhealth.api.generated.model.WeeklyPlanRequest
import com.gunnys.eundunhealth.api.generated.model.WeeklyPlanResponse
import com.gunnys.eundunhealth.data.local.dao.WeeklyPlanDao
import com.gunnys.eundunhealth.data.local.entity.WeeklyPlanEntity
import com.gunnys.eundunhealth.data.remote.api.dto.DayPlanJson
import com.gunnys.eundunhealth.data.remote.exercisedb.ExerciseDbDataSource
import com.gunnys.eundunhealth.data.remote.exercisedb.ExerciseDto
import com.gunnys.eundunhealth.data.translation.ExerciseTranslationProvider
import com.gunnys.eundunhealth.domain.model.DayPlan
import com.gunnys.eundunhealth.domain.model.Exercise
import com.gunnys.eundunhealth.domain.model.ExerciseType
import com.gunnys.eundunhealth.domain.model.FitnessLevel
import com.gunnys.eundunhealth.domain.model.Gender
import com.gunnys.eundunhealth.domain.model.UserProfile
import com.gunnys.eundunhealth.domain.model.WeeklyPlan
import com.gunnys.eundunhealth.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import io.sentry.Sentry
import io.sentry.protocol.SentryId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.time.LocalDate

class WorkoutRepositoryImplTest {

    private lateinit var api: WeeklyPlanApi
    private lateinit var exerciseDb: ExerciseDbDataSource
    private lateinit var dao: WeeklyPlanDao
    private lateinit var authRepo: AuthRepository
    private lateinit var translationProvider: ExerciseTranslationProvider
    private lateinit var repo: WorkoutRepositoryImpl

    @Before
    fun setup() {
        api = mockk(relaxed = true)
        exerciseDb = mockk()
        dao = mockk(relaxed = true)
        authRepo = mockk(relaxed = true)
        translationProvider = mockk()
        coEvery { translationProvider.localize(any<Exercise>()) } answers { firstArg() }
        coEvery { translationProvider.localize(any<com.gunnys.eundunhealth.domain.model.WeeklyPlan>()) } answers {
            firstArg()
        }
        repo = WorkoutRepositoryImpl(api, exerciseDb, dao, Gson(), authRepo, translationProvider)
    }

    @Test
    fun `createWeeklyPlan 은 모든 운동 풀이 비면 빈 계획을 저장하지 않고 실패한다`() = runTest {
        every { exerciseDb.getSetsAndReps(any()) } returns (3 to 10)
        coEvery { exerciseDb.getStrengthExercises(any(), any()) } returns emptyList()
        coEvery { exerciseDb.getCardioExercises(any()) } returns emptyList()
        coEvery { api.getPreviousWeeklyPlan(any()) } returns
            mockk<Response<WeeklyPlanResponse>> { every { body() } returns null }

        val profile = mockk<UserProfile>(relaxed = true)
        every { profile.fitnessLevel } returns FitnessLevel.BEGINNER
        every { profile.restDay } returns 7

        val result = repo.createWeeklyPlan(profile)

        assertTrue("빈 풀이면 실패해야 한다", result.isFailure)
        coVerify(exactly = 0) { api.createWeeklyPlan(any()) }
    }

    @Test
    fun `getExerciseById 는 Room 캐시에서 네트워크 없이 운동을 반환한다`() = runTest {
        val ex = Exercise("e1", "push-up", "chest", "bw", "", emptyList(), 3, 10, ExerciseType.STRENGTH)
        val json = Gson().toJson(
            listOf(DayPlanJson(DayPlan(LocalDate.of(2026, 6, 15), listOf(ex), isRestDay = false, isCompleted = false))),
        )
        coEvery { authRepo.getCurrentUserId() } returns "u"
        coEvery { dao.getPlan(any(), any()) } returns WeeklyPlanEntity("p", "u", "2026-06-15", json)
        coEvery { translationProvider.localize(ex) } returns ex.copy(name = "푸시업")

        val result = repo.getExerciseById("e1")

        assertEquals("e1", result.getOrThrow()?.id)
        assertEquals("푸시업", result.getOrThrow()?.name)
        coVerify(exactly = 1) { translationProvider.localize(ex) }
        // 캐시 히트면 네트워크(getWeeklyPlan)를 쓰지 않아야 한다 (회귀가드).
        coVerify(exactly = 0) { api.getWeeklyPlan(any()) }
    }

    @Test
    fun `getCurrentWeekPlan 네트워크 실패 시 Room plan 을 한글화해 반환한다`() = runTest {
        val ex = Exercise("e1", "push-up", "chest", "bodyweight", "", listOf("Go down."), 3, 10, ExerciseType.STRENGTH)
        val json = Gson().toJson(
            listOf(DayPlanJson(DayPlan(LocalDate.of(2026, 6, 15), listOf(ex), false, false))),
        )
        coEvery { api.getWeeklyPlan(any()) } throws IOException("offline")
        coEvery { authRepo.getCurrentUserId() } returns "u"
        coEvery { dao.getPlan(any(), any()) } returns WeeklyPlanEntity("p", "u", "2026-06-15", json)
        coEvery { translationProvider.localize(any<WeeklyPlan>()) } answers {
            firstArg<WeeklyPlan>().let { plan ->
                plan.copy(
                    days = plan.days.map { day ->
                        day.copy(exercises = day.exercises.map { it.copy(name = "푸시업") })
                    },
                )
            }
        }

        val result = repo.getCurrentWeekPlan().getOrThrow()

        assertEquals("푸시업", result?.days?.single()?.exercises?.single()?.name)
        coVerify(exactly = 1) { translationProvider.localize(any<WeeklyPlan>()) }
    }

    @Test
    fun `createWeeklyPlan 은 영문 정본을 전송 저장하고 한글화한 결과만 반환한다`() = runTest {
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
        coEvery { exerciseDb.getCardioExercises(any()) } returns emptyList()
        coEvery { api.getPreviousWeeklyPlan(any()) } returns mockk { every { body() } returns null }
        val request = slot<WeeklyPlanRequest>()
        coEvery { api.createWeeklyPlan(capture(request)) } returns Response.success(
            WeeklyPlanResponse("[]", "p", "u", "2026-10-12", null),
        )
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

        val result = repo.createWeeklyPlan(profile).getOrThrow()

        assertTrue(request.captured.dayPlans.contains("push-up"))
        assertTrue(!request.captured.dayPlans.contains("푸시업"))
        assertTrue(entity.captured.dayPlansJson.contains("push-up"))
        assertTrue(!entity.captured.dayPlansJson.contains("푸시업"))
        assertTrue(result.days.flatMap { it.exercises }.all { it.name == "푸시업" })
        coVerify(exactly = 1) { translationProvider.localize(any<WeeklyPlan>()) }
    }

    @Test
    fun `getHistory 는 모든 plan 을 한글화하고 totalCount 를 보존한다`() = runTest {
        val responsePlan = WeeklyPlanResponse("[]", "p", "u", "2026-10-12", null)
        coEvery { api.getWeeklyPlanHistory(0, 10) } returns Response.success(
            WeeklyPlanHistoryResponse(page = 0, plans = listOf(responsePlan), propertySize = 10, totalCount = 23),
        )

        val result = repo.getHistory(0, 10).getOrThrow()

        assertEquals(23, result.second)
        assertEquals("p", result.first.single().id)
        coVerify(exactly = 1) { translationProvider.localize(any<WeeklyPlan>()) }
    }

    @Test
    fun `getCurrentWeekPlan 성공 시 서버 plan 을 Room 캐시에 기록한다`() = runTest {
        // 캐시를 read 경로에서 갱신하지 않아 오프라인서 stale(미완료) plan 을 보이던 회귀 방지(F3).
        val resp = WeeklyPlanResponse(
            dayPlans = "[]",
            id = "p",
            userId = "u",
            weekStart = "2026-06-15",
            createdAt = null,
        )
        coEvery { api.getWeeklyPlan(any()) } returns Response.success(resp)

        repo.getCurrentWeekPlan()

        coVerify(exactly = 1) { dao.insertPlan(any()) }
        coVerify(exactly = 1) { translationProvider.localize(any<com.gunnys.eundunhealth.domain.model.WeeklyPlan>()) }
    }

    @Test
    fun `손상된 캐시 day_plans 는 Sentry 로 보고하고 빈 계획으로 폴백한다`() = runTest {
        // R8 keep 갭(INC-25)과 동일한 silent empty-data 클래스 — 폴백 전 관측 가능해야 한다(회귀가드).
        mockkStatic(Sentry::class)
        every { Sentry.captureException(any<Throwable>()) } returns SentryId.EMPTY_ID
        try {
            coEvery { api.getWeeklyPlan(any()) } throws IOException("offline")
            coEvery { authRepo.getCurrentUserId() } returns "u"
            coEvery { dao.getPlan(any(), any()) } returns WeeklyPlanEntity("p", "u", "2026-06-15", "{broken")

            val result = repo.getCurrentWeekPlan()

            assertTrue("손상 JSON 이어도 크래시 없이 success", result.isSuccess)
            assertEquals(emptyList<DayPlan>(), result.getOrThrow()?.days)
            verify(exactly = 1) { Sentry.captureException(any<Throwable>()) }
        } finally {
            unmockkStatic(Sentry::class)
        }
    }

    @Test
    fun `정상 빈 배열 캐시는 Sentry 로 보고하지 않는다`() = runTest {
        // 정당하게 비어 있는 plan("[]")은 손상이 아니므로 노이즈를 만들지 않아야 한다.
        mockkStatic(Sentry::class)
        every { Sentry.captureException(any<Throwable>()) } returns SentryId.EMPTY_ID
        try {
            coEvery { api.getWeeklyPlan(any()) } throws IOException("offline")
            coEvery { authRepo.getCurrentUserId() } returns "u"
            coEvery { dao.getPlan(any(), any()) } returns WeeklyPlanEntity("p", "u", "2026-06-15", "[]")

            val result = repo.getCurrentWeekPlan()

            assertTrue(result.isSuccess)
            verify(exactly = 0) { Sentry.captureException(any<Throwable>()) }
        } finally {
            unmockkStatic(Sentry::class)
        }
    }

    @Test
    fun `번역 중 coroutine 취소는 Result failure로 삼키지 않고 전파한다`() = runTest {
        val resp = WeeklyPlanResponse(
            dayPlans = "[]",
            id = "p",
            userId = "u",
            weekStart = "2026-06-15",
            createdAt = null,
        )
        coEvery { api.getWeeklyPlan(any()) } returns Response.success(resp)
        coEvery { translationProvider.localize(any<com.gunnys.eundunhealth.domain.model.WeeklyPlan>()) } throws
            CancellationException("cancelled")

        try {
            repo.getCurrentWeekPlan()
            throw AssertionError("CancellationException이 전파되어야 합니다.")
        } catch (_: CancellationException) {
            // expected
        }
    }
}
