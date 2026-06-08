package com.gunnys.eundunhealth.domain.usecase

import com.gunnys.eundunhealth.domain.model.BodyComposition
import com.gunnys.eundunhealth.domain.model.reportToSentry
import com.gunnys.eundunhealth.domain.model.toAppError
import com.gunnys.eundunhealth.domain.repository.HealthRepository
import javax.inject.Inject

/**
 * Health Connect 최신 체성분을 읽어온다. 비가용/무권한이면 null.
 * read 실패는 Sentry 보고 후 null 로 degrade (PR #83 패턴) — 호출 자체는 성공.
 */
class ImportBodyCompositionUseCase @Inject constructor(
    private val healthRepo: HealthRepository,
) {
    suspend operator fun invoke(): Result<BodyComposition?> = runCatching {
        if (!healthRepo.isAvailable() || !healthRepo.hasBodyCompositionPermissions()) {
            return@runCatching null
        }
        healthRepo.getLatestBodyComposition().getOrElse {
            it.toAppError().reportToSentry()
            null
        }
    }
}
