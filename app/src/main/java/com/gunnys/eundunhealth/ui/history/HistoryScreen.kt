package com.gunnys.eundunhealth.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gunnys.eundunhealth.domain.model.DayPlan
import com.gunnys.eundunhealth.domain.model.WeeklyPlan
import com.gunnys.eundunhealth.ui.components.EmptyContent
import com.gunnys.eundunhealth.ui.components.ErrorContent
import com.gunnys.eundunhealth.ui.home.PlanModeBadge
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val WEEK_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadNextPage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("운동 기록") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading && uiState.plans.isEmpty(),
            onRefresh = viewModel::refresh,
            state = pullState,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                uiState.error != null && uiState.plans.isEmpty() -> {
                    uiState.error?.let { error ->
                        ErrorContent(
                            error = error,
                            onRetry = viewModel::refresh,
                        )
                    }
                }
                uiState.plans.isEmpty() && !uiState.isLoading -> {
                    EmptyContent(message = "아직 운동 기록이 없습니다")
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(uiState.plans, key = { it.id }) { plan ->
                            HistoryWeekCard(plan)
                        }
                        if (uiState.isLoading) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryWeekCard(plan: WeeklyPlan) {
    val weekEnd = plan.weekStart.plusDays(6)
    val workoutDays = plan.days.count { !it.isRestDay }
    val completedDays = plan.days.count { !it.isRestDay && it.isCompleted }
    val rate = if (workoutDays > 0) completedDays.toFloat() / workoutDays else 0f
    var expanded by rememberSaveable(plan.id) { mutableStateOf(false) }

    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "${plan.weekStart.format(WEEK_DATE_FORMATTER)} - ${weekEnd.format(WEEK_DATE_FORMATTER)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    PlanModeBadge(mode = plan.mode)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$completedDays/${workoutDays}일",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "상세 접기" else "상세 펼치기",
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { rate },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                strokeCap = StrokeCap.Round,
            )
            Spacer(modifier = Modifier.height(10.dp))
            HistoryDayStampsRow(days = plan.days)

            AnimatedVisibility(visible = expanded) {
                HistoryWeekExpandedDetail(days = plan.days)
            }
        }
    }
}

@Composable
private fun HistoryDayStampsRow(days: List<DayPlan>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        days.forEach { day ->
            val shortDay = remember(day.date) {
                day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = when {
                        day.isRestDay -> Icons.Outlined.RemoveCircleOutline
                        day.isCompleted -> Icons.Default.CheckCircle
                        else -> Icons.Outlined.Circle
                    },
                    contentDescription = when {
                        day.isRestDay -> "$shortDay 휴식"
                        day.isCompleted -> "$shortDay 완료"
                        else -> "$shortDay 미완료"
                    },
                    tint = when {
                        day.isRestDay -> MaterialTheme.colorScheme.outlineVariant
                        day.isCompleted -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outline
                    },
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = shortDay,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HistoryWeekExpandedDetail(days: List<DayPlan>) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(8.dp))
        days.forEach { day ->
            val fullDay = remember(day.date) {
                "${day.date.format(WEEK_DATE_FORMATTER)} (${
                    day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)
                })"
            }
            val summary = when {
                day.isRestDay -> "휴식일"
                day.exercises.isEmpty() -> if (day.isCompleted) "완료됨" else "미완료"
                else -> {
                    val names = day.exercises.joinToString(", ") { it.name }
                    val status = if (day.isCompleted) "완료" else "미완료"
                    "$status · $names"
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = fullDay,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (day.isCompleted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                )
            }
        }
    }
}
