package com.gunnys.eundunhealth.data.repository

import com.gunnys.eundunhealth.data.remote.api.EundunApi
import com.gunnys.eundunhealth.data.remote.api.dto.BadgeDto
import com.gunnys.eundunhealth.domain.model.Badge
import com.gunnys.eundunhealth.domain.model.BadgeCatalog
import com.gunnys.eundunhealth.domain.repository.BadgeRepository
import java.time.Instant
import javax.inject.Inject

class BadgeRepositoryImpl @Inject constructor(
    private val api: EundunApi
) : BadgeRepository {

    private var cachedBadges: List<BadgeDto>? = null
    private var cacheTimestamp: Long = 0L

    private suspend fun getOrFetchBadges(): List<BadgeDto> {
        val now = System.currentTimeMillis()
        val cached = cachedBadges
        if (cached != null && now - cacheTimestamp < CACHE_TTL_MS) {
            return cached
        }
        val fresh = api.getBadges()
        cachedBadges = fresh
        cacheTimestamp = now
        return fresh
    }

    override suspend fun getEarnedBadges(): Result<List<Badge>> = runCatching {
        getOrFetchBadges().map { dto -> dto.toDomain() }
    }

    override suspend fun awardBadge(badgeKey: String): Result<Badge> = runCatching {
        val dto = api.awardBadge(badgeKey)
        // invalidate cache so hasBadge/getEarnedBadges가 즉시 최신화된다
        cachedBadges = null
        cacheTimestamp = 0L
        dto.toDomain()
    }

    override suspend fun hasBadge(badgeKey: String): Result<Boolean> = runCatching {
        getOrFetchBadges().any { it.badgeKey == badgeKey }
    }

    private fun BadgeDto.toDomain(): Badge {
        val (name, desc) = BadgeCatalog.getInfo(badgeKey)
        return Badge(
            id = id,
            userId = userId,
            key = badgeKey,
            name = name,
            description = desc,
            earnedAt = earnedAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
        )
    }

    private companion object {
        const val CACHE_TTL_MS = 60_000L  // 1분
    }
}
