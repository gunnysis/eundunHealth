package com.gunnys.eundunhealth.domain.usecase

import com.gunnys.eundunhealth.domain.model.BodyComposition
import com.gunnys.eundunhealth.domain.repository.HealthRepository
import javax.inject.Inject

/**
 * Health Connect 최신 체성분을 읽어온다. 비가용/무권한이면 null(=가져올 것 없음).
 * read 실패는 Result.failure 로 전파한다 — "기록 없음(null)" 과 구분해야 사용자에게
 * 거짓 메시지("가져올 기록이 없습니다")가 노출되지 않는다 (v0.1.9 사전점검 수정).
 * Sentry 보고는 호출자(ProfileViewModel) 의 onFailure 에서 수행.
 */
class ImportBodyCompositionUseCase @Inject constructor(
    private val healthRepo: HealthRepository,
) {
    suspend operator fun invoke(): Result<BodyComposition?> = runCatching {
        if (!healthRepo.isAvailable() || !healthRepo.hasBodyCompositionPermissions()) {
            return@runCatching null
        }
        healthRepo.getLatestBodyComposition().getOrThrow()
    }
}
