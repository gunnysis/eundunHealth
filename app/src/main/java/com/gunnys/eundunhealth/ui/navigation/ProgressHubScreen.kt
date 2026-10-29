package com.gunnys.eundunhealth.ui.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gunnys.eundunhealth.domain.model.SmileSummaryStats
import com.gunnys.eundunhealth.ui.home.PlanModeBadge
import com.gunnys.eundunhealth.ui.theme.EundunTheme

private val WEEK_DAYS_KOR = listOf("월", "화", "수", "목", "금", "토", "일")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressHubScreen(
    onHistoryClick: () -> Unit,
    onStatisticsClick: () -> Unit,
    onGoalClick: () -> Unit,
    onBadgesClick: () -> Unit,
    onSmileTrainingClick: () -> Unit = {},
    viewModel: ProgressHubViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshProgressOnResume(onRefresh = viewModel::loadSummary)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("진행") },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionHeader(
                title = "한눈에 보는 요약",
                subtitle = "탭하여 주간 운동 기록 · 통계 추이 · 체성분 목표 상세 보기",
            )
            VisualProgressSummaryDashboard(
                state = uiState,
                onThisWeekClick = onHistoryClick,
                onRecentWeeksClick = onStatisticsClick,
                onGoalClick = onGoalClick,
            )

            SectionHeader(
                title = "마음 연습",
                subtitle = "온디바이스 미소 근육 코칭",
            )
            SmileTrainingProgressCard(
                smileStats = uiState.smileStats,
                onClick = onSmileTrainingClick,
            )

            SectionHeader(
                title = "챌린지 배지",
                subtitle = "운동 및 목표 달성 마일스톤 컬렉션",
            )
            BadgeCollectionShowcaseCard(
                state = uiState,
                onClick = onBadgesClick,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RefreshProgressOnResume(onRefresh: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, onRefresh) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * '한눈에 보는 요약' 시각화 대시보드:
 * 1) 상단 히어로 카드(운동 기록 진입): 이번 주 원형 프로그레스 링 + 요일별 미니 스탬프(월~일) + 플랜 모드 배지
 * 2) 하단 좌측 타일(운동 통계 진입): 최근 4주 달성률(미니 주간 막대 그래프 + 연속 달성 주 배지)
 * 3) 하단 우측 타일(목표 관리 진입): 목표 체중/체지방 진행도 및 현재→목표 비교 캡슐
 */
@Composable
private fun VisualProgressSummaryDashboard(
    state: ProgressHubUiState,
    onThisWeekClick: () -> Unit,
    onRecentWeeksClick: () -> Unit,
    onGoalClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ThisWeekVisualHeroCard(
            state = state,
            onClick = onThisWeekClick,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FourWeekTrendVisualTile(
                state = state,
                onClick = onRecentWeeksClick,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            GoalProgressVisualTile(
                state = state,
                onClick = onGoalClick,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThisWeekVisualHeroCard(
    state: ProgressHubUiState,
    onClick: () -> Unit,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = state.thisWeekProgress.coerceIn(0f, 1f),
        label = "thisWeekRingProgress",
    )
    val percent = (state.thisWeekProgress * 100).toInt()
    val a11yDescription = if (state.thisWeekWorkoutDays > 0) {
        "이번 주 완료 ${state.thisWeekValue}, 달성률 $percent%, 눌러서 운동 기록 보기"
    } else {
        "이번 주 운동 계획 아직 없음, 눌러서 운동 기록 보기"
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = a11yDescription
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(68.dp),
                ) {
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.14f),
                        strokeWidth = 7.dp,
                        strokeCap = StrokeCap.Round,
                    )
                    Text(
                        text = if (state.thisWeekWorkoutDays > 0) "$percent%" else "—",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Today,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "이번 주 운동 기록",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                            )
                        }
                        state.thisWeekMode?.let { mode ->
                            PlanModeBadge(mode = mode)
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = state.thisWeekValue,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = state.thisWeekSubLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                            modifier = Modifier.padding(bottom = 3.dp),
                        )
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }

            if (state.thisWeekDayStamps.isNotEmpty()) {
                WeeklyMiniDayStampsBar(dayStamps = state.thisWeekDayStamps)
            }
        }
    }
}

@Composable
private fun WeeklyMiniDayStampsBar(dayStamps: List<Boolean?>) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            dayStamps.take(7).forEachIndexed { index, status ->
                val dayLabel = WEEK_DAYS_KOR.getOrElse(index) { "" }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        imageVector = when (status) {
                            null -> Icons.Outlined.RemoveCircleOutline
                            true -> Icons.Default.CheckCircle
                            false -> Icons.Outlined.Circle
                        },
                        contentDescription = null,
                        tint = when (status) {
                            null -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.35f)
                            true -> MaterialTheme.colorScheme.primary
                            false -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.55f)
                        },
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = dayLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (status == true) FontWeight.Bold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(
                            alpha = if (status == true) 1f else 0.7f,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun FourWeekTrendVisualTile(
    state: ProgressHubUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extendedColors = EundunTheme.extendedColors
    val streakLabel = if (state.currentStreakWeeks > 0) {
        "연속 ${state.currentStreakWeeks}주 달성"
    } else {
        state.recentFourWeeksSubLabel
    }

    Card(
        onClick = onClick,
        modifier = modifier.semantics(mergeDescendants = true) {
            role = Role.Button
            contentDescription = "최근 4주 통계 ${state.recentFourWeeksValue}, $streakLabel, 눌러서 통계 보기"
        },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        text = "최근 4주 통계",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (state.currentStreakWeeks > 0) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = extendedColors.successContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = null,
                                tint = extendedColors.onSuccessContainer,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = "${state.currentStreakWeeks}주",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = extendedColors.onSuccessContainer,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = state.recentFourWeeksValue,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = state.recentFourWeeksSubLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(10.dp))

            MiniWeeklySparkBars(
                rates = state.recentWeeklyRates,
                fallbackRate = state.recentFourWeeksRate,
            )
        }
    }
}

@Composable
private fun MiniWeeklySparkBars(
    rates: List<Float>,
    fallbackRate: Float,
) {
    val displayRates = if (rates.isNotEmpty()) {
        val padded = List((4 - rates.size).coerceAtLeast(0)) { 0f } + rates.takeLast(4)
        padded
    } else {
        listOf(0f, 0f, 0f, fallbackRate.coerceIn(0f, 1f))
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            displayRates.forEachIndexed { idx, rate ->
                val isLatest = idx == displayRates.lastIndex
                val heightFraction = rate.coerceIn(0.15f, 1f)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(heightFraction)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                        .background(
                            if (isLatest) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)
                            },
                        ),
                )
            }
        }
        LinearProgressIndicator(
            progress = { fallbackRate.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clearAndSetSemantics {},
            color = MaterialTheme.colorScheme.tertiary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
        )
    }
}

@Composable
private fun GoalProgressVisualTile(
    state: ProgressHubUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extendedColors = EundunTheme.extendedColors
    val bodyFatHint = if (state.targetBodyFatPct != null) {
        val currentBf = state.currentBodyFatPct?.let { "${"%.1f".format(it)}% → " }.orEmpty()
        "체지방 ${currentBf}${"%.1f".format(state.targetBodyFatPct)}%"
    } else {
        null
    }

    Card(
        onClick = onClick,
        modifier = modifier.semantics(mergeDescendants = true) {
            role = Role.Button
            contentDescription = "목표까지 ${state.goalDeltaValue}, ${state.goalDeltaSubLabel}, 눌러서 목표 설정 보기"
        },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        text = "목표 관리",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (state.isWeightGoalAchieved) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = extendedColors.successContainer,
                    ) {
                        Text(
                            text = "달성",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = extendedColors.onSuccessContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = state.goalDeltaValue,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = if (state.isWeightGoalAchieved) {
                    extendedColors.success
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = state.goalDeltaSubLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                val gaugeLabel = when {
                    state.currentWeightKg != null && state.targetWeightKg != null ->
                        "현재 ${"%.1f".format(state.currentWeightKg)}kg → ${"%.1f".format(state.targetWeightKg)}kg"
                    bodyFatHint != null -> bodyFatHint
                    else -> "탭하여 체중·체지방 목표 설정"
                }
                Text(
                    text = gaugeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SmileTrainingProgressCard(
    smileStats: SmileSummaryStats,
    onClick: () -> Unit,
) {
    val extendedColors = EundunTheme.extendedColors
    val summaryText = when {
        smileStats.isCompletedToday ->
            "오늘 연습 완료 · 연속 ${smileStats.currentStreakDays}일째 (누적 ${smileStats.totalSessions}회)"
        smileStats.currentStreakDays > 0 ->
            "연속 ${smileStats.currentStreakDays}일 진행 중 · 오늘 10초 미소 연습을 이어가세요"
        else ->
            "2초 미소 × 3회 반복 온디바이스 표정 근육 및 마음 연습"
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (smileStats.isCompletedToday) {
                            extendedColors.successContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (smileStats.isCompletedToday) {
                        Icons.Default.CheckCircle
                    } else {
                        Icons.Default.SentimentSatisfiedAlt
                    },
                    contentDescription = null,
                    tint = if (smileStats.isCompletedToday) {
                        extendedColors.onSuccessContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    modifier = Modifier.size(24.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "스마일 트레이닝",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    val badgeText = when {
                        smileStats.isCompletedToday -> "오늘 완료"
                        smileStats.currentStreakDays > 0 -> "연속 ${smileStats.currentStreakDays}일"
                        else -> "10초 루틴"
                    }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (smileStats.isCompletedToday) {
                            extendedColors.successContainer
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
                        },
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (smileStats.isCompletedToday) {
                                extendedColors.onSuccessContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    text = summaryText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
            )
        }
    }
}

/**
 * 상단 '한눈에 보는 요약'과 중복되던 '통계', '기록', '목표' 메뉴 행을 과감히 제거하고,
 * 성취 배지 달성 진행률과 배지 미니 쇼케이스를 한눈에 보여주는 단일 '챌린지 배지' 카드로 간소화.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BadgeCollectionShowcaseCard(
    state: ProgressHubUiState,
    onClick: () -> Unit,
) {
    val extendedColors = EundunTheme.extendedColors
    val badgeFraction = if (state.totalBadgeCount > 0) {
        (state.earnedBadgeCount.toFloat() / state.totalBadgeCount).coerceIn(0f, 1f)
    } else {
        0f
    }
    val subtitle = if (state.latestBadgeName != null) {
        "최근 획득: ${state.latestBadgeName}"
    } else {
        "첫 운동과 주간 목표를 달성해 배지를 모아보세요"
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "챌린지 배지 ${state.earnedBadgeCount}/${state.totalBadgeCount}개 획득, $subtitle, 눌러서 전체 배지 보기"
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(extendedColors.successContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = extendedColors.onSuccessContainer,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "성취 배지 컬렉션",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = "${state.earnedBadgeCount}/${state.totalBadgeCount}개 달성",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LinearProgressIndicator(
                progress = { badgeFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clearAndSetSemantics {},
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
            )

            if (state.badgePreviews.isNotEmpty()) {
                val sortedPreviews = state.badgePreviews
                    .sortedByDescending { it.earned }
                    .take(5)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    sortedPreviews.forEach { badge ->
                        BadgeMiniChip(badge = badge)
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeMiniChip(badge: BadgeMiniPreview) {
    val extendedColors = EundunTheme.extendedColors
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (badge.earned) {
            extendedColors.successContainer.copy(alpha = 0.85f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = if (badge.earned) Icons.Default.EmojiEvents else Icons.Outlined.Lock,
                contentDescription = null,
                tint = if (badge.earned) {
                    extendedColors.onSuccessContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                },
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = badge.name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (badge.earned) FontWeight.SemiBold else FontWeight.Normal,
                color = if (badge.earned) {
                    extendedColors.onSuccessContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                },
            )
        }
    }
}
