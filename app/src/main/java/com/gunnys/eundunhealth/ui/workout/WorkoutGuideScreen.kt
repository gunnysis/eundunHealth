package com.gunnys.eundunhealth.ui.workout

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.gunnys.eundunhealth.domain.model.Exercise
import com.gunnys.eundunhealth.domain.model.ExercisePrescriptionUnit
import com.gunnys.eundunhealth.domain.model.ExerciseType
import com.gunnys.eundunhealth.ui.components.AuthErrorBanner
import com.gunnys.eundunhealth.ui.components.ErrorContent
import com.gunnys.eundunhealth.ui.theme.EundunTheme
import com.gunnys.eundunhealth.ui.util.HapticFeedbackHelper
import com.gunnys.eundunhealth.ui.util.ObserveAsEvents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutGuideScreen(
    onBack: () -> Unit,
    onWorkoutCompleted: () -> Unit = {},
    onNavigateToSmileTraining: () -> Unit = {},
    viewModel: WorkoutGuideViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    var showExitDialog by rememberSaveable { mutableStateOf(false) }

    // 가이드 세션 동안 화면 꺼짐 방지 (FLAG_KEEP_SCREEN_ON)
    DisposableEffect(context) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    ObserveAsEvents(viewModel.sideEffect) { effect ->
        when (effect) {
            WorkoutGuideSideEffect.ExitGuide -> {
                onWorkoutCompleted()
                onBack()
            }
        }
    }

    BackHandler(
        enabled = uiState is WorkoutGuideUiState.InProgress || uiState is WorkoutGuideUiState.CompletedSummary,
    ) {
        when (uiState) {
            is WorkoutGuideUiState.InProgress -> showExitDialog = true
            is WorkoutGuideUiState.CompletedSummary -> {
                onWorkoutCompleted()
                onBack()
            }
            else -> Unit
        }
    }

    if (showExitDialog && uiState is WorkoutGuideUiState.InProgress) {
        WorkoutGuideExitDialog(
            onDismiss = { showExitDialog = false },
            onExitWithoutCompleting = {
                showExitDialog = false
                onBack()
            },
            onCompleteAndExit = {
                showExitDialog = false
                HapticFeedbackHelper.performConfirm(view)
                viewModel.completeWorkout(exitImmediately = true)
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val state = uiState
                    if (state is WorkoutGuideUiState.InProgress) {
                        Text("운동 가이드 (${state.currentIndex + 1}/${state.totalExercises})")
                    } else {
                        Text("운동 가이드")
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            when (uiState) {
                                is WorkoutGuideUiState.InProgress -> showExitDialog = true
                                is WorkoutGuideUiState.CompletedSummary -> {
                                    onWorkoutCompleted()
                                    onBack()
                                }
                                else -> onBack()
                            }
                        },
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "가이드 닫기")
                    }
                },
            )
        },
    ) { padding ->
        when (val state = uiState) {
            is WorkoutGuideUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            is WorkoutGuideUiState.Error -> {
                ErrorContent(
                    error = state.error,
                    modifier = Modifier.padding(padding),
                    onRetry = viewModel::load,
                )
            }
            is WorkoutGuideUiState.InProgress -> {
                WorkoutGuideInProgressContent(
                    state = state,
                    onPageChanged = viewModel::setCurrentIndex,
                    onPrevious = viewModel::previousExercise,
                    onNextOrComplete = {
                        if (state.isLastExercise) {
                            HapticFeedbackHelper.performConfirm(view)
                        }
                        viewModel.nextExerciseOrComplete()
                    },
                    modifier = Modifier.padding(padding),
                )
            }
            is WorkoutGuideUiState.CompletedSummary -> {
                WorkoutGuideCompletedContent(
                    exerciseCount = state.dayPlan.exercises.size,
                    onFinish = {
                        onWorkoutCompleted()
                        onBack()
                    },
                    onStartSmileCooldown = {
                        onWorkoutCompleted()
                        onBack()
                        onNavigateToSmileTraining()
                    },
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun WorkoutGuideInProgressContent(
    state: WorkoutGuideUiState.InProgress,
    onPageChanged: (Int) -> Unit,
    onPrevious: () -> Unit,
    onNextOrComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val exercises = state.dayPlan.exercises
    val pagerState = rememberPagerState(
        initialPage = state.currentIndex,
        pageCount = { exercises.size },
    )
    val latestCurrentIndex by rememberUpdatedState(state.currentIndex)
    val latestOnPageChanged by rememberUpdatedState(onPageChanged)

    LaunchedEffect(state.currentIndex) {
        if (pagerState.currentPage != state.currentIndex) {
            pagerState.animateScrollToPage(state.currentIndex)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page != latestCurrentIndex) {
                latestOnPageChanged(page)
            }
        }
    }

    val progressFraction = remember(state.currentIndex, state.totalExercises) {
        if (state.totalExercises > 0) (state.currentIndex + 1).toFloat() / state.totalExercises else 0f
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        LinearProgressIndicator(
            progress = { progressFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            strokeCap = StrokeCap.Round,
        )

        state.completionError?.let { err ->
            Spacer(modifier = Modifier.height(8.dp))
            AuthErrorBanner(error = err, screen = "workout_guide_complete")
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            val exercise = exercises[page]
            WorkoutGuideExercisePage(exercise = exercise)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onPrevious,
                enabled = state.currentIndex > 0 && !state.isCompleting,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 54.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("이전 종목")
            }

            Button(
                onClick = onNextOrComplete,
                enabled = !state.isCompleting,
                modifier = Modifier
                    .weight(1.4f)
                    .heightIn(min = 54.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                if (state.isCompleting) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(20.dp)
                            .padding(end = 8.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Text("완료 저장 중...")
                } else if (state.isLastExercise) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "오늘 운동 완료",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    Text(
                        text = "다음 종목",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkoutGuideExercisePage(exercise: Exercise) {
    val context = LocalContext.current
    val prescriptionText = when (exercise.prescriptionUnit) {
        ExercisePrescriptionUnit.SECONDS -> "목표 처방: ${exercise.sets}세트 · ${exercise.reps}초 유지"
        ExercisePrescriptionUnit.REPS -> "목표 처방: ${exercise.sets}세트 · ${exercise.reps}회"
    }
    val typeLabel = if (exercise.type == ExerciseType.CARDIO) "유산소" else "근력"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // GIF / 시범 영역
        if (exercise.gifUrl.isNotBlank()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(exercise.gifUrl)
                    .size(512)
                    .crossfade(true)
                    .build(),
                contentDescription = "${exercise.name} 동작 시범",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Fit,
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerLow),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                },
                error = {
                    ExerciseIllustrationFallback(exercise = exercise)
                },
            )
        } else {
            ExerciseIllustrationFallback(exercise = exercise)
        }

        // 종목 제목 및 부위 태그 (200% 대형 글꼴 줄바꿈 대응 FlowRow)
        Column {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (exercise.bodyPart.isNotBlank()) {
                    GuideTagChip(text = exercise.bodyPart)
                }
                if (exercise.equipment.isNotBlank()) {
                    GuideTagChip(text = exercise.equipment)
                }
                GuideTagChip(text = typeLabel)
            }
        }

        // 목표 처방 배너 (세트 카운터나 휴식 타이머 없이 깔끔한 목표 안내만 제공)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(
                text = prescriptionText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }

        // 수행 방법 안내
        if (exercise.instructions.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "운동 순서 및 자세 안내",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    exercise.instructions.forEachIndexed { index, step ->
                        Text(
                            text = "${index + 1}. $step",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseIllustrationFallback(exercise: Exercise) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.FitnessCenter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${exercise.name} · 아래 자세 안내를 참고해 진행해 주세요",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun GuideTagChip(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun WorkoutGuideCompletedContent(
    exerciseCount: Int,
    onFinish: () -> Unit,
    onStartSmileCooldown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = EundunTheme.extendedColors.success,
            modifier = Modifier.size(72.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "오늘의 운동을 완료했습니다!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "총 ${exerciseCount}종목 루틴이 오늘 기록에 반영되었습니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onFinish,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
        ) {
            Text("오늘 탭으로 돌아가기")
        }
        Spacer(modifier = Modifier.height(12.dp))
        FilledTonalButton(
            onClick = onStartSmileCooldown,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
        ) {
            Icon(
                imageVector = Icons.Default.SentimentSatisfiedAlt,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("마무리 10초 스마일 트레이닝 하기")
        }
    }
}

@Composable
internal fun WorkoutGuideExitDialog(
    onDismiss: () -> Unit,
    onExitWithoutCompleting: () -> Unit,
    onCompleteAndExit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("운동을 중단하시겠어요?") },
        text = {
            Text(
                text = "지금 나가면 완료 표시 없이 종료하거나, 오늘 운동을 완료 처리하고 나갈 수 있습니다.",
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        // 3개 이상의 긴 한국어 액션이 있는 다이얼로그에서 dismissButton 슬롯에 Column을 중첩하면
        // AlertDialogFlowRow 내부 가로·세로 측정 경합으로 하단 버튼('완료하지 않고 나가기')이 잘린다.
        // 단일 confirmButton 슬롯 안에 전체 너비(fillMaxWidth) 수직 스택으로 배치해
        // 모든 화면 폭·글꼴 크기(200%)에서 잘림 없이 시각적 우선순위를 명확히 표현한다.
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text(
                        text = "계속 운동하기",
                        textAlign = TextAlign.Center,
                    )
                }
                FilledTonalButton(
                    onClick = onCompleteAndExit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text(
                        text = "오늘 운동 완료 처리하고 나가기",
                        textAlign = TextAlign.Center,
                    )
                }
                TextButton(
                    onClick = onExitWithoutCompleting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text(
                        text = "완료하지 않고 나가기",
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
    )
}
