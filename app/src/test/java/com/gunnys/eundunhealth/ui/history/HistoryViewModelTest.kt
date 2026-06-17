package com.gunnys.eundunhealth.ui.history

import com.gunnys.eundunhealth.domain.model.WeeklyPlan
import com.gunnys.eundunhealth.domain.repository.WorkoutRepository
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val monday = LocalDate.of(2026, 6, 8)
    private lateinit var workoutRepo: WorkoutRepository

    private fun plan(id: String) = WeeklyPlan(id = id, userId = "u", weekStart = monday, days = emptyList())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        workoutRepo = mockk()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `로드한 수가 총합과 같으면 hasMore=false 이고 다음 페이지는 no-op`() = runTest {
        coEvery { workoutRepo.getHistory(0, 10) } returns Result.success(Pair(listOf(plan("a"), plan("b")), 2))
        val vm = HistoryViewModel(workoutRepo)
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.plans.size)
        assertFalse(vm.uiState.value.hasMore)

        vm.loadNextPage()
        advanceUntilIdle()

        // hasMore=false 면 추가 호출이 없어야 한다(무한 재요청 방지).
        coVerify(exactly = 1) { workoutRepo.getHistory(any(), any()) }
    }

    @Test
    fun `로드한 수가 총합보다 적으면 hasMore=true 이고 다음 페이지를 이어붙인다`() = runTest {
        coEvery { workoutRepo.getHistory(0, 10) } returns Result.success(Pair((1..10).map { plan("a$it") }, 12))
        coEvery { workoutRepo.getHistory(1, 10) } returns Result.success(Pair(listOf(plan("b1"), plan("b2")), 12))
        val vm = HistoryViewModel(workoutRepo)
        advanceUntilIdle()

        assertEquals(10, vm.uiState.value.plans.size)
        assertTrue(vm.uiState.value.hasMore)

        vm.loadNextPage()
        advanceUntilIdle()

        assertEquals(12, vm.uiState.value.plans.size)
        assertFalse(vm.uiState.value.hasMore)
    }

    @Test
    fun `로드 실패 시 error 가 set 되고 isLoading 은 false`() = runTest {
        coEvery { workoutRepo.getHistory(0, 10) } returns Result.failure(RuntimeException("net"))
        val vm = HistoryViewModel(workoutRepo)
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
    }
}
