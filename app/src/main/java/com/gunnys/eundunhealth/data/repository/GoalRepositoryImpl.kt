package com.gunnys.eundunhealth.data.repository

import com.gunnys.eundunhealth.api.generated.api.GoalsApi
import com.gunnys.eundunhealth.api.generated.api.ProfileApi
import com.gunnys.eundunhealth.api.generated.model.GoalRequest
import com.gunnys.eundunhealth.api.generated.model.GoalResponse
import com.gunnys.eundunhealth.api.generated.model.ProfileHistoryEntry
import com.gunnys.eundunhealth.data.remote.util.bodyOrThrow
import com.gunnys.eundunhealth.domain.model.Goal
import com.gunnys.eundunhealth.domain.model.GoalType
import com.gunnys.eundunhealth.domain.model.ProfileHistoryPoint
import com.gunnys.eundunhealth.domain.repository.GoalRepository
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

class GoalRepositoryImpl @Inject constructor(
    private val goalsApi: GoalsApi,
    private val profileApi: ProfileApi,
) : GoalRepository {

    override suspend fun getGoals(): Result<List<Goal>> = runCatching {
        goalsApi.getGoals().bodyOrThrow().mapNotNull { dto ->
            dto.toDomain() ?: run {
                // 알 수 없는 goalType 은 silent 하게 사라지면 데이터 손실처럼 보인다 → 관측 가능하게 보고.
                Sentry.addBreadcrumb(
                    Breadcrumb().apply {
                        category = "goal.unknown_type_dropped"
                        level = SentryLevel.WARNING
                        setData("goalType", dto.goalType)
                    },
                )
                null
            }
        }
    }

    override suspend fun upsertGoal(type: GoalType, targetValue: Float): Result<Goal> = runCatching {
        // GoalRequest.GoalType은 generated nested enum — Android GoalType.key("weight"/"body_fat")와 1:1.
        val dto = goalsApi.upsertGoal(
            GoalRequest(
                goalType = GoalRequest.GoalType.valueOf(type.key),
                targetValue = BigDecimal.valueOf(targetValue.toDouble()),
            ),
        ).bodyOrThrow()
        dto.toDomain() ?: error("서버 응답의 goalType을 해석할 수 없습니다: ${dto.goalType}")
    }

    override suspend fun getProfileHistory(limit: Int): Result<List<ProfileHistoryPoint>> = runCatching {
        profileApi.getProfileHistory(limit).bodyOrThrow().map { it.toDomain() }
    }

    private fun GoalResponse.toDomain(): Goal? {
        val gt = GoalType.fromKey(goalType) ?: return null
        return Goal(
            type = gt,
            targetValue = targetValue.toFloat(),
            createdAt = runCatching { Instant.parse(createdAt) }.getOrNull(),
        )
    }

    private fun ProfileHistoryEntry.toDomain(): ProfileHistoryPoint = ProfileHistoryPoint(
        heightCm = heightCm.toFloat(),
        weightKg = weightKg.toFloat(),
        bodyFatPct = bodyFatPct?.toFloat(),
        muscleMassKg = muscleMassKg?.toFloat(),
        recordedAt = runCatching { Instant.parse(recordedAt) }.getOrNull(),
    )
}
