package com.gunnys.eundunhealth.domain.repository

import com.gunnys.eundunhealth.domain.model.UserProfile
import com.gunnys.eundunhealth.domain.model.WorkoutPlanMode

interface UserRepository {
    suspend fun getProfile(): Result<UserProfile?>
    suspend fun saveProfile(profile: UserProfile): Result<Unit>
    suspend fun updateDefaultWorkoutPlanMode(mode: WorkoutPlanMode?): Result<UserProfile>
}
