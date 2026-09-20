package com.gunnys.eundunhealth.data.repository

import com.gunnys.eundunhealth.api.generated.api.ProfileApi
import com.gunnys.eundunhealth.api.generated.model.UserProfileRequest
import com.gunnys.eundunhealth.data.remote.util.bodyOrNull404
import com.gunnys.eundunhealth.domain.model.UserProfile
import com.gunnys.eundunhealth.domain.repository.UserRepository
import retrofit2.HttpException
import java.math.BigDecimal
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val api: ProfileApi,
) : UserRepository {

    override suspend fun getProfile(): Result<UserProfile?> = runCatching {
        val dto = api.getProfile().bodyOrNull404() ?: return@runCatching null
        UserProfile(
            userId = dto.userId,
            heightCm = dto.heightCm.toFloat(),
            weightKg = dto.weightKg.toFloat(),
            gender = runCatching { com.gunnys.eundunhealth.domain.model.Gender.valueOf(dto.gender.uppercase()) }
                .getOrDefault(com.gunnys.eundunhealth.domain.model.Gender.UNSPECIFIED),
            bodyFatPercent = dto.bodyFatPct?.toFloat(),
            muscleMassKg = dto.muscleMassKg?.toFloat(),
            restDay = dto.restDay ?: 7,
        )
    }

    override suspend fun saveProfile(profile: UserProfile): Result<Unit> = runCatching {
        val response = api.updateProfile(
            UserProfileRequest(
                heightCm = BigDecimal.valueOf(profile.heightCm.toDouble()),
                weightKg = BigDecimal.valueOf(profile.weightKg.toDouble()),
                gender = profile.gender.name.lowercase(),
                bodyFatPct = profile.bodyFatPercent?.let { BigDecimal.valueOf(it.toDouble()) },
                muscleMassKg = profile.muscleMassKg?.let { BigDecimal.valueOf(it.toDouble()) },
                restDay = profile.restDay,
            ),
        )
        if (!response.isSuccessful) throw HttpException(response)
    }
}
