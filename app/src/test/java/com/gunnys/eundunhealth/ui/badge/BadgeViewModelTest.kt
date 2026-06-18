package com.gunnys.eundunhealth.ui.badge

import com.gunnys.eundunhealth.domain.model.Badge
import com.gunnys.eundunhealth.domain.model.BadgeKeys
import com.gunnys.eundunhealth.domain.repository.BadgeRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class BadgeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var badgeRepo: BadgeRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        badgeRepo = mockk()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `획득 배지 없어도 카탈로그 전체 9개 Loaded 상태`() = runTest {
        coEvery { badgeRepo.getEarnedBadges() } returns Result.success(emptyList())
        val vm = BadgeViewModel(badgeRepo)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected Loaded but was $state", state is BadgeUiState.Loaded)
        val items = (state as BadgeUiState.Loaded).badges
        assertEquals("카탈로그 9개 모두 표시", 9, items.size)
        assertTrue("모두 미획득", items.none { it.earned })
    }

    @Test
    fun `획득 배지 있으면 해당 항목 earned=true 표시`() = runTest {
        val earned = listOf(
            Badge(
                key = BadgeKeys.FIRST_WORKOUT,
                name = "첫 운동",
                description = "처음 운동을 완료했습니다",
                earnedAt = Instant.now(),
            ),
        )
        coEvery { badgeRepo.getEarnedBadges() } returns Result.success(earned)
        val vm = BadgeViewModel(badgeRepo)
        advanceUntilIdle()

        val state = vm.uiState.value as BadgeUiState.Loaded
        val firstWorkout = state.badges.find { it.key == BadgeKeys.FIRST_WORKOUT }
        assertNotNull("first_workout 항목 존재", firstWorkout)
        assertTrue("first_workout 획득 표시", firstWorkout!!.earned)
        assertEquals("나머지 8개 미획득", 8, state.badges.count { !it.earned })
    }

    @Test
    fun `로드 실패 시 Error 상태`() = runTest {
        coEvery { badgeRepo.getEarnedBadges() } returns Result.failure(RuntimeException("서버 오류"))
        val vm = BadgeViewModel(badgeRepo)
        advanceUntilIdle()

        assertTrue("Expected Error", vm.uiState.value is BadgeUiState.Error)
    }
}
