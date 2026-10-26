package com.gunnys.eundunhealth.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressHubScreen(
    onHistoryClick: () -> Unit,
    onStatisticsClick: () -> Unit,
    onGoalClick: () -> Unit,
    onBadgesClick: () -> Unit,
    onSmileTrainingClick: () -> Unit = {},
) {
    Scaffold(topBar = { TopAppBar(title = { Text("진행") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("운동 기록과 목표", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            ProgressRow(Icons.Default.Insights, "통계", "주간과 월간 변화를 확인합니다", onStatisticsClick)
            ProgressRow(Icons.Default.History, "기록", "지난 운동 계획과 완료 기록을 봅니다", onHistoryClick)
            ProgressRow(Icons.Default.Flag, "목표", "운동 목표를 관리합니다", onGoalClick)
            ProgressRow(Icons.Default.EmojiEvents, "배지", "달성한 배지를 확인합니다", onBadgesClick)

            Spacer(Modifier.height(4.dp))
            Text("마음 연습", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            ProgressRow(
                icon = Icons.Default.SentimentSatisfiedAlt,
                title = "스마일 트레이닝",
                summary = "온디바이스 미소 피드백으로 표정 근육과 마음 연습을 합니다",
                onClick = onSmileTrainingClick,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgressRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(summary) },
            leadingContent = { Icon(icon, contentDescription = null) },
            trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
        )
    }
}
