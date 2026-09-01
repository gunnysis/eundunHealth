package com.gunnys.eundunhealth.domain.usecase

import androidx.compose.runtime.Immutable
import com.gunnys.eundunhealth.domain.model.DailyActivity
import com.gunnys.eundunhealth.domain.model.toReportedAppError
import com.gunnys.eundunhealth.domain.repository.HealthRepository
import javax.inject.Inject

@Immutable
data class TodayActivityResult(val activity: DailyActivity?, val hasPermission: Boolean)

/**
 * 오늘의 활동을 읽어온다. 비가용/무권한이면 hasPermission=false + activity=null.
 * read 실패는 Sentry 보고 후 activity=null 로 degrade (PR #83 패턴) — 호출은 성공.
 */
class GetTodayActivityUseCase @Inject constructor(
    private val healthRepo: HealthRepository,
) {
    suspend operator fun invoke(): Result<TodayActivityResult> = runCatching {
        if (!healthRepo.isAvailable() || !healthRepo.hasDailyActivityPermissions()) {
            return@runCatching TodayActivityResult(activity = null, hasPermission = false)
        }
        val activity = healthRepo.getTodayActivity().getOrElse {
            it.toReportedAppError()
            null
        }
        TodayActivityResult(activity = activity, hasPermission = true)
    }
}
