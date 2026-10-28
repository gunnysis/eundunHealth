package com.gunnys.eundunhealth.ui.workout

import androidx.lifecycle.SavedStateHandle
import com.gunnys.eundunhealth.domain.model.CurrentWeekPlanRead
import com.gunnys.eundunhealth.domain.model.DayPlan
import com.gunnys.eundunhealth.domain.model.Exercise
import com.gunnys.eundunhealth.domain.model.ExerciseType
import com.gunnys.eundunhealth.domain.model.WeeklyPlan
import com.gunnys.eundunhealth.domain.model.WorkoutPlanMode
import com.gunnys.eundunhealth.domain.repository.WorkoutRepository
import com.gunnys.eundunhealth.domain.usecase.CheckAndAwardBadgesUseCase
import com.gunnys.eundunhealth.domain.usecase.LoadCurrentWeekPlanUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutGuideViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val targetDate = LocalDate.of(2026, 10, 28)
    private val ex1 = Exercise(
        id = "ex-1",
        name = "스쿼트",
        bodyPart = "하체",
        equipment = "맨몸",
        gifUrl = "",
        instructions = listOf("발을 어깨너비로 벌립니다."),
        sets = 3,
        reps = 12,
        type = ExerciseType.STRENGTH,
    )
    private val ex2 = Exercise(
        id = "ex-2",
        name = "푸시업",
        bodyPart = "가슴",
        equipment = "맨몸",
        gifUrl = "",
        instructions = listOf("팔을 굽혔다 폅니다."),
        sets = 3,
        reps = 10,
        type = ExerciseType.STRENGTH,
    )
    private val plan = WeeklyPlan(
        id = "plan-1",
        userId = "user-1",
        weekStart = LocalDate.of(2026, 10, 26),
        days = listOf(
            DayPlan(
                date = targetDate,
                exercises = listOf(ex1, ex2),
                isRestDay = false,
                isCompleted = false,
            ),
        ),
        mode = WorkoutPlanMode.HOME,
    )

    private lateinit var loadCurrentWeekPlan: LoadCurrentWeekPlanUseCase
    private lateinit var workoutRepo: WorkoutRepository
    private lateinit var checkBadges: CheckAndAwardBadgesUseCase

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        loadCurrentWeekPlan = mockk()
        workoutRepo = mockk()
        checkBadges = mockk()

        coEvery { loadCurrentWeekPlan() } returns CurrentWeekPlanRead.Found(plan)
        coEvery { workoutRepo.updateDayCompletion(any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { checkBadges(any()) } returns Result.success(emptyList())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun createViewModel(dateIso: String = targetDate.toString()) = WorkoutGuideViewModel(
        savedStateHandle = SavedStateHandle(mapOf("dateIso" to dateIso)),
        loadCurrentWeekPlan = loadCurrentWeekPlan,
        workoutRepo = workoutRepo,
        checkBadges = checkBadges,
    )

    @Test
    fun `초기 로드 시 첫 종목부터 InProgress 상태가 된다`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is WorkoutGuideUiState.InProgress)
        state as WorkoutGuideUiState.InProgress
        assertEquals(0, state.currentIndex)
        assertEquals(2, state.totalExercises)
    }

    @Test
    fun `nextExerciseOrComplete 호출 시 다음 종목으로 이동하고 마지막 종목에서 완료 처리한다`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.nextExerciseOrComplete()
        val afterFirst = vm.uiState.value as WorkoutGuideUiState.InProgress
        assertEquals(1, afterFirst.currentIndex)
        assertTrue(afterFirst.isLastExercise)

        vm.nextExerciseOrComplete()
        advanceUntilIdle()

        assertTrue(vm.uiState.value is WorkoutGuideUiState.CompletedSummary)
        coVerify(exactly = 1) { workoutRepo.updateDayCompletion("plan-1", targetDate, true, manual = true) }
    }

    @Test
    fun `completeWorkout(exitImmediately=true) 성공 시 ExitGuide 이벤트를 발행한다`() = runTest {
        val vm = createViewModel()
        val effects = mutableListOf<WorkoutGuideSideEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.sideEffect.collect { effects.add(it) }
        }
        advanceUntilIdle()

        vm.completeWorkout(exitImmediately = true)
        advanceUntilIdle()

        assertTrue(effects.contains(WorkoutGuideSideEffect.ExitGuide))
        coVerify(exactly = 1) { workoutRepo.updateDayCompletion("plan-1", targetDate, true, manual = true) }
    }

    @Test
    fun `완료 저장 실패 시 InProgress 를 유지하고 completionError 를 노출한다`() = runTest {
        coEvery { workoutRepo.updateDayCompletion(any(), any(), any(), any()) } returns
            Result.failure(IOException("network error"))

        val vm = createViewModel()
        advanceUntilIdle()

        vm.completeWorkout(exitImmediately = false)
        advanceUntilIdle()

        val state = vm.uiState.value as WorkoutGuideUiState.InProgress
        assertNotNull(state.completionError)
    }
}
