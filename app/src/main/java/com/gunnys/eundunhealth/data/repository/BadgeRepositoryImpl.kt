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

    override suspend fun getEarnedBadges(): Result<List<Badge>> = runCatching {
        val dtos = api.getBadges()
        cachedBadges = dtos
        dtos.map { dto ->
            val (name, desc) = BadgeCatalog.getInfo(dto.badgeKey)
            Badge(dto.id, dto.userId, dto.badgeKey, name, desc,
                dto.earnedAt?.let { try { Instant.parse(it) } catch (_: Exception) { null } })
        }
    }

    override suspend fun awardBadge(badgeKey: String): Result<Badge> = runCatching {
        val dto = api.awardBadge(badgeKey)
        cachedBadges = null  // invalidate cache
        val (name, desc) = BadgeCatalog.getInfo(badgeKey)
        Badge(dto.id, dto.userId, dto.badgeKey, name, desc,
            dto.earnedAt?.let { try { Instant.parse(it) } catch (_: Exception) { null } })
    }

    override suspend fun hasBadge(badgeKey: String): Boolean {
        val badges = cachedBadges ?: try {
            api.getBadges().also { cachedBadges = it }
        } catch (_: Exception) { return false }
        return badges.any { it.badgeKey == badgeKey }
    }
}
