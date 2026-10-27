package com.gunnys.eundunhealth.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gunnys.eundunhealth.domain.model.MAX_REST_DAYS
import com.gunnys.eundunhealth.domain.model.TOTAL_DAYS_IN_WEEK
import com.gunnys.eundunhealth.domain.model.normalizeRestDays

private data class RestDayPreset(
    val label: String,
    val restDays: List<Int>,
)

@Suppress("MagicNumber")
private val REST_DAY_PRESETS = listOf(
    RestDayPreset(label = "주 3일 운동 (화·목·토·일 휴식)", restDays = listOf(2, 4, 6, 7)),
    RestDayPreset(label = "주 5일 운동 (토·일 휴식)", restDays = listOf(6, 7)),
    RestDayPreset(label = "주 6일 운동 (일 휴식)", restDays = listOf(7)),
)

internal val DAY_SHORT_LABELS = listOf("월", "화", "수", "목", "금", "토", "일")
private val DAY_FULL_LABELS = listOf("월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RestDaysSelector(
    restDays: List<Int>,
    onRestDaysChange: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedSet = normalizeRestDays(restDays).toSet()
    var boundaryMessage by rememberSaveable { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "휴식 요일 선택",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "쉬고 싶은 요일을 1~6개 선택하세요. 빠른 프리셋으로 시작할 수도 있어요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            REST_DAY_PRESETS.forEach { preset ->
                val isPresetSelected = selectedSet == preset.restDays.toSet()
                FilterChip(
                    selected = isPresetSelected,
                    onClick = {
                        boundaryMessage = null
                        onRestDaysChange(preset.restDays)
                    },
                    label = { Text(preset.label) },
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        MultiChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            DAY_SHORT_LABELS.forEachIndexed { idx, label ->
                val dayNumber = idx + 1
                val isChecked = dayNumber in selectedSet
                val fullDayName = DAY_FULL_LABELS[idx]
                SegmentedButton(
                    checked = isChecked,
                    onCheckedChange = { checked ->
                        val updated = if (checked) selectedSet + dayNumber else selectedSet - dayNumber
                        when {
                            updated.isEmpty() -> {
                                boundaryMessage = "회복을 위해 최소 1일의 휴식일이 필요해요"
                            }
                            updated.size > MAX_REST_DAYS -> {
                                boundaryMessage = "최소 1일 이상은 운동일이어야 주간 계획을 만들 수 있어요"
                            }
                            else -> {
                                boundaryMessage = null
                                onRestDaysChange(updated.sorted())
                            }
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = idx, count = DAY_SHORT_LABELS.size),
                    icon = {},
                    modifier = Modifier.semantics {
                        contentDescription = "$fullDayName 휴식"
                    },
                ) {
                    Text(label)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        val workoutDaysCount = TOTAL_DAYS_IN_WEEK - selectedSet.size
        val selectedDayNames = selectedSet.sorted().joinToString("·") { DAY_SHORT_LABELS[it - 1] }
        Text(
            text = "주 ${workoutDaysCount}일 운동 · 주 ${selectedSet.size}일 휴식 ($selectedDayNames)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )

        boundaryMessage?.let { msg ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}
