package com.gunnys.eundunhealth.domain.usecase

import com.gunnys.eundunhealth.domain.model.Badge
import com.gunnys.eundunhealth.domain.model.BadgeKeys
import com.gunnys.eundunhealth.domain.model.DayPlan
import com.gunnys.eundunhealth.domain.model.WeeklyPlan
import com.gunnys.eundunhealth.domain.repository.BadgeRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CheckAndAwardBadgesUseCaseTest {

    private fun createPlan(completedDays: Int, totalWorkoutDays: Int): WeeklyPlan {
        val weekStart = LocalDate.of(2026, 1, 5)
        val days = (0 until totalWorkoutDays).map { i ->
            DayPlan(
                date = weekStart.plusDays(i.toLong()),
                exercises = emptyList(),
                isRestDay = false,
                isCompleted = i < completedDays,
            )
        } + DayPlan(
            date = weekStart.plusDays(6),
            exercises = emptyList(),
            isRestDay = true,
            isCompleted = false,
        )
        return WeeklyPlan("plan1", "user1", weekStart, days)
    }

    class FakeBadgeRepo(
        private val earnedKeys: MutableSet<String> = mutableSetOf(),
    ) : BadgeRepository {
        val awardedKeys = mutableListOf<String>()

        override suspend fun getEarnedBadges(): Result<List<Badge>> = Result.success(
            earnedKeys.map { Badge("id", "user1", it, it, "", Instant.now()) },
        )

        override suspend fun awardBadge(badgeKey: String): Result<Badge> {
            earnedKeys.add(badgeKey)
            awardedKeys.add(badgeKey)
            return Result.success(Badge("id", "user1", badgeKey, badgeKey, "", Instant.now()))
        }

        override suspend fun hasBadge(badgeKey: String): Result<Boolean> = Result.success(badgeKey in earnedKeys)
    }

    @Test
    fun `all workout days completed awards badge`() = runTest {
        val repo = FakeBadgeRepo()
        val useCase = CheckAndAwardBadgesUseCase(repo)
        val plan = createPlan(completedDays = 5, totalWorkoutDays = 5)

        val result = useCase(plan)
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
        assertEquals(BadgeKeys.WEEK_1_COMPLETE, repo.awardedKeys.first())
    }

    @Test
    fun `incomplete workout days does not award badge`() = runTest {
        val repo = FakeBadgeRepo()
        val useCase = CheckAndAwardBadgesUseCase(repo)
        val plan = createPlan(completedDays = 3, totalWorkoutDays = 5)

        val result = useCase(plan)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
        assertTrue(repo.awardedKeys.isEmpty())
    }

    @Test
    fun `already earned badge is not awarded again`() = runTest {
        val repo = FakeBadgeRepo(mutableSetOf(BadgeKeys.WEEK_1_COMPLETE))
        val useCase = CheckAndAwardBadgesUseCase(repo)
        val plan = createPlan(completedDays = 5, totalWorkoutDays = 5)

        val result = useCase(plan)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
        assertTrue(repo.awardedKeys.isEmpty())
    }
}
