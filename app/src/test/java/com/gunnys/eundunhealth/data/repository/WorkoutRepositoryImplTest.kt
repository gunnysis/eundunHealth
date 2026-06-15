package com.gunnys.eundunhealth.data.repository

import com.google.gson.Gson
import com.gunnys.eundunhealth.api.generated.api.WeeklyPlanApi
import com.gunnys.eundunhealth.api.generated.model.WeeklyPlanResponse
import com.gunnys.eundunhealth.data.local.dao.WeeklyPlanDao
import com.gunnys.eundunhealth.data.remote.exercisedb.ExerciseDbDataSource
import com.gunnys.eundunhealth.domain.model.FitnessLevel
import com.gunnys.eundunhealth.domain.model.UserProfile
import com.gunnys.eundunhealth.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

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
}
