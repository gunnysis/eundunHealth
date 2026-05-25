package com.gunnys.eundunhealth.data.repository

import com.gunnys.eundunhealth.api.generated.api.ProfileApi
import com.gunnys.eundunhealth.api.generated.model.UserProfileRequest
import com.gunnys.eundunhealth.domain.model.UserProfile
import com.gunnys.eundunhealth.domain.repository.UserRepository
import retrofit2.HttpException
import java.math.BigDecimal
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val api: ProfileApi,
) : UserRepository {

    override suspend fun getProfile(): Result<UserProfile?> = try {
        val response = api.getProfile()
        when {
            response.code() == 404 -> Result.success(null)
            !response.isSuccessful -> Result.failure(HttpException(response))
            else -> {
                val dto = response.body() ?: return Result.failure(IllegalStateException("Empty profile body"))
                Result.success(
                    UserProfile(
                        userId = dto.userId,
                        heightCm = dto.heightCm.toFloat(),
                        weightKg = dto.weightKg.toFloat(),
                        bodyFatPercent = dto.bodyFatPct?.toFloat() ?: 0f,
                        muscleMassKg = dto.muscleMassKg?.toFloat() ?: 0f,
                        restDay = dto.restDay ?: 7,
                    ),
                )
            }
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun saveProfile(profile: UserProfile): Result<Unit> = runCatching {
        val response = api.updateProfile(
            UserProfileRequest(
                heightCm = BigDecimal.valueOf(profile.heightCm.toDouble()),
                weightKg = BigDecimal.valueOf(profile.weightKg.toDouble()),
                bodyFatPct = BigDecimal.valueOf(profile.bodyFatPercent.toDouble()),
                muscleMassKg = BigDecimal.valueOf(profile.muscleMassKg.toDouble()),
                restDay = profile.restDay,
            ),
        )
        if (!response.isSuccessful) throw HttpException(response)
    }
}
