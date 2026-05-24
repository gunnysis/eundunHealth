package com.gunnys.eundunhealth.domain.repository

import com.gunnys.eundunhealth.domain.model.Goal
import com.gunnys.eundunhealth.domain.model.GoalType
import com.gunnys.eundunhealth.domain.model.ProfileHistoryPoint

interface GoalRepository {
    suspend fun getGoals(): Result<List<Goal>>
    suspend fun upsertGoal(type: GoalType, targetValue: Float): Result<Goal>
    suspend fun getProfileHistory(limit: Int = 50): Result<List<ProfileHistoryPoint>>
}
