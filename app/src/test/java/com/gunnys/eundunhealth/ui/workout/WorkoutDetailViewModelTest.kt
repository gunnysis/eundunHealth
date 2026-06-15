package com.gunnys.eundunhealth.ui.workout

import androidx.lifecycle.SavedStateHandle
import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.model.Exercise
import com.gunnys.eundunhealth.domain.model.ExerciseType
import com.gunnys.eundunhealth.domain.repository.WorkoutRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var workoutRepo: WorkoutRepository

    private val exercise = Exercise("e1", "푸시업", "chest", "bw", "", emptyList(), 3, 10, ExerciseType.STRENGTH)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        workoutRepo = mockk()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun createViewModel() = WorkoutDetailViewModel(SavedStateHandle(mapOf("exerciseId" to "e1")), workoutRepo)

    @Test
    fun `운동을 찾으면 Loaded 상태`() = runTest {
        coEvery { workoutRepo.getExerciseById("e1") } returns Result.success(exercise)

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is WorkoutDetailUiState.Loaded)
        assertTrue((state as WorkoutDetailUiState.Loaded).exercise.id == "e1")
    }

    @Test
    fun `운동을 못 찾으면 NotFound 에러 (Sentry 노이즈 없는 예상 케이스)`() = runTest {
        coEvery { workoutRepo.getExerciseById("e1") } returns Result.success(null)

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is WorkoutDetailUiState.Error)
        assertTrue((state as WorkoutDetailUiState.Error).error is AppError.NotFound)
    }
}
