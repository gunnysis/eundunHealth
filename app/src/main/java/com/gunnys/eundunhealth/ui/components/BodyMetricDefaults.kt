package com.gunnys.eundunhealth.ui.components

import com.gunnys.eundunhealth.domain.model.Gender

/**
 * 한국 성인 표준 중앙값 기반 신체 지표 초기 앵커.
 */
object BodyMetricDefaults {
    const val DEFAULT_MALE_HEIGHT_CM = 174f
    const val DEFAULT_MALE_WEIGHT_KG = 72.0f
    const val DEFAULT_FEMALE_HEIGHT_CM = 161f
    const val DEFAULT_FEMALE_WEIGHT_KG = 54.0f

    fun defaultHeightFor(gender: Gender): Float = when (gender) {
        Gender.FEMALE -> DEFAULT_FEMALE_HEIGHT_CM
        Gender.MALE, Gender.UNSPECIFIED -> DEFAULT_MALE_HEIGHT_CM
    }

    fun defaultWeightFor(gender: Gender): Float = when (gender) {
        Gender.FEMALE -> DEFAULT_FEMALE_WEIGHT_KG
        Gender.MALE, Gender.UNSPECIFIED -> DEFAULT_MALE_WEIGHT_KG
    }
}
