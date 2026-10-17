package com.gunnys.eundunhealth.ui.home

import com.gunnys.eundunhealth.domain.model.DayPlan
import com.gunnys.eundunhealth.domain.model.WeeklyPlan
import com.gunnys.eundunhealth.domain.repository.WorkoutRepository
import com.gunnys.eundunhealth.domain.usecase.CheckAndAwardBadgesUseCase
import com.gunnys.eundunhealth.domain.usecase.GetOrCreateWeeklyPlanUseCase
import com.gunnys.eundunhealth.domain.usecase.HealthSyncResult
import com.gunnys.eundunhealth.domain.usecase.SyncHealthDataUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
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
    )

    private lateinit var getOrCreate: GetOrCreateWeeklyPlanUseCase
    private lateinit var syncHealth: SyncHealthDataUseCase
    private lateinit var checkBadges: CheckAndAwardBadgesUseCase
    private lateinit var workoutRepo: WorkoutRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        getOrCreate = mockk()
        syncHealth = mockk()
        checkBadges = mockk()
        workoutRepo = mockk()
        coEvery { getOrCreate() } returns Result.success(plan)
        coEvery { syncHealth(any()) } returns Result.success(
            HealthSyncResult(plan, isAvailable = true, hasPermission = true, newlyCompletedDates = emptyList()),
        )
        coEvery { checkBadges(any()) } returns Result.success(emptyList())
        coEvery { workoutRepo.updateDayCompletion(any(), any(), any(), any()) } returns Result.success(Unit)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun createViewModel() = HomeViewModel(
        getOrCreate,
        syncHealth,
        checkBadges,
        workoutRepo,
    )

    @Test
    fun `init 시 plan 과 today(KST)가 Success 상태로 로드된다`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is HomeUiState.Success)
        state as HomeUiState.Success
        assertEquals(plan, state.plan)
        assertEquals(0, state.completedCount)
        assertEquals(2, state.totalWorkoutDays)
        assertEquals(LocalDate.now(ZoneId.of("Asia/Seoul")), state.today)
        assertTrue(state.isHealthConnectAvailable)
        assertTrue(state.hasHealthPermission)
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

        // 직렬화가 없으면 true·false 두 PATCH 가 모두 나가 서버 상태가 어긋난다(회귀가드).
        coVerify(exactly = 1) { workoutRepo.updateDayCompletion(any(), monday, false, any()) }
        coVerify(exactly = 0) { workoutRepo.updateDayCompletion(any(), monday, true, any()) }
    }

    @Test
    fun `toggleDayCompletion 은 낙관적 업데이트에서 해당 날을 manuallySet=true 로 표시한다`() = runTest {
        // PR #122 핵심수정 회귀가드 — 수동 토글이 manuallySet 을 박제해야 이후 HC 자동완료가 덮지 못한다.
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
        // 사용자 수동 토글은 manual=true (HC 자동완료 manual=false 와 구분) → 백엔드가 manuallySet 박제.
        val vm = createViewModel()
        advanceUntilIdle()

        vm.toggleDayCompletion(monday)
        advanceUntilIdle()

        coVerify(exactly = 1) { workoutRepo.updateDayCompletion(any(), monday, true, manual = true) }
    }
}
