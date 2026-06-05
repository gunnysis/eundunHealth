package com.gunnys.eundunhealth.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gunnys.eundunhealth.data.preferences.ThemeMode
import com.gunnys.eundunhealth.domain.model.DayPlan
import com.gunnys.eundunhealth.ui.components.ErrorContent
import com.gunnys.eundunhealth.ui.components.SkeletonHomeContent
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onExerciseClick: (String) -> Unit,
    onBadgesClick: () -> Unit,
    onHistoryClick: () -> Unit = {},
    onStatisticsClick: () -> Unit = {},
    onGoalClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onLogout: () -> Unit,
    onRequestHealthPermissions: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val pullState = rememberPullToRefreshState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is HomeSideEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("이번 주 운동 계획") },
                actions = {
                    HomeTopBarActions(
                        themeMode = themeMode,
                        onProfileClick = onProfileClick,
                        onCycleTheme = { viewModel.cycleTheme() },
                        onRefresh = { viewModel.loadPlan() },
                        onHistoryClick = onHistoryClick,
                        onStatisticsClick = onStatisticsClick,
                        onGoalClick = onGoalClick,
                        onBadgesClick = onBadgesClick,
                        onLogout = onLogout,
                    )
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState is HomeUiState.Loading,
            onRefresh = { viewModel.loadPlan() },
            state = pullState,
            modifier = Modifier.padding(padding),
        ) {
            when (val state = uiState) {
                is HomeUiState.Loading -> {
                    SkeletonHomeContent()
                }
                is HomeUiState.Error -> {
                    ErrorContent(
                        error = state.error,
                        onRetry = { viewModel.loadPlan() },
                    )
                }
                is HomeUiState.Success -> {
                    LazyColumn(contentPadding = padding) {
                        item {
                            WeeklyProgressCard(
                                completedCount = state.completedCount,
                                totalDays = state.totalWorkoutDays,
                                completionRate = state.completionRate,
                            )
                        }
                        if (!state.hasHealthPermission) {
                            item { HealthConnectPromptCard(onRequest = onRequestHealthPermissions) }
                        }
                        items(state.plan.days, key = { it.date.toString() }) { day ->
                            DayPlanCard(
                                day = day,
                                onExerciseClick = onExerciseClick,
                                onToggleComplete = { viewModel.toggleDayCompletion(day.date) },
                            )
                        }
                    }
                }
            }
        } // PullToRefreshBox
    }
}

@Composable
private fun HomeTopBarActions(
    themeMode: ThemeMode,
    onProfileClick: () -> Unit,
    onCycleTheme: () -> Unit,
    onRefresh: () -> Unit,
    onHistoryClick: () -> Unit,
    onStatisticsClick: () -> Unit,
    onGoalClick: () -> Unit,
    onBadgesClick: () -> Unit,
    onLogout: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "새로고침") }
    Box {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(Icons.Default.MoreVert, "더보기")
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("프로필") },
                onClick = {
                    menuExpanded = false
                    onProfileClick()
                },
                leadingIcon = { Icon(Icons.Default.Person, null) },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        when (themeMode) {
                            ThemeMode.SYSTEM -> "테마 (시스템)"
                            ThemeMode.DARK -> "테마 (다크)"
                            ThemeMode.LIGHT -> "테마 (라이트)"
                        },
                    )
                },
                onClick = {
                    menuExpanded = false
                    onCycleTheme()
                },
                leadingIcon = {
                    Icon(
                        when (themeMode) {
                            ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                            ThemeMode.DARK -> Icons.Default.DarkMode
                            ThemeMode.LIGHT -> Icons.Default.LightMode
                        },
                        null,
                    )
                },
            )
            DropdownMenuItem(
                text = { Text("기록") },
                onClick = {
                    menuExpanded = false
                    onHistoryClick()
                },
                leadingIcon = { Icon(Icons.Default.History, null) },
            )
            DropdownMenuItem(
                text = { Text("통계") },
                onClick = {
                    menuExpanded = false
                    onStatisticsClick()
                },
                leadingIcon = { Icon(Icons.Default.QueryStats, null) },
            )
            DropdownMenuItem(
                text = { Text("목표") },
                onClick = {
                    menuExpanded = false
                    onGoalClick()
                },
                leadingIcon = { Icon(Icons.Default.Flag, null) },
            )
            DropdownMenuItem(
                text = { Text("배지") },
                onClick = {
                    menuExpanded = false
                    onBadgesClick()
                },
                leadingIcon = { Icon(Icons.Default.EmojiEvents, null) },
            )
            DropdownMenuItem(
                text = { Text("로그아웃") },
                onClick = {
                    menuExpanded = false
                    onLogout()
                },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) },
            )
        }
    }
}

@Composable
private fun HealthConnectPromptCard(onRequest: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.FitnessCenter, "Health Connect")
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Health Connect로 운동 달성을 자동 추적",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onRequest) { Text("연동") }
        }
    }
}

@Composable
fun WeeklyProgressCard(completedCount: Int, totalDays: Int, completionRate: Float) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "이번 주 진행률",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { completionRate },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "$completedCount/$totalDays 완료 (${(completionRate * 100).toInt()}%)",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun DayPlanCard(day: DayPlan, onExerciseClick: (String) -> Unit, onToggleComplete: () -> Unit) {
    val dayName = day.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)
    val dateStr = "${day.date.monthValue}/${day.date.dayOfMonth}"
    val containerColor by animateColorAsState(
        if (day.isCompleted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        label = "cardColor",
    )

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("$dayName ($dateStr)", style = MaterialTheme.typography.titleMedium)
                }
                if (!day.isRestDay) {
                    IconButton(onClick = onToggleComplete) {
                        if (day.isCompleted) {
                            Icon(Icons.Default.CheckCircle, "완료 해제", tint = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(Icons.Outlined.Circle, "완료 처리", tint = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
            if (day.isRestDay) {
                Text(
                    "휴식일",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                day.exercises.forEach { exercise ->
                    TextButton(
                        onClick = { onExerciseClick(exercise.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                            Text(
                                "${exercise.name}  ${exercise.sets}x${exercise.reps}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}
