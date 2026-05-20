package com.gunnys.eundunhealth.data.repository

import com.gunnys.eundunhealth.data.remote.api.EundunApi
import com.gunnys.eundunhealth.data.remote.api.dto.UserProfileRequest
import com.gunnys.eundunhealth.domain.model.UserProfile
import com.gunnys.eundunhealth.domain.repository.UserRepository
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val api: EundunApi
) : UserRepository {

    override suspend fun getProfile(): Result<UserProfile?> = runCatching {
        val dto = api.getProfile()
        UserProfile(
            userId = dto.userId,
            heightCm = dto.heightCm,
            weightKg = dto.weightKg,
            bodyFatPercent = dto.bodyFatPct ?: 0f,
            muscleMassKg = dto.muscleMassKg ?: 0f
        )
    }

    override suspend fun saveProfile(profile: UserProfile): Result<Unit> = runCatching {
        api.updateProfile(
            UserProfileRequest(
                heightCm = profile.heightCm,
                weightKg = profile.weightKg,
                bodyFatPct = profile.bodyFatPercent,
                muscleMassKg = profile.muscleMassKg
            )
        )
    }
}
