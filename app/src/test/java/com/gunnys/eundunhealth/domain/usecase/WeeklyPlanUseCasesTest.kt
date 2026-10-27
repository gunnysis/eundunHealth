package com.gunnys.eundunhealth.domain.usecase

import com.gunnys.eundunhealth.domain.model.CurrentWeekPlanRead
import com.gunnys.eundunhealth.domain.model.DayPlan
import com.gunnys.eundunhealth.domain.model.Exercise
import com.gunnys.eundunhealth.domain.model.ExerciseType
import com.gunnys.eundunhealth.domain.model.Gender
import com.gunnys.eundunhealth.domain.model.PlanSource
import com.gunnys.eundunhealth.domain.model.UserProfile
import com.gunnys.eundunhealth.domain.model.WeeklyPlan
import com.gunnys.eundunhealth.domain.model.WeeklyPlanAlreadyExistsException
import com.gunnys.eundunhealth.domain.model.WorkoutPlanMode
import com.gunnys.eundunhealth.domain.repository.UserRepository
import com.gunnys.eundunhealth.domain.repository.WorkoutRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

class WeeklyPlanUseCasesTest {

    private val monday = LocalDate.of(2026, 6, 15)
    private lateinit var workoutRepo: WorkoutRepository
    private lateinit var userRepo: UserRepository

    private val profile = UserProfile(
        userId = "u1",
        heightCm = 172f,
        weightKg = 68f,
        gender = Gender.MALE,
        bodyFatPercent = 18f,
        muscleMassKg = 32f,
        restDay = 7,
        defaultWorkoutPlanMode = WorkoutPlanMode.HOME,
    )

    private fun samplePlan(id: String = "plan-1", mode: WorkoutPlanMode = WorkoutPlanMode.HOME) = WeeklyPlan(
        id = id,
        userId = "u1",
        weekStart = monday,
        days = listOf(
            DayPlan(
                monday,
                listOf(Exercise("e1", "푸시업", "가슴", "없음", "", listOf("1"), 3, 12, ExerciseType.STRENGTH)),
                isRestDay = false,
                isCompleted = false,
            ),
        ),
        mode = mode,
    )

    @Before
    fun setup() {
        workoutRepo = mockk()
        userRepo = mockk()
    }

    @Test
    fun `LoadCurrentWeekPlanUseCase 는 자동 생성 없이 repository 결과를 그대로 반환한다`() = runTest {
        val loadUseCase = LoadCurrentWeekPlanUseCase(workoutRepo)
        coEvery { workoutRepo.loadCurrentWeekPlan() } returns CurrentWeekPlanRead.Missing

        val result = loadUseCase()

        assertEquals(CurrentWeekPlanRead.Missing, result)
        coVerify(exactly = 0) { workoutRepo.createWeeklyPlan(any(), any()) }
    }

    @Test
    fun `LoadDefaultWorkoutPlanModeUseCase 는 프로필 기본값을 반환하고 실패 시 null 을 반환한다`() = runTest {
        val modeUseCase = LoadDefaultWorkoutPlanModeUseCase(userRepo)
        coEvery { userRepo.getProfile() } returnsMany listOf(
            Result.success(profile),
            Result.failure(IOException("offline")),
        )

        assertEquals(WorkoutPlanMode.HOME, modeUseCase())
        assertNull(modeUseCase())
    }

    @Test
    fun `CreateWeeklyPlanUseCase 는 409 충돌 시 덮어쓰지 않고 단일 GET 으로 기존 계획을 반환한다`() = runTest {
        val createUseCase = CreateWeeklyPlanUseCase(workoutRepo, userRepo)
        val existing = samplePlan(id = "existing-plan", mode = WorkoutPlanMode.GYM)
        coEvery { userRepo.getProfile() } returns Result.success(profile)
        coEvery { workoutRepo.createWeeklyPlan(profile, WorkoutPlanMode.HOME) } returns
            Result.failure(WeeklyPlanAlreadyExistsException())
        coEvery { workoutRepo.loadCurrentWeekPlan() } returns CurrentWeekPlanRead.Found(existing)

        val result = createUseCase(WorkoutPlanMode.HOME)

        assertTrue(result.isSuccess)
        assertEquals(existing, result.getOrThrow())
        coVerify(exactly = 1) { workoutRepo.createWeeklyPlan(profile, WorkoutPlanMode.HOME) }
        coVerify(exactly = 1) { workoutRepo.loadCurrentWeekPlan() }
    }

    @Test
    fun `CreateWeeklyPlanUseCase 는 409 후 재조회도 Unreadable 이면 실패를 반환한다`() = runTest {
        val createUseCase = CreateWeeklyPlanUseCase(workoutRepo, userRepo)
        coEvery { userRepo.getProfile() } returns Result.success(profile)
        coEvery { workoutRepo.createWeeklyPlan(profile, WorkoutPlanMode.HOME) } returns
            Result.failure(WeeklyPlanAlreadyExistsException())
        coEvery { workoutRepo.loadCurrentWeekPlan() } returns
            CurrentWeekPlanRead.Unreadable(PlanSource.REMOTE, IllegalArgumentException("bad json"))

        val result = createUseCase(WorkoutPlanMode.HOME)

        assertTrue(result.isFailure)
        coVerify(exactly = 1) { workoutRepo.loadCurrentWeekPlan() }
    }
}
