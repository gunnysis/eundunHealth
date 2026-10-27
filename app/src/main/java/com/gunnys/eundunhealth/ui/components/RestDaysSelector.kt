package com.gunnys.eundunhealth.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gunnys.eundunhealth.domain.model.MAX_REST_DAYS
import com.gunnys.eundunhealth.domain.model.TOTAL_DAYS_IN_WEEK
import com.gunnys.eundunhealth.domain.model.normalizeRestDays

internal val DAY_SHORT_LABELS = listOf("월", "화", "수", "목", "금", "토", "일")
private val DAY_FULL_LABELS = listOf("월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일")

@Composable
fun RestDaysSelector(
    restDays: List<Int>,
    onRestDaysChange: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedSet = normalizeRestDays(restDays).toSet()
    var boundaryMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val workoutDaysCount = TOTAL_DAYS_IN_WEEK - selectedSet.size

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "휴식 요일 선택",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(4.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Text(
                text = "운동 ${workoutDaysCount}일 · 휴식 ${selectedSet.size}일",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "충분한 회복을 위해 쉬고 싶은 요일을 1~6개 선택하세요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(12.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DAY_SHORT_LABELS.forEachIndexed { idx, label ->
                val dayNumber = idx + 1
                val isChecked = dayNumber in selectedSet
                val fullDayName = DAY_FULL_LABELS[idx]
                FilterChip(
                    selected = isChecked,
                    onClick = {
                        val checked = !isChecked
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
                    label = { Text(text = label) },
                    leadingIcon = if (isChecked) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.sizeIn(maxWidth = 18.dp, maxHeight = 18.dp),
                            )
                        }
                    } else {
                        null
                    },
                    colors = FilterChipDefaults.filterChipColors(),
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics {
                            contentDescription = "$fullDayName 휴식일"
                        },
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val restDayNames = selectedSet.sorted().joinToString("·") { DAY_SHORT_LABELS[it - 1] }
        val workoutDays = (1..TOTAL_DAYS_IN_WEEK).filter { it !in selectedSet }
        val workoutDayNames = workoutDays.joinToString("·") { DAY_SHORT_LABELS[it - 1] }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "휴식일 ($restDayNames)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "운동일 ($workoutDayNames)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        boundaryMessage?.let { msg ->
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                        error(msg)
                    },
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.sizeIn(maxWidth = 16.dp, maxHeight = 16.dp),
                    )
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RestDaysSelectorPreview() {
    MaterialTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            RestDaysSelector(
                restDays = listOf(6, 7),
                onRestDaysChange = {},
            )
        }
    }
}
