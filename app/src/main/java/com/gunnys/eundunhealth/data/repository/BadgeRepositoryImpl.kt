package com.gunnys.eundunhealth.data.repository

import com.gunnys.eundunhealth.api.generated.api.BadgesApi
import com.gunnys.eundunhealth.api.generated.model.BadgeResponse
import com.gunnys.eundunhealth.data.remote.util.bodyOrThrow
import com.gunnys.eundunhealth.domain.model.Badge
import com.gunnys.eundunhealth.domain.model.BadgeCatalog
import com.gunnys.eundunhealth.domain.repository.BadgeRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject

/**
 * 배지 조회 결과를 60초 캐시한다. `@Singleton`(`RepositoryModule`)이라 **앱 전체가 한 인스턴스를
 * 공유**한다 — 홈의 배지 적립과 배지 화면의 목록 조회가 서로 다른 코루틴에서 동시에 들어온다.
 *
 * 그래서 캐시 상태는 세 가지를 동시에 만족해야 한다:
 *
 * 1. **원자적 갱신** — `Mutex` 로 읽기·쓰기를 직렬화한다. `synchronized` 는 suspend 함수를
 *    감쌀 수 없어 쓸 수 없고, 보호하지 않으면 다른 스레드가 쓰기를 아예 못 볼 수도 있다.
 * 2. **되살아나지 않는 무효화** — [cacheGeneration] 참조. fetch 는 락 밖에서 하므로,
 *    그 사이에 일어난 무효화를 fetch 완료가 덮어써선 안 된다.
 * 3. **단조적이지 않은 시계에 안전** — [isFresh] 참조.
 */
class BadgeRepositoryImpl internal constructor(
    private val api: BadgesApi,
    private val nowMs: () -> Long,
) : BadgeRepository {

    /** Hilt 진입점 — 프로덕션은 항상 시스템 시계. 두 번째 인자는 테스트가 시간을 제어하기 위한 것. */
    @Inject
    constructor(api: BadgesApi) : this(api, System::currentTimeMillis)

    private val mutex = Mutex()
    private var cachedBadges: List<BadgeResponse>? = null
    private var cacheTimestamp: Long = 0L

    /**
     * 무효화 세대. `awardBadge` 성공 시 증가한다.
     *
     * fetch 를 락 안에서 하면 네트워크 왕복 동안 `awardBadge` 가 통째로 블록되므로 락 밖에서
     * 한다. 그러면 다음이 가능해진다 —
     * ```
     * T1 fetch 시작(새 배지 없는 응답)  →  T2 awardBadge 성공(무효화)  →  T1 fetch 완료
     * ```
     * T1 이 무조건 결과를 쓰면 **무효화가 되살아나** 방금 딴 배지가 최대 60초간 사라진다.
     * 실패도 로그도 없이 조용히 틀리는 종류다. 그래서 fetch 시작 시 세대를 기억하고,
     * **완료 시점에 세대가 그대로일 때만** 캐시에 쓴다.
     */
    private var cacheGeneration: Long = 0L

    /**
     * `now - ts` 를 부호 없이 비교하면 안 된다.
     *
     * `System.currentTimeMillis()` 는 사용자·NTP 가 되돌릴 수 있는 벽시계다. 시계가 역행하면
     * 경과가 **음수**가 되고 `< TTL` 을 만족해 캐시가 영원히 만료되지 않는다. 범위로 검사해
     * 역행(음수)과 정상 만료(TTL 이상)를 모두 "만료" 로 떨어뜨린다 — 안전한 쪽은 재조회다.
     */
    private fun isFresh(now: Long): Boolean = (now - cacheTimestamp) in 0 until CACHE_TTL_MS

    private suspend fun getOrFetchBadges(): List<BadgeResponse> {
        val generationAtStart = mutex.withLock {
            val cached = cachedBadges
            if (cached != null && isFresh(nowMs())) return cached
            cacheGeneration
        }

        val fresh = api.getBadges().bodyOrThrow()

        mutex.withLock {
            // 이 fetch 가 도는 동안 무효화가 있었으면 결과를 버린다(응답 자체는 그대로 반환).
            if (cacheGeneration == generationAtStart) {
                cachedBadges = fresh
                cacheTimestamp = nowMs()
            }
        }
        return fresh
    }

    override suspend fun getEarnedBadges(): Result<List<Badge>> = runCatching {
        getOrFetchBadges().map { it.toDomain() }
    }

    override suspend fun awardBadge(badgeKey: String): Result<Badge> = runCatching {
        // 적립이 성공했을 때만 무효화한다 — 실패까지 무효화하면 매 실패마다 불필요한 재조회가 붙는다.
        val dto = api.awardBadge(badgeKey).bodyOrThrow()
        mutex.withLock {
            cachedBadges = null
            cacheTimestamp = 0L
            cacheGeneration++
        }
        dto.toDomain()
    }

    override suspend fun hasBadge(badgeKey: String): Result<Boolean> = runCatching {
        getOrFetchBadges().any { it.badgeKey == badgeKey }
    }

    private fun BadgeResponse.toDomain(): Badge {
        val (name, desc) = BadgeCatalog.getInfo(badgeKey)
        return Badge(
            key = badgeKey,
            name = name,
            description = desc,
            earnedAt = runCatching { Instant.parse(earnedAt) }.getOrNull(),
        )
    }

    private companion object {
        const val CACHE_TTL_MS = 60_000L // 1분
    }
}
