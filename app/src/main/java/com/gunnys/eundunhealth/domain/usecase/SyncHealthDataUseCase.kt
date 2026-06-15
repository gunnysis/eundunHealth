package com.gunnys.eundunhealth.domain.usecase

import androidx.compose.runtime.Immutable
import com.gunnys.eundunhealth.domain.model.WeeklyPlan
import com.gunnys.eundunhealth.domain.model.reportToSentry
import com.gunnys.eundunhealth.domain.model.toAppError
import com.gunnys.eundunhealth.domain.repository.HealthRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * Health Connect 동기화 결과.
 *
 * 순수 read+compute 만 수행하고 서버 쓰기는 하지 않는다 (호출자가 [newlyCompletedDates] 를
 * 백그라운드로 푸시). 권한/가용성 신호를 함께 반환하여 호출자가 중복 조회 없이 UI 를 구성한다.
 */
@Immutable
data class HealthSyncResult(
    val plan: WeeklyPlan,
    val isAvailable: Boolean,
    val hasPermission: Boolean,
    val newlyCompletedDates: List<LocalDate>,
)

class SyncHealthDataUseCase @Inject constructor(
    private val healthRepo: HealthRepository,
) {
    suspend operator fun invoke(plan: WeeklyPlan): Result<HealthSyncResult> = runCatching {
        val available = healthRepo.isAvailable()
        if (!available || !healthRepo.hasPermissions()) {
            return@runCatching HealthSyncResult(
                plan = plan,
                isAvailable = available,
                hasPermission = false,
                newlyCompletedDates = emptyList(),
            )
        }

        val completedDates = healthRepo.getExerciseDatesThisWeek(plan.weekStart).getOrElse {
            it.toAppError().reportToSentry()
            emptyList()
        }

        // Health Connect 가 감지했지만 아직 미완료로 기록된 운동일만 추린다.
        // - 이미 완료된 날 제외 (중복 서버 푸시 방지)
        // - manuallySet(사용자가 명시적으로 토글한 날) 제외 → 수동 우선. 사용자가 해제한 날을
        //   HC 가 다시 완료로 덮어쓰던 회귀 차단.
        val newlyCompleted = plan.days
            .filter { !it.isRestDay && !it.isCompleted && !it.manuallySet && it.date in completedDates }
            .map { it.date }

        val updatedDays = plan.days.map { day ->
            if (day.date in newlyCompleted) day.copy(isCompleted = true) else day
        }

        HealthSyncResult(
            plan = plan.copy(days = updatedDays),
            isAvailable = true,
            hasPermission = true,
            newlyCompletedDates = newlyCompleted,
        )
    }
}
