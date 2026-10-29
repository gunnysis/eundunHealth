package com.gunnys.eundunhealth.data.repository

import com.gunnys.eundunhealth.api.generated.api.ProfileApi
import com.gunnys.eundunhealth.api.generated.model.DefaultWorkoutPlanModeRequest
import com.gunnys.eundunhealth.api.generated.model.UserProfileRequest
import com.gunnys.eundunhealth.api.generated.model.UserProfileResponse
import com.gunnys.eundunhealth.data.remote.util.bodyOrNull404
import com.gunnys.eundunhealth.data.remote.util.bodyOrThrow
import com.gunnys.eundunhealth.domain.model.Gender
import com.gunnys.eundunhealth.domain.model.UserProfile
import com.gunnys.eundunhealth.domain.model.WorkoutPlanMode
import com.gunnys.eundunhealth.domain.model.normalizeRestDays
import com.gunnys.eundunhealth.domain.model.runSuspendCatching
import com.gunnys.eundunhealth.domain.model.toWorkoutPlanMode
import com.gunnys.eundunhealth.domain.repository.UserRepository
import retrofit2.HttpException
import java.math.BigDecimal
import javax.inject.Inject
import com.gunnys.eundunhealth.api.generated.model.WorkoutPlanMode as ApiWorkoutPlanMode

class UserRepositoryImpl @Inject constructor(
    private val api: ProfileApi,
) : UserRepository {

    override suspend fun getProfile(): Result<UserProfile?> = runSuspendCatching {
        val dto = api.getProfile().bodyOrNull404() ?: return@runSuspendCatching null
        dto.toDomain()
    }

    override suspend fun saveProfile(profile: UserProfile): Result<Unit> = runSuspendCatching {
        val normalizedRestDays = normalizeRestDays(profile.restDays)
        val response = api.updateProfile(
            UserProfileRequest(
                heightCm = BigDecimal.valueOf(profile.heightCm.toDouble()),
                weightKg = BigDecimal.valueOf(profile.weightKg.toDouble()),
                gender = profile.gender.name.lowercase(),
                bodyFatPct = profile.bodyFatPercent?.let { BigDecimal.valueOf(it.toDouble()) },
                muscleMassKg = profile.muscleMassKg?.let { BigDecimal.valueOf(it.toDouble()) },
                restDays = normalizedRestDays,
            ),
        )
        if (!response.isSuccessful) throw HttpException(response)
    }

    override suspend fun updateDefaultWorkoutPlanMode(mode: WorkoutPlanMode?): Result<UserProfile> = runSuspendCatching {
        val dto = api.updateDefaultWorkoutPlanModeV2(
            DefaultWorkoutPlanModeRequest(
                defaultWorkoutPlanMode = when (mode) {
                    WorkoutPlanMode.GYM -> ApiWorkoutPlanMode.gym
                    WorkoutPlanMode.HOME -> ApiWorkoutPlanMode.home
                    null -> null
                },
            ),
        ).bodyOrThrow()
        dto.toDomain()
    }

    private fun UserProfileResponse.toDomain(): UserProfile = UserProfile(
        userId = userId,
        heightCm = heightCm.toFloat(),
        weightKg = weightKg.toFloat(),
        gender = runCatching { Gender.valueOf(gender.uppercase()) }
            .getOrDefault(Gender.UNSPECIFIED),
        bodyFatPercent = bodyFatPct?.toFloat(),
        muscleMassKg = muscleMassKg?.toFloat(),
        restDays = normalizeRestDays(restDays),
        defaultWorkoutPlanMode = defaultWorkoutPlanMode?.value?.toWorkoutPlanMode(),
    )
}
