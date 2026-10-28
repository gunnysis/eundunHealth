package com.gunnys.eundunhealth.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 선택 사항인 체성분(골격근량·체지방률) 접이식 입력 카드.
 *
 * - 기본 상태: 접힘(`null` 유지). 인바디를 모르는 사용자가 억지로 가짜 기본값을 저장하지 않도록 보호한다 (`F-3`).
 * - 사용자가 카드를 펼치면 수치 피커를 노출하고, `[체성분 입력 비우기]` 버튼으로 언제든 `null`로 되돌릴 수 있다.
 */
@Composable
fun OptionalBodyCompositionCard(
    muscleMassKg: Float?,
    onMuscleMassChange: (Float?) -> Unit,
    bodyFatPct: Float?,
    onBodyFatChange: (Float?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasInitialValues = muscleMassKg != null || bodyFatPct != null
    var expanded by rememberSaveable { mutableStateOf(hasInitialValues) }

    LaunchedEffect(hasInitialValues) {
        if (hasInitialValues) {
            expanded = true
        }
    }

    val summarySubtitle = when {
        muscleMassKg != null && bodyFatPct != null ->
            "골격근량 ${"%.1f".format(muscleMassKg)}kg · 체지방률 ${"%.1f".format(bodyFatPct)}%"
        muscleMassKg != null ->
            "골격근량 ${"%.1f".format(muscleMassKg)}kg · 체지방률 미입력"
        bodyFatPct != null ->
            "골격근량 미입력 · 체지방률 ${"%.1f".format(bodyFatPct)}%"
        else ->
            "모르셔도 괜찮습니다. 비워두면 키와 몸무게 기준으로 맞춤 루틴을 생성합니다."
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "인바디(체성분) 결과도 알고 계신가요? (선택)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = summarySubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = {
                        val next = !expanded
                        expanded = next
                        if (next && muscleMassKg == null && bodyFatPct == null) {
                            onMuscleMassChange(30.0f)
                            onBodyFatChange(20.0f)
                        }
                    },
                ) {
                    Text(if (expanded) "접기" else "입력하기")
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    HybridBodyMetricPicker(
                        label = "골격근량 (선택)",
                        value = muscleMassKg ?: 30.0f,
                        range = 10f..60f,
                        step = 0.5f,
                        unit = "kg",
                        decimals = 1,
                        onValueChange = { onMuscleMassChange(it) },
                    )
                    HybridBodyMetricPicker(
                        label = "체지방률 (선택)",
                        value = bodyFatPct ?: 20.0f,
                        range = 5f..50f,
                        step = 0.5f,
                        unit = "%",
                        decimals = 1,
                        onValueChange = { onBodyFatChange(it) },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = {
                                onMuscleMassChange(null)
                                onBodyFatChange(null)
                                expanded = false
                            },
                        ) {
                            Text("체성분 입력 비우기 (미입력으로 저장)")
                        }
                    }
                }
            }
        }
    }
}
