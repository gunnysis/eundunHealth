package com.gunnys.eundunhealth.domain.usecase

import com.gunnys.eundunhealth.domain.model.CurrentWeekPlanRead
import com.gunnys.eundunhealth.domain.model.WeeklyPlan
import com.gunnys.eundunhealth.domain.model.WeeklyPlanAlreadyExistsException
import com.gunnys.eundunhealth.domain.model.WorkoutPlanMode
import com.gunnys.eundunhealth.domain.repository.UserRepository
import com.gunnys.eundunhealth.domain.repository.WorkoutRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * 선택한 운동 환경([WorkoutPlanMode])으로 현재 주의 운동 계획을 생성한다.
 *
 * - 프로필을 조회해 신체 정보 및 휴식일을 반영한다.
 * - 서버가 409 Conflict([WeeklyPlanAlreadyExistsException])를 반환하면, 이미 생성된 계획을
 *   덮어쓰지 않고 정확히 한 번 재조회(`loadCurrentWeekPlan`)해 반환한다.
 */
class CreateWeeklyPlanUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
    private val userRepo: UserRepository,
) {
    @Suppress("TooGenericExceptionCaught")
    suspend operator fun invoke(mode: WorkoutPlanMode): Result<WeeklyPlan> = try {
        val profile = userRepo.getProfile().getOrThrow()
            ?: error("프로필이 없습니다. 신체정보를 먼저 입력해주세요.")
        val created = workoutRepo.createWeeklyPlan(profile, mode).getOrElse { err ->
            if (err is WeeklyPlanAlreadyExistsException) {
                when (val read = workoutRepo.loadCurrentWeekPlan()) {
                    is CurrentWeekPlanRead.Found -> read.plan
                    is CurrentWeekPlanRead.Unreadable -> throw (read.error ?: error("계획 데이터를 읽을 수 없습니다"))
                    is CurrentWeekPlanRead.Failed -> throw read.error
                    CurrentWeekPlanRead.Missing -> throw err
                }
            } else {
                throw err
            }
        }
        Result.success(created)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
