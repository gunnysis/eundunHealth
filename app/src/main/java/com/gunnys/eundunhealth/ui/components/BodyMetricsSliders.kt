package com.gunnys.eundunhealth.ui.components

import androidx.compose.runtime.Composable

/**
 * 신체 4지표 슬라이더(키/몸무게/골격근량/체지방률) — 범위·단위가 검증 계약이라 단일화.
 * Onboarding·Profile 공유. (ProfileScreen 에서 promote)
 */
@Composable
fun BodyMetricsSliders(
    height: Float,
    onHeightChange: (Float) -> Unit,
    weight: Float,
    onWeightChange: (Float) -> Unit,
    muscleMass: Float,
    onMuscleMassChange: (Float) -> Unit,
    bodyFat: Float,
    onBodyFatChange: (Float) -> Unit,
) {
    ProfileSlider("키", height, 140f..210f, "cm", 0, onHeightChange)
    ProfileSlider("몸무게", weight, 40f..150f, "kg", 1, onWeightChange)
    ProfileSlider("골격근량", muscleMass, 10f..60f, "kg", 1, onMuscleMassChange)
    ProfileSlider("체지방률", bodyFat, 5f..50f, "%", 1, onBodyFatChange)
}
