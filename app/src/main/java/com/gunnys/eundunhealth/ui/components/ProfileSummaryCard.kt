package com.gunnys.eundunhealth.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gunnys.eundunhealth.domain.model.TOTAL_DAYS_IN_WEEK
import com.gunnys.eundunhealth.domain.model.normalizeRestDays

@Composable
fun ProfileSummaryCard(
    height: Float,
    weight: Float,
    bodyFat: Float?,
    muscleMass: Float?,
    title: String = "입력 요약",
    restDays: List<Int>? = null,
    modifier: Modifier = Modifier,
) {
    val bodyCompText = when {
        muscleMass != null && bodyFat != null ->
            "${"%.1f".format(muscleMass)}kg · ${"%.1f".format(bodyFat)}%"
        muscleMass != null ->
            "골격근량 ${"%.1f".format(muscleMass)}kg · 체지방률 미입력"
        bodyFat != null ->
            "골격근량 미입력 · 체지방률 ${"%.1f".format(bodyFat)}%"
        else ->
            "미입력 (선택 항목)"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            SummaryEntry(
                label = "키 / 몸무게",
                value = "${"%.0f".format(height)}cm · ${"%.1f".format(weight)}kg",
            )
            Spacer(modifier = Modifier.height(6.dp))
            SummaryEntry(
                label = "골격근량 / 체지방률",
                value = bodyCompText,
            )
            restDays?.let { days ->
                val normalized = normalizeRestDays(days)
                val dayNames = normalized.joinToString("·") { DAY_SHORT_LABELS[it - 1] }
                val workoutDays = TOTAL_DAYS_IN_WEEK - normalized.size
                Spacer(modifier = Modifier.height(6.dp))
                SummaryEntry(
                    label = "주간 루틴",
                    value = "주 ${workoutDays}일 운동 · 휴식 ($dayNames)",
                    valueColor = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun SummaryEntry(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            textAlign = TextAlign.End,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileSummaryCardPreview() {
    MaterialTheme {
        ProfileSummaryCard(
            height = 175f,
            weight = 70.5f,
            bodyFat = null,
            muscleMass = null,
            restDays = listOf(6, 7),
        )
    }
}
