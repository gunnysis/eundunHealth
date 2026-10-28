package com.gunnys.eundunhealth.ui.mealplan

import com.gunnys.eundunhealth.domain.model.DailyMealPlan
import com.gunnys.eundunhealth.domain.model.WeeklyMealPlan
import com.gunnys.eundunhealth.domain.repository.MealPlanRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class MealPlanViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: MealPlanRepository

    private val samplePlan = WeeklyMealPlan(
        weeklyPlan = listOf(
            DailyMealPlan(day = "월요일", isRestDay = false, meals = emptyList()),
        ),
        summary = "균형 잡힌 고단백 식단입니다.",
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `초기 로드 시 식단이 없으면 Empty 상태가 된다`() = runTest {
        coEvery { repository.getCurrentPlan() } returns Result.success(null)

        val vm = MealPlanViewModel(repository)
        advanceUntilIdle()

        assertTrue(vm.uiState.value is MealPlanUiState.Empty)
    }

    @Test
    fun `기존 식단이 있는 상태에서 generatePlan 실패 시 기존 식단을 유지하고 regenerateError 를 설정한다`() = runTest {
        coEvery { repository.getCurrentPlan() } returns Result.success(samplePlan)
        coEvery { repository.generatePlan() } returns Result.failure(IOException("timeout"))

        val vm = MealPlanViewModel(repository)
        advanceUntilIdle()
        assertTrue(vm.uiState.value is MealPlanUiState.Success)

        vm.generatePlan()
        assertTrue(vm.uiState.value is MealPlanUiState.Generating)

        advanceUntilIdle()
        val state = vm.uiState.value as MealPlanUiState.Success
        assertEquals(samplePlan, state.plan)
        assertNotNull(state.regenerateError)
    }

    @Test
    fun `generatePlan 성공 시 새로운 식단으로 전환되고 regenerateError 가 해제된다`() = runTest {
        val newPlan = samplePlan.copy(summary = "새로 생성된 식단입니다.")
        coEvery { repository.getCurrentPlan() } returns Result.success(samplePlan)
        coEvery { repository.generatePlan() } returns Result.success(newPlan)

        val vm = MealPlanViewModel(repository)
        advanceUntilIdle()

        vm.generatePlan()
        advanceUntilIdle()

        val state = vm.uiState.value as MealPlanUiState.Success
        assertEquals(newPlan, state.plan)
        assertNull(state.regenerateError)
    }
}
