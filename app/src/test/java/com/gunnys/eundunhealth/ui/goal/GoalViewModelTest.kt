package com.gunnys.eundunhealth.ui.goal

import com.gunnys.eundunhealth.domain.model.Goal
import com.gunnys.eundunhealth.domain.model.GoalType
import com.gunnys.eundunhealth.domain.repository.GoalRepository
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GoalViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var goalRepo: GoalRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        goalRepo = mockk()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `load 성공 시 goals 가 채워지고 error 는 null`() = runTest {
        coEvery { goalRepo.getGoals() } returns Result.success(
            listOf(Goal(type = GoalType.WEIGHT, targetValue = 70f, createdAt = null)),
        )
        coEvery { goalRepo.getProfileHistory() } returns Result.success(emptyList())

        val vm = GoalViewModel(goalRepo)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(1, state.goals.size)
        assertNull(state.error)
    }

    @Test
    fun `goals 성공 + history 실패 시 화면은 렌더되고 error 는 null (부분 실패 비차단)`() = runTest {
        // 회귀 가드: 비핵심 차트(history) 실패가 목표 편집기 전체를 막으면 안 된다.
        coEvery { goalRepo.getGoals() } returns Result.success(
            listOf(Goal(type = GoalType.WEIGHT, targetValue = 70f, createdAt = null)),
        )
        coEvery { goalRepo.getProfileHistory() } returns Result.failure(RuntimeException("chart down"))

        val vm = GoalViewModel(goalRepo)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(1, state.goals.size)
        assertNull(state.error)
    }

    @Test
    fun `load 실패 시 error 가 set 되고 isLoading 은 false (silent empty 금지)`() = runTest {
        coEvery { goalRepo.getGoals() } returns Result.failure(RuntimeException("network"))
        coEvery { goalRepo.getProfileHistory() } returns Result.success(emptyList())

        val vm = GoalViewModel(goalRepo)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNotNull("로드 실패는 error 로 노출되어야 한다", state.error)
        assertEquals(false, state.isLoading)
    }

    @Test
    fun `에러 후 재시도(load) 성공 시 error 가 사라진다`() = runTest {
        coEvery { goalRepo.getGoals() } returns Result.failure(RuntimeException("network"))
        coEvery { goalRepo.getProfileHistory() } returns Result.success(emptyList())
        val vm = GoalViewModel(goalRepo)
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.error)

        coEvery { goalRepo.getGoals() } returns Result.success(emptyList())
        vm.load()
        advanceUntilIdle()

        assertNull(vm.uiState.value.error)
    }
}
