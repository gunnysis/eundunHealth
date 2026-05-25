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
            earnedKeys.map { Badge(key = it, name = it, description = "", earnedAt = Instant.now()) },
        )

        override suspend fun awardBadge(badgeKey: String): Result<Badge> {
            earnedKeys.add(badgeKey)
            awardedKeys.add(badgeKey)
            return Result.success(Badge(key = badgeKey, name = badgeKey, description = "", earnedAt = Instant.now()))
        }

        override suspend fun hasBadge(badgeKey: String): Result<Boolean> = Result.success(badgeKey in earnedKeys)
    }

    @Test
    fun `all workout days completed awards WEEK_1_COMPLETE`() = runTest {
        // FIRST_WORKOUT은 이미 보유 가정 — WEEK_1_COMPLETE 부여만 검증
        val repo = FakeBadgeRepo(mutableSetOf(BadgeKeys.FIRST_WORKOUT))
        val useCase = CheckAndAwardBadgesUseCase(repo)
        val plan = createPlan(completedDays = 5, totalWorkoutDays = 5)

        val result = useCase(plan)
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
        assertEquals(BadgeKeys.WEEK_1_COMPLETE, repo.awardedKeys.first())
    }

    @Test
    fun `no workout completed awards nothing`() = runTest {
        // 운동일이 한 개도 완료되지 않으면 FIRST_WORKOUT도 부여되지 않음
        val repo = FakeBadgeRepo()
        val useCase = CheckAndAwardBadgesUseCase(repo)
        val plan = createPlan(completedDays = 0, totalWorkoutDays = 5)

        val result = useCase(plan)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
        assertTrue(repo.awardedKeys.isEmpty())
    }

    @Test
    fun `incomplete week still awards FIRST_WORKOUT when any day is completed`() = runTest {
        // 한 주를 모두 완료하지 않더라도 한 운동일이 완료됐다면 FIRST_WORKOUT 부여 (§N)
        val repo = FakeBadgeRepo()
        val useCase = CheckAndAwardBadgesUseCase(repo)
        val plan = createPlan(completedDays = 3, totalWorkoutDays = 5)

        val result = useCase(plan)
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
        assertEquals(BadgeKeys.FIRST_WORKOUT, repo.awardedKeys.first())
    }

    @Test
    fun `already earned WEEK_1_COMPLETE plus FIRST_WORKOUT is not re-awarded`() = runTest {
        val repo = FakeBadgeRepo(mutableSetOf(BadgeKeys.WEEK_1_COMPLETE, BadgeKeys.FIRST_WORKOUT))
        val useCase = CheckAndAwardBadgesUseCase(repo)
        val plan = createPlan(completedDays = 5, totalWorkoutDays = 5)

        val result = useCase(plan)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
        assertTrue(repo.awardedKeys.isEmpty())
    }

    @Test
    fun `fresh user with full week earns both WEEK_1_COMPLETE and FIRST_WORKOUT`() = runTest {
        val repo = FakeBadgeRepo()
        val useCase = CheckAndAwardBadgesUseCase(repo)
        val plan = createPlan(completedDays = 5, totalWorkoutDays = 5)

        val result = useCase(plan)
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().size)
        assertEquals(
            setOf(BadgeKeys.WEEK_1_COMPLETE, BadgeKeys.FIRST_WORKOUT),
            repo.awardedKeys.toSet(),
        )
    }
}
