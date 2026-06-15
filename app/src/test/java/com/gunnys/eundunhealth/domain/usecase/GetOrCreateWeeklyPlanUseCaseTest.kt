package com.gunnys.eundunhealth.domain.usecase

import com.gunnys.eundunhealth.domain.model.DayPlan
import com.gunnys.eundunhealth.domain.model.Exercise
import com.gunnys.eundunhealth.domain.model.ExerciseType
import com.gunnys.eundunhealth.domain.model.UserProfile
import com.gunnys.eundunhealth.domain.model.WeeklyPlan
import com.gunnys.eundunhealth.domain.repository.UserRepository
import com.gunnys.eundunhealth.domain.repository.WorkoutRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class GetOrCreateWeeklyPlanUseCaseTest {

    private val monday = LocalDate.of(2026, 6, 15)
    private lateinit var workoutRepo: WorkoutRepository
    private lateinit var userRepo: UserRepository
    private lateinit var useCase: GetOrCreateWeeklyPlanUseCase

    private fun exercise() = Exercise("e1", "푸시업", "chest", "bw", "", emptyList(), 3, 10, ExerciseType.STRENGTH)

    private fun planWithExercises(id: String) = WeeklyPlan(
        id,
        "u",
        monday,
        listOf(DayPlan(monday, listOf(exercise()), isRestDay = false, isCompleted = false)),
    )

    private fun emptyPlan(id: String) = WeeklyPlan(
        id,
        "u",
        monday,
        listOf(
            DayPlan(monday, emptyList(), isRestDay = false, isCompleted = false),
            DayPlan(monday.plusDays(1), emptyList(), isRestDay = false, isCompleted = false),
        ),
    )

    private val profile = mockk<UserProfile>(relaxed = true)

    @Before
    fun setup() {
        workoutRepo = mockk()
        userRepo = mockk()
        useCase = GetOrCreateWeeklyPlanUseCase(workoutRepo, userRepo)
    }

    @Test
    fun `기존 계획이 운동을 포함하면 그대로 반환하고 재생성하지 않는다`() = runTest {
        coEvery { workoutRepo.getCurrentWeekPlan() } returns Result.success(planWithExercises("existing"))

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals("existing", result.getOrThrow().id)
        coVerify(exactly = 0) { workoutRepo.createWeeklyPlan(any()) }
    }

    @Test
    fun `기존 계획의 운동이 모두 비어 있으면 재생성한다 (자가치유)`() = runTest {
        coEvery { workoutRepo.getCurrentWeekPlan() } returns Result.success(emptyPlan("empty"))
        coEvery { userRepo.getProfile() } returns Result.success(profile)
        coEvery { workoutRepo.createWeeklyPlan(any()) } returns Result.success(planWithExercises("regenerated"))

        val result = useCase()

        assertEquals("regenerated", result.getOrThrow().id)
        coVerify(exactly = 1) { workoutRepo.createWeeklyPlan(any()) }
    }

    @Test
    fun `기존 계획이 없으면 생성한다`() = runTest {
        coEvery { workoutRepo.getCurrentWeekPlan() } returns Result.success(null)
        coEvery { userRepo.getProfile() } returns Result.success(profile)
        coEvery { workoutRepo.createWeeklyPlan(any()) } returns Result.success(planWithExercises("created"))

        val result = useCase()

        assertEquals("created", result.getOrThrow().id)
        coVerify(exactly = 1) { workoutRepo.createWeeklyPlan(any()) }
    }
}
