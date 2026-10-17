package com.gunnys.eundunhealth.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gunnys.eundunhealth.domain.model.DayPlan
import com.gunnys.eundunhealth.domain.model.Exercise
import com.gunnys.eundunhealth.domain.model.ExerciseType
import com.gunnys.eundunhealth.domain.model.WeeklyPlan
import com.gunnys.eundunhealth.ui.components.AuthErrorBanner
import com.gunnys.eundunhealth.ui.components.ErrorContent
import com.gunnys.eundunhealth.ui.components.SkeletonHomeContent
import com.gunnys.eundunhealth.ui.home.components.HealthConnectPromptCard
import com.gunnys.eundunhealth.ui.home.components.HealthConnectUnavailableCard
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val DAY_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onExerciseClick: (String) -> Unit,
    onRequestHealthPermissions: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pullState = rememberPullToRefreshState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("이번 주 운동 계획") },
                actions = {
                    IconButton(onClick = viewModel::loadPlan) {
                        Icon(Icons.Default.Refresh, contentDescription = "운동 계획 새로고침")
                    }
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
                    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                        state.toggleError?.let { err ->
                            item {
                                AuthErrorBanner(
                                    error = err,
                                    screen = "home_toggle",
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                            }
                        }
                        item {
                            WeeklyProgressCard(
                                plan = state.plan,
                                completedCount = state.completedCount,
                                totalDays = state.totalWorkoutDays,
                                completionRate = state.completionRate,
                                today = state.today,
                            )
                        }
                        if (!state.isHealthConnectAvailable) {
                            item { HealthConnectUnavailableCard() }
                        } else if (!state.hasHealthPermission) {
                            item { HealthConnectPromptCard(onRequest = onRequestHealthPermissions) }
                        }
                        items(state.plan.days, key = { it.date.toString() }) { day ->
                            // Compose Strong Skipping 최적화: LocalDate(외부 타입) 대신 원시 Boolean 전달
                            DayPlanCard(
                                day = day,
                                isToday = day.date == state.today,
                                onExerciseClick = onExerciseClick,
                                onToggleComplete = { viewModel.toggleDayCompletion(day.date) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WeeklyProgressCard(
    plan: WeeklyPlan,
    completedCount: Int,
    totalDays: Int,
    completionRate: Float,
    today: LocalDate,
) {
    val weekRangeLabel = remember(plan.weekStart) {
        val end = plan.weekStart.plusDays(6)
        "${plan.weekStart.format(DAY_DATE_FORMATTER)} - ${end.format(DAY_DATE_FORMATTER)}"
    }
    val animatedProgress by animateFloatAsState(
        targetValue = completionRate.coerceIn(0f, 1f),
        label = "weeklyProgress",
    )
    val percentInt = (completionRate * 100).toInt()

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "이번 주 진행률",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "$weekRangeLabel · $completedCount/${totalDays}일 운동 완료",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Text(
                        "$percentInt%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.18f),
                strokeCap = StrokeCap.Round,
            )
            if (plan.days.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                )
                Spacer(modifier = Modifier.height(12.dp))
                WeeklyDayStampRow(days = plan.days, today = today)
            }
        }
    }
}

@Composable
private fun WeeklyDayStampRow(days: List<DayPlan>, today: LocalDate) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        days.forEach { day ->
            DayStampItem(day = day, isToday = day.date == today)
        }
    }
}

@Composable
private fun DayStampItem(day: DayPlan, isToday: Boolean) {
    val shortDayName = remember(day.date) {
        day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)
    }
    val statusDesc = when {
        day.isCompleted -> "운동 완료"
        day.isRestDay -> "휴식일"
        else -> "운동 예정"
    }
    val todayPrefix = if (isToday) "오늘, " else ""

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "$todayPrefix$shortDayName $statusDesc"
        },
    ) {
        Text(
            text = shortDayName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (isToday) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
            },
        )
        Spacer(modifier = Modifier.height(6.dp))
        when {
            day.isCompleted -> {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            day.isRestDay -> {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "-",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                    )
                }
            }
            else -> {
                val borderWidth = if (isToday) 2.dp else 1.dp
                val borderColor = if (isToday) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.35f)
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .border(borderWidth, borderColor, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${day.date.dayOfMonth}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
fun DayPlanCard(
    day: DayPlan,
    isToday: Boolean,
    onExerciseClick: (String) -> Unit,
    onToggleComplete: () -> Unit,
) {
    // 날짜 라벨은 day.date 가 바뀔 때만 재계산 — 토글 색 애니메이션(animateColorAsState)으로
    // 카드가 빈번히 recompose 되는데, getDisplayName(ResourceBundle lookup)을 매번 돌리지 않는다.
    val dayLabel = remember(day.date) {
        val name = day.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)
        "$name (${day.date.format(DAY_DATE_FORMATTER)})"
    }
    val targetContainerColor = when {
        day.isCompleted -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        isToday && !day.isRestDay -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        day.isRestDay -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.surface
    }
    val containerColor by animateColorAsState(targetContainerColor, label = "cardColor")
    val cardBorder = if (isToday) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        CardDefaults.outlinedCardBorder()
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = cardBorder,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            DayPlanCardHeader(
                dayLabel = dayLabel,
                day = day,
                isToday = isToday,
                onToggleComplete = onToggleComplete,
            )
            if (!day.isRestDay && day.exercises.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    day.exercises.forEach { exercise ->
                        ExerciseRow(
                            exercise = exercise,
                            onClick = { onExerciseClick(exercise.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayPlanCardHeader(
    dayLabel: String,
    day: DayPlan,
    isToday: Boolean,
    onToggleComplete: () -> Unit,
) {
    val subtitle = when {
        day.isRestDay -> "휴식일"
        day.isCompleted -> "운동 ${day.exercises.size}종목 · 완료됨"
        else -> "운동 ${day.exercises.size}종목"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = dayLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold,
                )
                if (isToday) {
                    Spacer(modifier = Modifier.width(8.dp))
                    TodayBadge()
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (day.isCompleted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        if (!day.isRestDay) {
            IconButton(onClick = onToggleComplete) {
                if (day.isCompleted) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "완료 해제",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Circle,
                        contentDescription = "완료 처리",
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayBadge() {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Text(
            text = "오늘",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun ExerciseRow(
    exercise: Exercise,
    onClick: () -> Unit,
) {
    val typeIcon = if (exercise.type == ExerciseType.CARDIO) {
        Icons.AutoMirrored.Filled.DirectionsRun
    } else {
        Icons.Default.FitnessCenter
    }
    val metaText = remember(exercise.bodyPart, exercise.equipment) {
        listOf(exercise.bodyPart, exercise.equipment)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = typeIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (metaText.isNotEmpty()) {
                    Text(
                        text = metaText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    text = "${exercise.sets}×${exercise.reps}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
