package com.gunnys.eundunhealth.ui.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gunnys.eundunhealth.domain.model.Statistics
import com.gunnys.eundunhealth.domain.model.WorkoutPlanMode
import com.gunnys.eundunhealth.ui.components.EmptyContent
import com.gunnys.eundunhealth.ui.components.ErrorContent
import com.gunnys.eundunhealth.ui.components.LineChart
import java.time.format.DateTimeFormatter

private val WEEK_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onBack: () -> Unit,
    viewModel: StatisticsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("통계") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                    }
                },
            )
        },
    ) { padding ->
        when (val state = uiState) {
            is StatisticsUiState.Loading -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is StatisticsUiState.Empty -> {
                EmptyContent(
                    message = "아직 통계로 보여줄 운동 기록이 없습니다",
                    modifier = Modifier.padding(padding),
                )
            }
            is StatisticsUiState.Error -> {
                ErrorContent(
                    error = state.error,
                    modifier = Modifier.padding(padding),
                    onRetry = { viewModel.load() },
                )
            }
            is StatisticsUiState.Loaded -> {
                StatisticsContent(state.data, modifier = Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun StatisticsContent(stats: Statistics, modifier: Modifier = Modifier) {
    val avgPercent = remember(stats.weeklyRates) {
        if (stats.weeklyRates.isEmpty()) {
            0
        } else {
            (stats.weeklyRates.map { it.completionRate }.average() * 100).toInt()
        }
    }
    val chartA11ySummary = remember(stats.weeklyRates, avgPercent) {
        val maxEntry = stats.weeklyRates.maxByOrNull { it.completionRate }
        val maxText = maxEntry?.let {
            "최고 ${it.weekStart.format(WEEK_FORMATTER)} 주간 ${(it.completionRate * 100).toInt()}%"
        }.orEmpty()
        "주간 완료율 추이 차트: 최근 ${stats.weeklyRates.size}주 평균 완료율 $avgPercent%, $maxText"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 스트릭 카드
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StreakCard("현재 스트릭", stats.currentStreak, Modifier.weight(1f))
            StreakCard("최장 스트릭", stats.longestStreak, Modifier.weight(1f))
        }

        // 완료율 추이 차트 (TalkBack 요약 포함)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "주간 완료율 추이",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "최근 ${stats.weeklyRates.size}주 · 평균 완료율 $avgPercent%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                CompletionRateChart(
                    stats = stats,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .semantics { contentDescription = chartA11ySummary },
                )
            }
        }

        // 운동 환경 비율 (헬스장 vs 홈트레이닝)
        WorkoutModeRatioCard(stats = stats)
    }
}

@Composable
private fun WorkoutModeRatioCard(stats: Statistics) {
    val total = stats.weeklyRates.size.coerceAtLeast(1)
    val gymCount = stats.weeklyRates.count { it.mode == WorkoutPlanMode.GYM }
    val homeCount = stats.weeklyRates.count { it.mode == WorkoutPlanMode.HOME }
    val gymRatio = gymCount.toFloat() / total

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "운동 환경 비율",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "헬스장 ${gymCount}주",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "홈트레이닝 ${homeCount}주",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { gymRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.tertiaryContainer,
                strokeCap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun StreakCard(label: String, value: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "${value}주",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun CompletionRateChart(stats: Statistics, modifier: Modifier = Modifier) {
    val yValues = stats.weeklyRates.map { (it.completionRate * 100).toDouble() }
    val labels = stats.weeklyRates.map { it.weekStart.format(WEEK_FORMATTER) }
    LineChart(yValues = yValues, modifier = modifier, xLabels = labels)
}
