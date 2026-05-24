package com.gunnys.eundunhealth.data.repository

import com.gunnys.eundunhealth.data.remote.api.EundunApi
import com.gunnys.eundunhealth.data.remote.api.dto.GoalDto
import com.gunnys.eundunhealth.data.remote.api.dto.GoalRequest
import com.gunnys.eundunhealth.data.remote.api.dto.ProfileHistoryEntryDto
import com.gunnys.eundunhealth.domain.model.Goal
import com.gunnys.eundunhealth.domain.model.GoalType
import com.gunnys.eundunhealth.domain.model.ProfileHistoryPoint
import com.gunnys.eundunhealth.domain.repository.GoalRepository
import java.time.Instant
import javax.inject.Inject

class GoalRepositoryImpl @Inject constructor(
    private val api: EundunApi,
) : GoalRepository {

    override suspend fun getGoals(): Result<List<Goal>> = runCatching {
        api.getGoals().mapNotNull { it.toDomain() }
    }

    override suspend fun upsertGoal(type: GoalType, targetValue: Float): Result<Goal> = runCatching {
        val dto = api.upsertGoal(GoalRequest(goalType = type.key, targetValue = targetValue))
        dto.toDomain() ?: error("서버 응답의 goal_type을 해석할 수 없습니다: ${dto.goalType}")
    }

    override suspend fun getProfileHistory(limit: Int): Result<List<ProfileHistoryPoint>> = runCatching {
        api.getProfileHistory(limit).map { it.toDomain() }
    }

    private fun GoalDto.toDomain(): Goal? {
        val gt = GoalType.fromKey(goalType) ?: return null
        return Goal(
            type = gt,
            targetValue = targetValue,
            createdAt = runCatching { Instant.parse(createdAt) }.getOrNull(),
        )
    }

    private fun ProfileHistoryEntryDto.toDomain(): ProfileHistoryPoint = ProfileHistoryPoint(
        heightCm = heightCm,
        weightKg = weightKg,
        bodyFatPct = bodyFatPct,
        muscleMassKg = muscleMassKg,
        recordedAt = runCatching { Instant.parse(recordedAt) }.getOrNull(),
    )
}
