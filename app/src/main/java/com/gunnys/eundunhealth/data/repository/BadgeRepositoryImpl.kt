package com.gunnys.eundunhealth.data.repository

import com.gunnys.eundunhealth.api.generated.api.BadgesApi
import com.gunnys.eundunhealth.api.generated.model.BadgeResponse
import com.gunnys.eundunhealth.data.remote.util.bodyOrThrow
import com.gunnys.eundunhealth.domain.model.Badge
import com.gunnys.eundunhealth.domain.model.BadgeCatalog
import com.gunnys.eundunhealth.domain.repository.BadgeRepository
import java.time.Instant
import javax.inject.Inject

class BadgeRepositoryImpl @Inject constructor(
    private val api: BadgesApi,
) : BadgeRepository {

    private var cachedBadges: List<BadgeResponse>? = null
    private var cacheTimestamp: Long = 0L

    private suspend fun getOrFetchBadges(): List<BadgeResponse> {
        val now = System.currentTimeMillis()
        val cached = cachedBadges
        if (cached != null && now - cacheTimestamp < CACHE_TTL_MS) {
            return cached
        }
        val fresh = api.getBadges().bodyOrThrow()
        cachedBadges = fresh
        cacheTimestamp = now
        return fresh
    }

    override suspend fun getEarnedBadges(): Result<List<Badge>> = runCatching {
        getOrFetchBadges().map { it.toDomain() }
    }

    override suspend fun awardBadge(badgeKey: String): Result<Badge> = runCatching {
        val dto = api.awardBadge(badgeKey).bodyOrThrow()
        cachedBadges = null
        cacheTimestamp = 0L
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
