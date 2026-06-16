package com.gunnys.eundunhealth.ui.home

import com.gunnys.eundunhealth.data.preferences.ThemeMode
import com.gunnys.eundunhealth.data.preferences.ThemePreferences
import com.gunnys.eundunhealth.domain.model.DailyActivity
import com.gunnys.eundunhealth.domain.model.DayPlan
import com.gunnys.eundunhealth.domain.model.WeeklyPlan
import com.gunnys.eundunhealth.domain.repository.WorkoutRepository
import com.gunnys.eundunhealth.domain.usecase.CheckAndAwardBadgesUseCase
import com.gunnys.eundunhealth.domain.usecase.GetOrCreateWeeklyPlanUseCase
import com.gunnys.eundunhealth.domain.usecase.GetTodayActivityUseCase
import com.gunnys.eundunhealth.domain.usecase.HealthSyncResult
import com.gunnys.eundunhealth.domain.usecase.SyncHealthDataUseCase
import com.gunnys.eundunhealth.domain.usecase.TodayActivityResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

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
    private val activity = DailyActivity(steps = 1234L, totalCaloriesKcal = 320, avgHeartRateBpm = 88L)

    private lateinit var getOrCreate: GetOrCreateWeeklyPlanUseCase
    private lateinit var syncHealth: SyncHealthDataUseCase
    private lateinit var checkBadges: CheckAndAwardBadgesUseCase
    private lateinit var workoutRepo: WorkoutRepository
    private lateinit var themePrefs: ThemePreferences
    private lateinit var getTodayActivity: GetTodayActivityUseCase

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        getOrCreate = mockk()
        syncHealth = mockk()
        checkBadges = mockk()
        workoutRepo = mockk()
        themePrefs = mockk()
        getTodayActivity = mockk()

        every { themePrefs.themeMode } returns flowOf(ThemeMode.SYSTEM)
        coEvery { getOrCreate() } returns Result.success(plan)
        coEvery { syncHealth(any()) } returns Result.success(
            HealthSyncResult(plan, isAvailable = true, hasPermission = true, newlyCompletedDates = emptyList()),
        )
        coEvery { checkBadges(any()) } returns Result.success(emptyList())
        coEvery { getTodayActivity() } returns Result.success(
            TodayActivityResult(activity = activity, hasPermission = true),
        )
        coEvery { workoutRepo.updateDayCompletion(any(), any(), any(), any()) } returns Result.success(Unit)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun createViewModel() = HomeViewModel(
        getOrCreate,
        syncHealth,
        checkBadges,
        workoutRepo,
        themePrefs,
        getTodayActivity,
    )

    @Test
    fun `init 시 plan + todayActivity 가 Success 상태로 로드된다`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is HomeUiState.Success)
        state as HomeUiState.Success
        assertEquals(activity, state.todayActivity)
        assertTrue(state.hasActivityPermission)
    }

    @Test
    fun `toggleDayCompletion 성공 시 todayActivity 가 유지된다 (회귀 가드)`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.toggleDayCompletion(monday)
        advanceUntilIdle()

        val state = vm.uiState.value as HomeUiState.Success
        // 활동 카드가 토글로 사라지면 안 된다 (successWithStats 가 활동 필드를 리셋하던 회귀).
        assertEquals(activity, state.todayActivity)
        assertEquals(1, state.completedCount)
    }

    @Test
    fun `toggleDayCompletion 서버 실패 시 완료는 revert 되고 todayActivity 는 보존된다`() = runTest {
        coEvery { workoutRepo.updateDayCompletion(any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("server down"))
        val vm = createViewModel()
        advanceUntilIdle()

        vm.toggleDayCompletion(monday)
        advanceUntilIdle()

        val state = vm.uiState.value as HomeUiState.Success
        assertEquals(0, state.completedCount) // 낙관적 토글 revert
        assertEquals(activity, state.todayActivity) // 활동 필드는 live 에서 보존
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
