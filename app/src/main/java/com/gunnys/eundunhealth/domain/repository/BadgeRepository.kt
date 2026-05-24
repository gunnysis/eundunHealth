package com.gunnys.eundunhealth.domain.repository

import com.gunnys.eundunhealth.domain.model.Badge

interface BadgeRepository {
    suspend fun getEarnedBadges(): Result<List<Badge>>
    suspend fun awardBadge(badgeKey: String): Result<Badge>
    suspend fun hasBadge(badgeKey: String): Result<Boolean>
}
