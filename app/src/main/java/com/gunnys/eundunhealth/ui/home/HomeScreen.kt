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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.model.DayPlan
import com.gunnys.eundunhealth.domain.model.Exercise
import com.gunnys.eundunhealth.domain.model.ExercisePrescriptionUnit
import com.gunnys.eundunhealth.domain.model.ExerciseType
import com.gunnys.eundunhealth.domain.model.PlanSource
import com.gunnys.eundunhealth.domain.model.WeeklyPlan
import com.gunnys.eundunhealth.domain.model.WorkoutPlanMode
import com.gunnys.eundunhealth.ui.components.AuthErrorBanner
import com.gunnys.eundunhealth.ui.components.ErrorContent
import com.gunnys.eundunhealth.ui.components.SkeletonHomeContent
import com.gunnys.eundunhealth.ui.home.components.HealthConnectPromptCard
import com.gunnys.eundunhealth.ui.home.components.HealthConnectUnavailableCard
import com.gunnys.eundunhealth.ui.home.components.TodayHeroCard
import com.gunnys.eundunhealth.ui.util.ObserveAsEvents
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val DAY_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d")

fun WorkoutPlanMode.displayLabel(): String = when (this) {
    WorkoutPlanMode.GYM -> "헬스장"
    WorkoutPlanMode.HOME -> "홈트레이닝"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onExerciseClick: (String) -> Unit,
    onRequestHealthPermissions: () -> Unit,
    onStartWorkoutGuide: (LocalDate) -> Unit = {},
    onNavigateToSmileTraining: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pullState = rememberPullToRefreshState()
    val snackbarHostState = remember { SnackbarHostState() }

    ObserveAsEvents(viewModel.sideEffect) { effect ->
        when (effect) {
            is HomeSideEffect.ShowUndoUncompleteSnackbar -> {
                val result = snackbarHostState.showSnackbar(
                    message = "운동 완료 표시가 해제되었습니다",
                    actionLabel = "되돌리기",
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.undoUncomplete(effect.date)
                }
            }
        }
    }

    val isRefreshing = (uiState as? HomeUiState.Success)?.isRefreshing == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("오늘의 운동") },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.loadPlan() },
            state = pullState,
            modifier = Modifier.padding(padding),
        ) {
            when (val state = uiState) {
                is HomeUiState.Loading -> {
                    SkeletonHomeContent()
                }
                is HomeUiState.NoPlan -> {
                    WorkoutPlanModeSelectionContent(
                        selectedMode = state.selectedMode,
                        isCreating = false,
                        createError = state.createError,
                        onSelectMode = viewModel::selectMode,
                        onCreatePlan = viewModel::createSelectedPlan,
                    )
                }
                is HomeUiState.Creating -> {
                    WorkoutPlanModeSelectionContent(
                        selectedMode = state.selectedMode,
                        isCreating = true,
                        createError = null,
                        onSelectMode = {},
                        onCreatePlan = {},
                    )
                }
                is HomeUiState.AutoCreating -> {
                    AutoCreatingContent(mode = state.mode)
                }
                is HomeUiState.AutoCreateError -> {
                    AutoCreateErrorContent(
                        mode = state.mode,
                        error = state.error,
                        onRetry = viewModel::retryAutoCreate,
                    )
                }
                is HomeUiState.PlanDataError -> {
                    PlanDataErrorContent(
                        source = state.source,
                        resetError = state.resetError,
                        isResetting = state.isResetting,
                        onRefresh = viewModel::loadPlan,
                        onResetPlan = viewModel::resetCorruptedPlan,
                    )
                }
                is HomeUiState.Error -> {
                    ErrorContent(
                        error = state.error,
                        onRetry = { viewModel.loadPlan() },
                    )
                }
                is HomeUiState.Success -> {
                    val todayPlan = remember(state.plan.days, state.today) {
                        state.plan.days.find { it.date == state.today }
                    }
                    val showSmileCard = !state.isSmileShortcutDismissedToday &&
                        (todayPlan == null || todayPlan.isRestDay || todayPlan.isCompleted)

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
                            TodayHeroCard(
                                todayPlan = todayPlan,
                                todayDate = state.today,
                                mode = state.plan.mode,
                                onStartWorkoutGuide = onStartWorkoutGuide,
                                onExerciseClick = onExerciseClick,
                                onToggleComplete = viewModel::toggleDayCompletion,
                            )
                        }
                        if (showSmileCard) {
                            item {
                                SmileTrainingContextualCard(
                                    onStartSmile = onNavigateToSmileTraining,
                                    onDismissToday = viewModel::dismissSmileShortcutToday,
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
                        item {
                            WeeklyPlanExpandHeader(
                                isExpanded = state.isWeeklyListExpanded,
                                onToggle = viewModel::toggleWeeklyListExpanded,
                            )
                        }
                        if (state.isWeeklyListExpanded) {
                            items(state.plan.days, key = { it.date.toString() }) { day ->
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
}

@Composable
private fun SmileTrainingContextualCard(
    onStartSmile: () -> Unit,
    onDismissToday: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Face,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "가벼운 미소 훈련으로 마무리해 볼까요?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "온디바이스 안면 근육 운동으로 기분까지 상쾌하게",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    )
                }
                IconButton(onClick = onDismissToday) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "오늘 하루 닫기",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismissToday) {
                    Text("오늘 하루 닫기")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onStartSmile) {
                    Text("미소 훈련 시작")
                }
            }
        }
    }
}

@Composable
private fun WeeklyPlanExpandHeader(
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        onClick = onToggle,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .heightIn(min = 48.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isExpanded) "이번 주 전체 계획 접기" else "이번 주 전체 계획 보기 (7일)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "접기" else "펼치기",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun WorkoutPlanModeSelectionContent(
    selectedMode: WorkoutPlanMode?,
    isCreating: Boolean,
    createError: AppError?,
    onSelectMode: (WorkoutPlanMode) -> Unit,
    onCreatePlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "이번 주 운동 환경을 선택하세요",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "선택한 환경에 맞춰 이번 주 7일 운동 계획을 생성합니다. 매주 자동으로 생성하려면 프로필에서 기본 운동 환경을 설정하세요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        createError?.let { err ->
            AuthErrorBanner(error = err, screen = "home_create_plan")
        }

        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WorkoutPlanModeOptionCard(
                title = "헬스장",
                description = "헬스장 — 장비를 사용하는 운동 계획",
                selected = selectedMode == WorkoutPlanMode.GYM,
                enabled = !isCreating,
                onClick = { onSelectMode(WorkoutPlanMode.GYM) },
            )
            WorkoutPlanModeOptionCard(
                title = "홈트레이닝",
                description = "홈트레이닝 — 장비 없이 하는 기본 동작 계획",
                selected = selectedMode == WorkoutPlanMode.HOME,
                enabled = !isCreating,
                onClick = { onSelectMode(WorkoutPlanMode.HOME) },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onCreatePlan,
            enabled = selectedMode != null && !isCreating,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            if (isCreating) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(20.dp)
                        .padding(end = 8.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text("이번 주 계획을 만드는 중...")
            } else {
                Text("이번 주 계획 만들기")
            }
        }
    }
}

@Composable
private fun WorkoutPlanModeOptionCard(
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val borderWidth = if (selected) 2.dp else 1.dp
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(12.dp))
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(borderWidth, borderColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AutoCreatingContent(mode: WorkoutPlanMode) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "기본 운동 환경으로 이번 주 계획을 만드는 중",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "선택된 환경: ${mode.displayLabel()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AutoCreateErrorContent(
    mode: WorkoutPlanMode,
    error: AppError,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "기본 운동 환경(${mode.displayLabel()})으로 계획을 만들지 못했습니다",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(8.dp))
        AuthErrorBanner(
            error = error,
            screen = "home_auto_create_error",
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text("다시 만들기")
        }
    }
}

@Composable
private fun PlanDataErrorContent(
    source: PlanSource,
    resetError: AppError?,
    isResetting: Boolean,
    onRefresh: () -> Unit,
    onResetPlan: () -> Unit,
) {
    val sourceCode = when (source) {
        PlanSource.REMOTE -> "PLAN-REMOTE-UNREADABLE"
        PlanSource.CACHE -> "PLAN-CACHE-UNREADABLE"
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "현재 주 계획 데이터를 읽을 수 없습니다",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "데이터 손상이 감지되어 기존 계획을 자동으로 덮어쓰지 않고 보호했습니다. 새로고침하거나 이번 주 계획을 초기화한 뒤 다시 만들 수 있습니다. (진단 코드: $sourceCode)",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        resetError?.let { err ->
            Spacer(modifier = Modifier.height(12.dp))
            AuthErrorBanner(error = err, screen = "home_plan_reset")
        }
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onRefresh,
                enabled = !isResetting,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("새로고침")
            }
            Button(
                onClick = onResetPlan,
                enabled = !isResetting,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                if (isResetting) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(18.dp)
                            .padding(end = 8.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Text("계획 초기화")
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "이번 주 진행률",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.semantics { heading() },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        PlanModeBadge(mode = plan.mode)
                    }
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
fun PlanModeBadge(
    mode: WorkoutPlanMode,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = mode.displayLabel(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
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
    val prescriptionLabel = when (exercise.prescriptionUnit) {
        ExercisePrescriptionUnit.SECONDS -> "${exercise.sets}×${exercise.reps}초"
        ExercisePrescriptionUnit.REPS -> "${exercise.sets}×${exercise.reps}"
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
                    text = prescriptionLabel,
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
