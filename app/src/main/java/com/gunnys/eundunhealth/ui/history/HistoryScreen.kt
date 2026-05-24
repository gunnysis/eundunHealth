package com.gunnys.eundunhealth.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gunnys.eundunhealth.domain.model.WeeklyPlan
import com.gunnys.eundunhealth.ui.components.EmptyContent
import com.gunnys.eundunhealth.ui.components.ErrorContent
import java.time.format.DateTimeFormatter

// 카드마다 ofPattern을 호출하던 비용 제거 — Pattern은 immutable이므로 안전한 싱글톤
private val WEEK_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val error by viewModel.error.collectAsState()
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
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading && uiState.plans.isEmpty(),
            onRefresh = {
                viewModel.clearError()
                // 페이지를 0으로 되돌리려면 새로운 fixture가 필요하지만 우선 다음 페이지 로드로 폴백
                viewModel.loadNextPage()
            },
            state = pullState,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                error != null && uiState.plans.isEmpty() -> {
                    ErrorContent(
                        error = error!!,
                        onRetry = {
                            viewModel.clearError()
                            viewModel.loadNextPage()
                        },
                    )
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

@Composable
fun HistoryWeekCard(plan: WeeklyPlan) {
    val weekEnd = plan.weekStart.plusDays(6)
    val workoutDays = plan.days.count { !it.isRestDay }
    val completedDays = plan.days.count { !it.isRestDay && it.isCompleted }
    val rate = if (workoutDays > 0) completedDays.toFloat() / workoutDays else 0f

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "${plan.weekStart.format(WEEK_DATE_FORMATTER)} - ${weekEnd.format(WEEK_DATE_FORMATTER)}",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { rate },
                modifier = Modifier.fillMaxWidth().height(6.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                plan.days.forEach { day ->
                    if (!day.isRestDay) {
                        Icon(
                            if (day.isCompleted) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                            contentDescription = null,
                            tint = if (day.isCompleted) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "${completedDays}/${workoutDays}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
