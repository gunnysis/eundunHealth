package com.gunnys.eundunhealth.ui.home

import com.gunnys.eundunhealth.data.preferences.ThemePreferences
import com.gunnys.eundunhealth.domain.model.CurrentWeekPlanRead
import com.gunnys.eundunhealth.domain.model.DayPlan
import com.gunnys.eundunhealth.domain.model.PlanSource
import com.gunnys.eundunhealth.domain.model.SmileSummaryStats
import com.gunnys.eundunhealth.domain.model.WeeklyPlan
import com.gunnys.eundunhealth.domain.model.WorkoutPlanMode
import com.gunnys.eundunhealth.domain.repository.SmileTrainingRepository
import com.gunnys.eundunhealth.domain.repository.WorkoutRepository
import com.gunnys.eundunhealth.domain.usecase.CheckAndAwardBadgesUseCase
import com.gunnys.eundunhealth.domain.usecase.CreateWeeklyPlanUseCase
import com.gunnys.eundunhealth.domain.usecase.HealthSyncResult
import com.gunnys.eundunhealth.domain.usecase.LoadCurrentWeekPlanUseCase
import com.gunnys.eundunhealth.domain.usecase.LoadDefaultWorkoutPlanModeUseCase
import com.gunnys.eundunhealth.domain.usecase.SyncHealthDataUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val monday = LocalDate.of(2026, 6, 8)
    private val plan = WeeklyPlan(
        id = "plan-1",
        userId = "user-1",
        weekStart = monday,
        days = listOf(
            DayPlan(monday, emptyList(), isRestDay = false, isCompleted = false),
            DayPlan(monday.plusDays(1), emptyList(), isRestDay = false, isCompleted = false),
            DayPlan(monday.plusDays(2), emptyList(), isRestDay = true, isCompleted = false),
        ),
        mode = WorkoutPlanMode.HOME,
    )

    private lateinit var loadCurrentWeekPlan: LoadCurrentWeekPlanUseCase
    private lateinit var loadDefaultWorkoutPlanMode: LoadDefaultWorkoutPlanModeUseCase
    private lateinit var createWeeklyPlan: CreateWeeklyPlanUseCase
    private lateinit var themePreferences: ThemePreferences
    private lateinit var syncHealth: SyncHealthDataUseCase
    private lateinit var checkBadges: CheckAndAwardBadgesUseCase
    private lateinit var workoutRepo: WorkoutRepository
    private lateinit var smileTrainingRepo: SmileTrainingRepository
    private lateinit var smileStatsFlow: MutableStateFlow<SmileSummaryStats>

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        loadCurrentWeekPlan = mockk()
        loadDefaultWorkoutPlanMode = mockk()
        createWeeklyPlan = mockk()
        themePreferences = mockk(relaxed = true)
        syncHealth = mockk()
        checkBadges = mockk()
        workoutRepo = mockk()
        smileTrainingRepo = mockk()
        smileStatsFlow = MutableStateFlow(SmileSummaryStats())

        coEvery { loadCurrentWeekPlan() } returns CurrentWeekPlanRead.Found(plan)
        coEvery { loadDefaultWorkoutPlanMode() } returns null
        coEvery { themePreferences.hasAutoCreateAttempted(any()) } returns false
        coEvery { themePreferences.isSmileShortcutDismissed(any()) } returns false
        every { smileTrainingRepo.observeSummaryStats() } returns smileStatsFlow
        coEvery { syncHealth(any()) } answers {
            val inputPlan = firstArg<WeeklyPlan>()
            Result.success(
                HealthSyncResult(inputPlan, isAvailable = true, hasPermission = true, newlyCompletedDates = emptyList()),
            )
        }
        coEvery { checkBadges(any()) } returns Result.success(emptyList())
        coEvery { workoutRepo.updateDayCompletion(any(), any(), any(), any()) } returns Result.success(Unit)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun createViewModel() = HomeViewModel(
        loadCurrentWeekPlan = loadCurrentWeekPlan,
        loadDefaultWorkoutPlanMode = loadDefaultWorkoutPlanMode,
        createWeeklyPlan = createWeeklyPlan,
        themePreferences = themePreferences,
        syncHealth = syncHealth,
        checkBadges = checkBadges,
        workoutRepo = workoutRepo,
        smileTrainingRepo = smileTrainingRepo,
    )

    @Test
    fun `init 시 plan 과 today(KST)가 Success 상태로 로드된다`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is HomeUiState.Success)
        state as HomeUiState.Success
        assertEquals(plan, state.plan)
        assertEquals(WorkoutPlanMode.HOME, state.plan.mode)
        assertEquals(0, state.completedCount)
        assertEquals(2, state.totalWorkoutDays)
        assertEquals(LocalDate.now(ZoneId.of("Asia/Seoul")), state.today)
        assertTrue(state.isHealthConnectAvailable)
        assertTrue(state.hasHealthPermission)
        assertFalse(state.isSmileCompletedToday)
        assertEquals(0, state.smileCurrentStreakDays)
        coVerify(exactly = 1) { themePreferences.clearAutoCreateAttempt() }
    }

    @Test
    fun `smileTrainingRepo 요약 통계 변경 시 Success 상태의 isSmileCompletedToday 와 smileCurrentStreakDays 가 갱신된다`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        val initial = vm.uiState.value as HomeUiState.Success
        assertFalse(initial.isSmileCompletedToday)
        assertEquals(0, initial.smileCurrentStreakDays)

        smileStatsFlow.value = SmileSummaryStats(
            totalSessions = 5,
            totalDurationSec = 60,
            isCompletedToday = true,
            currentStreakDays = 4,
            longestStreakDays = 4,
        )
        advanceUntilIdle()

        val updated = vm.uiState.value as HomeUiState.Success
        assertTrue(updated.isSmileCompletedToday)
        assertEquals(4, updated.smileCurrentStreakDays)
    }

    @Test
    fun `정상 404(Missing) + 기본 환경 미설정 시 자동 생성 없이 NoPlan 상태로 전환된다`() = runTest {
        coEvery { loadCurrentWeekPlan() } returns CurrentWeekPlanRead.Missing
        coEvery { loadDefaultWorkoutPlanMode() } returns null

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is HomeUiState.NoPlan)
        assertNull((state as HomeUiState.NoPlan).selectedMode)
        coVerify(exactly = 0) { createWeeklyPlan(any()) }
    }

    @Test
    fun `NoPlan 에서 환경 선택 후 수동 생성 성공 시 Success 로 전환된다`() = runTest {
        coEvery { loadCurrentWeekPlan() } returns CurrentWeekPlanRead.Missing
        coEvery { loadDefaultWorkoutPlanMode() } returns null
        coEvery { createWeeklyPlan(WorkoutPlanMode.HOME) } returns Result.success(plan)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.selectMode(WorkoutPlanMode.HOME)
        assertEquals(WorkoutPlanMode.HOME, (vm.uiState.value as HomeUiState.NoPlan).selectedMode)

        vm.createSelectedPlan()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is HomeUiState.Success)
        coVerify(exactly = 1) { createWeeklyPlan(WorkoutPlanMode.HOME) }
    }

    @Test
    fun `정상 404(Missing) + 기본 환경 설정 시 1회 자동 생성 후 Success 로 전환된다`() = runTest {
        coEvery { loadCurrentWeekPlan() } returns CurrentWeekPlanRead.Missing
        coEvery { loadDefaultWorkoutPlanMode() } returns WorkoutPlanMode.GYM
        val gymPlan = plan.copy(mode = WorkoutPlanMode.GYM)
        coEvery { createWeeklyPlan(WorkoutPlanMode.GYM) } returns Result.success(gymPlan)

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is HomeUiState.Success)
        assertEquals(WorkoutPlanMode.GYM, (state as HomeUiState.Success).plan.mode)
        coVerify(exactly = 1) { themePreferences.markAutoCreateAttempted(any()) }
        coVerify(exactly = 1) { createWeeklyPlan(WorkoutPlanMode.GYM) }
    }

    @Test
    fun `같은 KST 주에 이미 자동 생성 시도가 기록된 경우 재시작 시 자동 POST 를 생략하고 AutoCreateError 를 표시한다`() = runTest {
        coEvery { loadCurrentWeekPlan() } returns CurrentWeekPlanRead.Missing
        coEvery { loadDefaultWorkoutPlanMode() } returns WorkoutPlanMode.HOME
        coEvery { themePreferences.hasAutoCreateAttempted(any()) } returns true

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is HomeUiState.AutoCreateError)
        assertEquals(WorkoutPlanMode.HOME, (state as HomeUiState.AutoCreateError).mode)
        coVerify(exactly = 0) { createWeeklyPlan(any()) }
    }

    @Test
    fun `AutoCreateError 에서 다시 만들기(retryAutoCreate) 호출 시에만 명시적으로 재생성을 시도한다`() = runTest {
        coEvery { loadCurrentWeekPlan() } returns CurrentWeekPlanRead.Missing
        coEvery { loadDefaultWorkoutPlanMode() } returns WorkoutPlanMode.HOME
        coEvery { themePreferences.hasAutoCreateAttempted(any()) } returns true
        coEvery { createWeeklyPlan(WorkoutPlanMode.HOME) } returns Result.success(plan)

        val vm = createViewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is HomeUiState.AutoCreateError)

        vm.retryAutoCreate()
        advanceUntilIdle()

        assertTrue(vm.uiState.value is HomeUiState.Success)
        coVerify(exactly = 1) { createWeeklyPlan(WorkoutPlanMode.HOME) }
    }

    @Test
    fun `Unreadable 계획은 자동 덮어쓰기 없이 PlanDataError 로 노출되고 계획 초기화 시 DELETE 후 새 경로로 전환된다`() = runTest {
        coEvery { loadCurrentWeekPlan() } returns CurrentWeekPlanRead.Unreadable(PlanSource.REMOTE)
        coEvery { workoutRepo.deleteCurrentWeekPlan() } returns Result.success(Unit)
        coEvery { loadDefaultWorkoutPlanMode() } returns null

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is HomeUiState.PlanDataError)
        coVerify(exactly = 0) { createWeeklyPlan(any()) }

        vm.resetCorruptedPlan()
        advanceUntilIdle()

        coVerify(exactly = 1) { workoutRepo.deleteCurrentWeekPlan() }
        assertTrue(vm.uiState.value is HomeUiState.NoPlan)
    }

    @Test
    fun `조회 실패(Failed)는 계획 없음으로 오판하지 않고 Error 상태를 유지하며 생성을 호출하지 않는다`() = runTest {
        coEvery { loadCurrentWeekPlan() } returns CurrentWeekPlanRead.Failed(IOException("network down"))

        val vm = createViewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value is HomeUiState.Error)
        coVerify(exactly = 0) { createWeeklyPlan(any()) }
    }

    @Test
    fun `toggleDayCompletion 성공 시 completedCount 가 갱신되고 today 와 권한 상태가 유지된다`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        val initialToday = (vm.uiState.value as HomeUiState.Success).today

        vm.toggleDayCompletion(monday)
        advanceUntilIdle()

        val state = vm.uiState.value as HomeUiState.Success
        assertEquals(1, state.completedCount)
        assertEquals(0.5f, state.completionRate, 0.001f)
        assertEquals(initialToday, state.today)
        assertTrue(state.hasHealthPermission)
    }

    @Test
    fun `toggleDayCompletion 서버 실패 시 완료는 revert 되고 toggleError 가 설정된다`() = runTest {
        coEvery { workoutRepo.updateDayCompletion(any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("server down"))
        val vm = createViewModel()
        advanceUntilIdle()
        val initialToday = (vm.uiState.value as HomeUiState.Success).today

        vm.toggleDayCompletion(monday)
        advanceUntilIdle()

        val state = vm.uiState.value as HomeUiState.Success
        assertEquals(0, state.completedCount) // 낙관적 토글 revert
        assertEquals(initialToday, state.today)
        assertNotNull(state.toggleError)
    }

    @Test
    fun `toggleDayCompletion 재시도 성공 시 이전 toggleError 가 초기화된다`() = runTest {
        coEvery { workoutRepo.updateDayCompletion(any(), any(), any(), any()) } returnsMany listOf(
            Result.failure(RuntimeException("transient error")),
            Result.success(Unit),
        )
        val vm = createViewModel()
        advanceUntilIdle()

        vm.toggleDayCompletion(monday)
        advanceUntilIdle()
        assertNotNull((vm.uiState.value as HomeUiState.Success).toggleError)

        vm.toggleDayCompletion(monday)
        advanceUntilIdle()
        val recovered = vm.uiState.value as HomeUiState.Success
        assertNull(recovered.toggleError)
        assertEquals(1, recovered.completedCount)
    }

    @Test
    fun `completionRate 는 운동일이 0 이면 0`() {
        val restOnly = HomeUiState.Success(plan = plan, completedCount = 0, totalWorkoutDays = 0)
        assertEquals(0f, restOnly.completionRate, 0f)
    }

    @Test
    fun `빠른 체크 후 해제는 마지막 의도(해제)만 서버에 전송한다 (레이스 직렬화 가드)`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.toggleDayCompletion(monday) // 체크 → PATCH(true) 예약
        vm.toggleDayCompletion(monday) // 해제 → 직전 전송 취소 + PATCH(false)
        advanceUntilIdle()

        coVerify(exactly = 1) { workoutRepo.updateDayCompletion(any(), monday, false, any()) }
        coVerify(exactly = 0) { workoutRepo.updateDayCompletion(any(), monday, true, any()) }
    }

    @Test
    fun `toggleDayCompletion 은 낙관적 업데이트에서 해당 날을 manuallySet=true 로 표시한다`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.toggleDayCompletion(monday)
        advanceUntilIdle()

        val state = vm.uiState.value as HomeUiState.Success
        val day = state.plan.days.first { it.date == monday }
        assertTrue("수동 토글은 manuallySet=true", day.manuallySet)
    }

    @Test
    fun `toggleDayCompletion 은 updateDayCompletion 을 manual=true 로 전송한다`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.toggleDayCompletion(monday)
        advanceUntilIdle()

        coVerify(exactly = 1) { workoutRepo.updateDayCompletion(any(), monday, true, manual = true) }
    }

    @Test
    fun `Success 상태에서 loadPlan 호출 시 Loading 으로 리셋되지 않고 isRefreshing 만 갱신된다`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is HomeUiState.Success)

        vm.loadPlan()
        assertTrue("Success 상태 유지", vm.uiState.value is HomeUiState.Success)
        assertTrue("새로고침 중 플래그 활성", (vm.uiState.value as HomeUiState.Success).isRefreshing)

        advanceUntilIdle()
        val after = vm.uiState.value as HomeUiState.Success
        assertFalse(after.isRefreshing)
    }

    @Test
    fun `완료 해제 시 ShowUndoUncompleteSnackbar 이벤트가 발행되고 undoUncomplete 로 복원된다`() = runTest {
        val vm = createViewModel()
        val effects = mutableListOf<HomeSideEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.sideEffect.collect { effects.add(it) }
        }
        advanceUntilIdle()

        vm.toggleDayCompletion(monday) // 완료 처리
        advanceUntilIdle()
        assertTrue(effects.isEmpty())

        vm.toggleDayCompletion(monday) // 완료 해제
        advanceUntilIdle()
        assertEquals(listOf(HomeSideEffect.ShowUndoUncompleteSnackbar(monday)), effects)

        vm.undoUncomplete(monday) // 되돌리기
        advanceUntilIdle()
        val state = vm.uiState.value as HomeUiState.Success
        assertTrue(state.plan.days.first { it.date == monday }.isCompleted)
    }

    @Test
    fun `refreshHealthConnectStatus 호출 시 권한 허용 상태와 새로 감지된 운동 완료가 즉시 반영된다`() = runTest {
        val syncedPlan = plan.copy(
            days = plan.days.map { if (it.date == monday) it.copy(isCompleted = true) else it },
        )
        coEvery { syncHealth(any()) } returnsMany listOf(
            Result.success(
                HealthSyncResult(plan, isAvailable = true, hasPermission = false, newlyCompletedDates = emptyList()),
            ),
            Result.success(
                HealthSyncResult(syncedPlan, isAvailable = true, hasPermission = true, newlyCompletedDates = listOf(monday)),
            ),
        )

        val vm = createViewModel()
        advanceUntilIdle()
        val initial = vm.uiState.value as HomeUiState.Success
        assertFalse("초기에는 권한 미허용 상태", initial.hasHealthPermission)
        assertEquals(0, initial.completedCount)

        vm.refreshHealthConnectStatus()
        advanceUntilIdle()

        val updated = vm.uiState.value as HomeUiState.Success
        assertTrue("권한 허용 후 hasHealthPermission 이 true 로 갱신되어야 한다", updated.hasHealthPermission)
        assertEquals(1, updated.completedCount)
        coVerify(exactly = 1) { workoutRepo.updateDayCompletion(plan.id, monday, true, manual = false) }
        coVerify(exactly = 2) { checkBadges(any()) }
    }
}
