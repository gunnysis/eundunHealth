package com.gunnys.eundunhealth.data.repository

import com.gunnys.eundunhealth.data.remote.api.EundunApi
import com.gunnys.eundunhealth.domain.model.Badge
import com.gunnys.eundunhealth.domain.repository.BadgeRepository
import java.time.Instant
import javax.inject.Inject

class BadgeRepositoryImpl @Inject constructor(
    private val api: EundunApi
) : BadgeRepository {

    private val badgeInfo = mapOf(
        "week_1_complete" to ("1주 완료" to "첫 번째 주간 목표를 달성했습니다"),
        "week_2_complete" to ("2주 연속" to "2주 연속 목표를 달성했습니다"),
        "streak_3weeks" to ("3주 연속" to "3주 연속 목표를 달성했습니다")
    )

    override suspend fun getEarnedBadges(): Result<List<Badge>> = runCatching {
        api.getBadges().map { dto ->
            val (name, desc) = badgeInfo[dto.badgeKey] ?: (dto.badgeKey to "")
            Badge(dto.id, dto.userId, dto.badgeKey, name, desc,
                dto.earnedAt?.let { Instant.parse(it) })
        }
    }

    override suspend fun awardBadge(badgeKey: String): Result<Badge> = runCatching {
        val dto = api.awardBadge(badgeKey)
        val (name, desc) = badgeInfo[badgeKey] ?: (badgeKey to "")
        Badge(dto.id, dto.userId, dto.badgeKey, name, desc,
            dto.earnedAt?.let { Instant.parse(it) })
    }

    override suspend fun hasBadge(badgeKey: String): Boolean =
        try {
            api.getBadges().any { it.badgeKey == badgeKey }
        } catch (_: Exception) { false }
}
