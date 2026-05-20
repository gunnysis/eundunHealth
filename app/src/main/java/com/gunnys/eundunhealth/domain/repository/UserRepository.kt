package com.gunnys.eundunhealth.domain.repository

import com.gunnys.eundunhealth.domain.model.UserProfile

interface UserRepository {
    suspend fun getProfile(): Result<UserProfile?>
    suspend fun saveProfile(profile: UserProfile): Result<Unit>
}
