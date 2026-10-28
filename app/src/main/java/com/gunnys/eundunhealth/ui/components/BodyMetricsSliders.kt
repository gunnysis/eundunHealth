package com.gunnys.eundunhealth.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 신체 핵심 지표(키/몸무게) 하이브리드 피커 + 선택적 체성분(골격근량/체지방률) 접이식 카드 묶음.
 * Onboarding·Profile 공유.
 */
@Composable
fun BodyMetricsSliders(
    height: Float,
    onHeightChange: (Float) -> Unit,
    weight: Float,
    onWeightChange: (Float) -> Unit,
    muscleMass: Float?,
    onMuscleMassChange: (Float?) -> Unit,
    bodyFat: Float?,
    onBodyFatChange: (Float?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HybridBodyMetricPicker(
            label = "키 (필수)",
            value = height,
            range = 140f..210f,
            step = 1f,
            unit = "cm",
            decimals = 0,
            onValueChange = onHeightChange,
        )
        HybridBodyMetricPicker(
            label = "몸무게 (필수)",
            value = weight,
            range = 40f..150f,
            step = 0.5f,
            unit = "kg",
            decimals = 1,
            onValueChange = onWeightChange,
        )
        OptionalBodyCompositionCard(
            muscleMassKg = muscleMass,
            onMuscleMassChange = onMuscleMassChange,
            bodyFatPct = bodyFat,
            onBodyFatChange = onBodyFatChange,
        )
    }
}
