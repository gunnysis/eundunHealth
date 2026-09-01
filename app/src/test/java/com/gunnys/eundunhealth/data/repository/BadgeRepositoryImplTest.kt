package com.gunnys.eundunhealth.data.repository

import com.gunnys.eundunhealth.api.generated.api.BadgesApi
import com.gunnys.eundunhealth.api.generated.model.BadgeResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

/**
 * BadgeRepositoryImpl 의 60초 TTL 캐시 특성화 테스트.
 *
 * 이 캐시는 `@Singleton` 이라 앱 전체가 한 인스턴스를 공유한다(`RepositoryModule`) — 서로 다른
 * 화면·코루틴이 동시에 두드린다. 그래서 "동시에 불러도 옳은가" 가 이 클래스의 핵심 계약이고,
 * 아래 `되살아나는 무효화` 테스트가 그 계약을 고정한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BadgeRepositoryImplTest {

    // ---- 기본 캐시 동작 ----

    @Test
    fun `TTL 안에서는 API 를 다시 부르지 않는다`() = runTest {
        val api = FakeBadgesApi(badges = listOf(badge("first_workout")))
        val repo = BadgeRepositoryImpl(api)

        repo.getEarnedBadges()
        repo.getEarnedBadges()
        repo.hasBadge("first_workout")

        assertEquals(1, api.getBadgesCallCount)
    }

    @Test
    fun `TTL 이 지나면 다시 부른다`() = runTest {
        val clock = MutableClock(now = 1_000L)
        val api = FakeBadgesApi(badges = listOf(badge("first_workout")))
        val repo = BadgeRepositoryImpl(api, clock)

        repo.getEarnedBadges()
        clock.now += 60_001L
        repo.getEarnedBadges()

        assertEquals(2, api.getBadgesCallCount)
    }

    @Test
    fun `시계가 뒤로 가면 캐시를 만료시킨다`() = runTest {
        // 벽시계는 사용자·NTP 가 되돌릴 수 있다. `now - ts` 를 부호 없이 비교하면 음수가
        // `< TTL` 을 만족해 캐시가 영원히 만료되지 않는다.
        val clock = MutableClock(now = 1_000_000L)
        val api = FakeBadgesApi(badges = listOf(badge("first_workout")))
        val repo = BadgeRepositoryImpl(api, clock)

        repo.getEarnedBadges()
        clock.now -= 500_000L // 시계 역행
        repo.getEarnedBadges()

        assertEquals(2, api.getBadgesCallCount)
    }

    // ---- 무효화 ----

    @Test
    fun `awardBadge 직후의 hasBadge 는 새 배지를 본다`() = runTest {
        val api = FakeBadgesApi(badges = emptyList())
        val repo = BadgeRepositoryImpl(api)

        repo.getEarnedBadges() // 캐시 채움 (비어 있음)
        api.badges = listOf(badge("week_1_complete"))
        repo.awardBadge("week_1_complete")

        assertTrue(repo.hasBadge("week_1_complete").getOrThrow())
    }

    @Test
    fun `무효화는 되살아나지 않는다 — 진행 중 fetch 가 낡은 목록을 되돌려 쓰지 못한다`() = runTest {
        // 회귀 가드(설계 A2). 수정 전 시퀀스:
        //   T1 getEarnedBadges() → 캐시 미스 → fetch 시작 (새 배지 없는 목록)
        //   T2 awardBadge(X)     → 성공 → 캐시 무효화
        //   T1 fetch 완료        → 무효화를 덮어쓰고 낡은 목록을 캐시에 저장
        // 결과: 방금 딴 배지가 최대 60초간 보이지 않는다. 실패 로그도 남지 않는다.
        val gate = CompletableDeferred<Unit>()
        val api = FakeBadgesApi(badges = emptyList(), getBadgesGate = gate)
        val repo = BadgeRepositoryImpl(api)

        val inFlight = async { repo.getEarnedBadges() } // T1 — 게이트에서 멈춘다
        advanceUntilIdle()

        api.badges = listOf(badge("week_1_complete"))
        repo.awardBadge("week_1_complete") // T2 — 무효화
        advanceUntilIdle()

        gate.complete(Unit) // T1 완료 — 낡은(빈) 목록을 들고 돌아온다
        inFlight.await()

        // 낡은 결과가 캐시에 남았다면 아래가 false 로 떨어진다.
        assertTrue(repo.hasBadge("week_1_complete").getOrThrow())
    }

    @Test
    fun `awardBadge 실패는 캐시를 무효화하지 않는다`() = runTest {
        val api = FakeBadgesApi(badges = listOf(badge("first_workout")), awardFails = true)
        val repo = BadgeRepositoryImpl(api)

        repo.getEarnedBadges()
        assertTrue(repo.awardBadge("week_1_complete").isFailure)
        repo.getEarnedBadges()

        // 실패한 적립으로 캐시를 버리면 매 실패마다 불필요한 재조회가 생긴다.
        assertEquals(1, api.getBadgesCallCount)
    }

    // ---- 매핑 ----

    @Test
    fun `hasBadge 는 없는 키에 false 를 돌려준다`() = runTest {
        val repo = BadgeRepositoryImpl(FakeBadgesApi(badges = listOf(badge("first_workout"))))

        assertFalse(repo.hasBadge("week_1_complete").getOrThrow())
    }

    @Test
    fun `깨진 earnedAt 은 null 로 떨어질 뿐 전체를 실패시키지 않는다`() = runTest {
        val repo = BadgeRepositoryImpl(FakeBadgesApi(badges = listOf(badge("first_workout", earnedAt = "not-a-date"))))

        val badges = repo.getEarnedBadges().getOrThrow()
        assertEquals(1, badges.size)
        assertEquals(null, badges[0].earnedAt)
    }

    // ---- Helpers ----

    private fun badge(key: String, earnedAt: String = "2026-09-01T00:00:00Z") = BadgeResponse(badgeKey = key, earnedAt = earnedAt)

    private class MutableClock(var now: Long) : () -> Long {
        override fun invoke(): Long = now
    }

    private class FakeBadgesApi(
        var badges: List<BadgeResponse>,
        private val getBadgesGate: CompletableDeferred<Unit>? = null,
        private val awardFails: Boolean = false,
    ) : BadgesApi {
        var getBadgesCallCount = 0
            private set

        override suspend fun getBadges(): Response<List<BadgeResponse>> {
            getBadgesCallCount++
            // 서버는 *요청이 도달한 시점*의 상태를 돌려준다 — 응답을 게이트 통과 후에 읽으면
            // 그 사이의 적립이 섞여 들어와 "낡은 응답" 시나리오 자체가 재현되지 않는다.
            val snapshot = badges
            getBadgesGate?.await()
            return Response.success(snapshot)
        }

        override suspend fun awardBadge(key: String): Response<BadgeResponse> {
            if (awardFails) return Response.error(500, okhttp3.ResponseBody.create(null, ""))
            return Response.success(BadgeResponse(badgeKey = key, earnedAt = "2026-09-01T00:00:00Z"))
        }
    }
}
