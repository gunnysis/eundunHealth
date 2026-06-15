package com.gunnys.eundunhealth.data.repository

import com.google.gson.Gson
import com.gunnys.eundunhealth.api.generated.api.WeeklyPlanApi
import com.gunnys.eundunhealth.api.generated.model.WeeklyPlanResponse
import com.gunnys.eundunhealth.data.local.dao.WeeklyPlanDao
import com.gunnys.eundunhealth.data.local.entity.WeeklyPlanEntity
import com.gunnys.eundunhealth.data.remote.api.dto.DayPlanJson
import com.gunnys.eundunhealth.data.remote.exercisedb.ExerciseDbDataSource
import com.gunnys.eundunhealth.domain.model.DayPlan
import com.gunnys.eundunhealth.domain.model.Exercise
import com.gunnys.eundunhealth.domain.model.ExerciseType
import com.gunnys.eundunhealth.domain.model.FitnessLevel
import com.gunnys.eundunhealth.domain.model.UserProfile
import com.gunnys.eundunhealth.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.time.LocalDate

class WorkoutRepositoryImplTest {

    private lateinit var api: WeeklyPlanApi
    private lateinit var exerciseDb: ExerciseDbDataSource
    private lateinit var dao: WeeklyPlanDao
    private lateinit var authRepo: AuthRepository
    private lateinit var repo: WorkoutRepositoryImpl

    @Before
    fun setup() {
        api = mockk(relaxed = true)
        exerciseDb = mockk()
        dao = mockk(relaxed = true)
        authRepo = mockk(relaxed = true)
        repo = WorkoutRepositoryImpl(api, exerciseDb, dao, Gson(), authRepo)
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
        val ex = Exercise("e1", "푸시업", "chest", "bw", "", emptyList(), 3, 10, ExerciseType.STRENGTH)
        val json = Gson().toJson(
            listOf(DayPlanJson(DayPlan(LocalDate.of(2026, 6, 15), listOf(ex), isRestDay = false, isCompleted = false))),
        )
        coEvery { authRepo.getCurrentUserId() } returns "u"
        coEvery { dao.getPlan(any(), any()) } returns WeeklyPlanEntity("p", "u", "2026-06-15", json)

        val result = repo.getExerciseById("e1")

        assertEquals("e1", result.getOrThrow()?.id)
        // 캐시 히트면 네트워크(getWeeklyPlan)를 쓰지 않아야 한다 (회귀가드).
        coVerify(exactly = 0) { api.getWeeklyPlan(any()) }
    }
}
