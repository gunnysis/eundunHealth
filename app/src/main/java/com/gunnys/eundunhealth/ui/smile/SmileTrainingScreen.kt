package com.gunnys.eundunhealth.ui.smile

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.model.SmileDifficultyLevel
import com.gunnys.eundunhealth.domain.model.SmileRoutineType
import com.gunnys.eundunhealth.domain.model.SmileSessionRecord
import com.gunnys.eundunhealth.domain.model.SmileSummaryStats
import com.gunnys.eundunhealth.domain.model.SmileSyncStatus
import com.gunnys.eundunhealth.domain.smile.SmileRoutineRule
import com.gunnys.eundunhealth.ui.util.ObserveAsEvents
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TargetRepChoices = listOf(1, 2, 3, 4, 5)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmileTrainingScreen(
    onBack: () -> Unit,
    viewModel: SmileTrainingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    SmileSideEffectsHandler(
        viewModel = viewModel,
        snackbarHostState = snackbarHostState,
    )

    val isCameraPhase = uiState.phase.isCameraActivePhase()
    KeepScreenOnEffect(enabled = isCameraPhase)

    val handleExitRequest = {
        if (viewModel.onBackOrExitRequested()) {
            onBack()
        }
    }

    BackHandler(onBack = handleExitRequest)

    SmileConfirmDialogs(
        uiState = uiState,
        viewModel = viewModel,
    )

    if (isCameraPhase) {
        SmileCameraStage(
            uiState = uiState,
            onFrameSignal = viewModel::onFrameSignal,
            onStartExercise = { viewModel.beginActiveExercise(SystemClock.elapsedRealtime()) },
            onPauseExercise = { viewModel.pauseActiveExercise() },
            onLifecycleStopped = viewModel::onCameraLifecycleStopped,
            onPermissionRevoked = {
                viewModel.onCameraPermissionResult(isGranted = false, shouldShowRationale = true)
            },
            onCameraError = viewModel::onCameraError,
            onCloseClick = handleExitRequest,
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("스마일 트레이닝") },
                    navigationIcon = {
                        IconButton(onClick = handleExitRequest) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                SmileNonCameraPhaseContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    context = context,
                    onDoneClick = onBack,
                )
            }
        }
    }
}

private fun SmileSessionPhase.isCameraActivePhase(): Boolean = this == SmileSessionPhase.READY ||
    this == SmileSessionPhase.ACTIVE ||
    this == SmileSessionPhase.REP_SUCCESS

@Composable
private fun SmileSideEffectsHandler(
    viewModel: SmileTrainingViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    val view = LocalView.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        val activity = context.findActivity()
        val shouldShowRationale = activity != null &&
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
        viewModel.onCameraPermissionResult(isGranted = isGranted, shouldShowRationale = shouldShowRationale)
    }

    ObserveAsEvents(viewModel.sideEffect) { effect ->
        when (effect) {
            SmileTrainingSideEffect.RequestCameraPermission -> {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
            SmileTrainingSideEffect.OpenAppSettings -> {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(intent) }
            }
            SmileTrainingSideEffect.TriggerRepSuccessHaptic -> {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            }
            is SmileTrainingSideEffect.AnnounceAccessibility -> {
                @Suppress("DEPRECATION")
                view.announceForAccessibility(effect.message)
            }
            is SmileTrainingSideEffect.ShowSnackbar -> {
                snackbarHostState.showSnackbar(effect.message)
            }
        }
    }
}

@Composable
private fun KeepScreenOnEffect(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(enabled) {
        val window = context.findActivity()?.window
        if (enabled) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

@Composable
private fun SmileConfirmDialogs(
    uiState: SmileTrainingUiState,
    viewModel: SmileTrainingViewModel,
) {
    if (uiState.showExitConfirmDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissExitConfirmDialog,
            title = { Text("연습을 중단할까요?") },
            text = { Text("지금 나가면 현재 진행 중인 반복 횟수는 저장되지 않습니다.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmAbortActiveSession) {
                    Text("중단하기")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissExitConfirmDialog) {
                    Text("계속 연습")
                }
            },
        )
    }

    if (uiState.showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteAllHistoryDialog,
            title = { Text("모든 스마일 기록 삭제") },
            text = { Text("기기와 서버에 저장된 모든 스마일 트레이닝 기록이 영구적으로 삭제됩니다.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDeleteAllHistory) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteAllHistoryDialog) {
                    Text("취소")
                }
            },
        )
    }
}

@Composable
private fun SmileNonCameraPhaseContent(
    uiState: SmileTrainingUiState,
    viewModel: SmileTrainingViewModel,
    context: Context,
    onDoneClick: () -> Unit,
) {
    val hasCameraHardware = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT) ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }

    when (uiState.phase) {
        SmileSessionPhase.INTRO -> SmileIntroContent(
            uiState = uiState,
            hasCameraHardware = hasCameraHardware,
            onSelectRoutine = viewModel::selectRoutine,
            onSelectDifficulty = viewModel::selectDifficulty,
            onSelectTargetReps = viewModel::selectTargetReps,
            onStartClick = {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA,
                ) == PackageManager.PERMISSION_GRANTED
                viewModel.onStartClicked(hasCameraPermission = granted)
            },
            onRetryBlockedSync = viewModel::retryBlockedSync,
            onDeleteAllClick = viewModel::requestDeleteAllHistory,
            onDismissError = viewModel::clearError,
        )
        SmileSessionPhase.PERMISSION_RATIONALE -> PermissionRationaleContent(
            onAllowClick = viewModel::onConfirmPermissionRationale,
            onCancelClick = viewModel::resetToIntro,
        )
        SmileSessionPhase.PERMISSION_BLOCKED -> PermissionBlockedContent(
            onOpenSettingsClick = viewModel::onOpenSettingsClicked,
            onBackClick = viewModel::resetToIntro,
        )
        SmileSessionPhase.MODEL_PREPARING -> ModelPreparingContent(
            progressPercent = uiState.modelDownloadPercent,
            onCancelClick = viewModel::resetToIntro,
        )
        SmileSessionPhase.MODEL_UNAVAILABLE -> ModelUnavailableContent(
            reason = uiState.modelUnavailableReason ?: "얼굴 인식 모듈을 준비할 수 없습니다.",
            onRetryClick = viewModel::prepareModelAndProceed,
            onBackClick = viewModel::resetToIntro,
        )
        SmileSessionPhase.REFLECTION -> ReflectionContent(
            uiState = uiState,
            onSelectEmoji = viewModel::selectReflectionEmoji,
            onSaveClick = { viewModel.finishAndSaveSession(skipReflection = false) },
            onSkipClick = { viewModel.finishAndSaveSession(skipReflection = true) },
        )
        SmileSessionPhase.COMPLETED -> CompletedContent(
            uiState = uiState,
            onPracticeAgainClick = viewModel::resetToIntro,
            onDoneClick = onDoneClick,
        )
        SmileSessionPhase.READY,
        SmileSessionPhase.ACTIVE,
        SmileSessionPhase.REP_SUCCESS,
        -> Unit
    }
}

@Composable
private fun SmileIntroContent(
    uiState: SmileTrainingUiState,
    hasCameraHardware: Boolean,
    onSelectRoutine: (SmileRoutineType) -> Unit,
    onSelectDifficulty: (SmileDifficultyLevel) -> Unit,
    onSelectTargetReps: (Int) -> Unit,
    onStartClick: () -> Unit,
    onRetryBlockedSync: () -> Unit,
    onDeleteAllClick: () -> Unit,
    onDismissError: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        uiState.error?.let { err ->
            InlineErrorBanner(error = err, onDismiss = onDismissError)
        }

        PrivacyNoticeCard()

        SummaryStatsCard(
            stats = uiState.summaryStats,
            onRetryBlockedSync = onRetryBlockedSync,
        )

        RoutineSelectionSection(
            selectedRoutine = uiState.selectedRoutine,
            selectedDifficulty = uiState.selectedDifficulty,
            onSelectRoutine = onSelectRoutine,
        )

        DifficultyAndRepsSection(
            selectedDifficulty = uiState.selectedDifficulty,
            targetReps = uiState.targetReps,
            targetHoldDurationMs = uiState.targetHoldDurationMs,
            onSelectDifficulty = onSelectDifficulty,
            onSelectTargetReps = onSelectTargetReps,
        )

        Button(
            onClick = onStartClick,
            enabled = hasCameraHardware,
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            Icon(Icons.Default.SentimentSatisfiedAlt, contentDescription = null)
            val buttonLabel = if (hasCameraHardware) {
                " 스마일 트레이닝 시작하기"
            } else {
                " 이 기기에서 카메라를 사용할 수 없습니다"
            }
            Text(buttonLabel, style = MaterialTheme.typography.titleMedium)
        }

        if (uiState.recentSessions.isNotEmpty()) {
            RecentSessionsSection(
                sessions = uiState.recentSessions,
                isDeleting = uiState.isDeletingHistory,
                onDeleteAllClick = onDeleteAllClick,
            )
        }
    }
}

@Composable
private fun RoutineSelectionSection(
    selectedRoutine: SmileRoutineType,
    selectedDifficulty: SmileDifficultyLevel,
    onSelectRoutine: (SmileRoutineType) -> Unit,
) {
    Text(
        text = "루틴 선택",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() },
    )
    SmileRoutineType.entries.forEach { routine ->
        val selected = selectedRoutine == routine
        val effectiveHoldMs = remember(routine, selectedDifficulty) {
            SmileRoutineRule(routine, selectedDifficulty).targetHoldDurationMs
        }
        val holdSecText = String.format(Locale.US, "%.1f초 유지", effectiveHoldMs / 1000f)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = selected,
                    role = Role.RadioButton,
                    onClick = { onSelectRoutine(routine) },
                ),
            colors = CardDefaults.cardColors(
                containerColor = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RadioButton(selected = selected, onClick = null)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = routine.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            RoutineMetaBadge(text = holdSecText)
                            if (routine.requiresEyesOpen) {
                                RoutineMetaBadge(text = "눈 뜨기 포함")
                            }
                        }
                    }
                    Text(
                        text = routine.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RoutineMetaBadge(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DifficultyAndRepsSection(
    selectedDifficulty: SmileDifficultyLevel,
    targetReps: Int,
    targetHoldDurationMs: Long,
    onSelectDifficulty: (SmileDifficultyLevel) -> Unit,
    onSelectTargetReps: (Int) -> Unit,
) {
    Text(
        text = "난이도 및 반복 횟수",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() },
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SmileDifficultyLevel.entries.forEach { level ->
            FilterChip(
                selected = selectedDifficulty == level,
                onClick = { onSelectDifficulty(level) },
                label = { Text("${level.displayName} (${level.description})") },
            )
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "목표 반복 횟수",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                val totalHoldSec = String.format(
                    Locale.US,
                    "%.1f",
                    (targetHoldDurationMs * targetReps) / 1000f,
                )
                Text(
                    text = "총 미소 유지 시간 약 ${totalHoldSec}초",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TargetRepChoices.forEach { reps ->
                    val selected = targetReps == reps
                    OutlinedButton(
                        onClick = { onSelectTargetReps(reps) },
                        modifier = Modifier.weight(1f).height(40.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = if (selected) {
                            ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        },
                    ) {
                        Text(
                            text = "${reps}회",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyNoticeCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Column {
                Text(
                    text = "온디바이스 프라이버시 보호",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = "카메라 영상은 기기 내부에서만 실시간 미소 신호 측정에 사용되며, 사진이나 영상은 어디에도 저장·전송되지 않습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun SummaryStatsCard(
    stats: SmileSummaryStats,
    onRetryBlockedSync: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (stats.isCompletedToday) "오늘 마음 연습 완료" else "오늘 첫 미소를 시작해보세요",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "연속 ${stats.currentStreakDays}일 (최장 ${stats.longestStreakDays}일)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "누적 ${stats.totalSessions}회 완료 · 총 ${formatDurationKorean(stats.totalDurationSec)} 연습",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (stats.pendingSyncCount > 0) {
                    Text(
                        text = "동기화 대기 ${stats.pendingSyncCount}건",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (stats.blockedSyncCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "동기화 확인 필요 ${stats.blockedSyncCount}건",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onRetryBlockedSync) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(" 다시 동기화")
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentSessionsSection(
    sessions: List<SmileSessionRecord>,
    isDeleting: Boolean,
    onDeleteAllClick: () -> Unit,
) {
    val formatter = remember {
        DateTimeFormatter.ofPattern("M월 d일 a h:mm", Locale.KOREAN)
            .withZone(ZoneId.of("Asia/Seoul"))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "최근 연습 기록",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            TextButton(onClick = onDeleteAllClick, enabled = !isDeleting) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(" 전체 삭제")
            }
        }
        sessions.forEach { record ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "${record.routine.displayName} · ${record.difficulty.displayName} (${record.completedReps}회)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        val dateText = formatter.format(Instant.ofEpochMilli(record.occurredAtEpochMs))
                        Text(
                            text = "$dateText · ${formatDurationKorean(record.durationSec)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val statusText = when (record.syncStatus) {
                        SmileSyncStatus.SYNCED -> "동기화됨"
                        SmileSyncStatus.PENDING -> "대기 중"
                        SmileSyncStatus.BLOCKED -> "전송 보류"
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (record.syncStatus == SmileSyncStatus.BLOCKED) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRationaleContent(
    onAllowClick: () -> Unit,
    onCancelClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "전면 카메라 권한 안내",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "스마일 트레이닝은 전면 카메라로 미소 유지 여부를 기기 안에서만 측정합니다. 얼굴 사진이나 영상은 저장되거나 서버로 전송되지 않습니다.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onAllowClick, modifier = Modifier.fillMaxWidth()) {
            Text("카메라 권한 허용하기")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onCancelClick, modifier = Modifier.fillMaxWidth()) {
            Text("돌아가기")
        }
    }
}

@Composable
private fun PermissionBlockedContent(
    onOpenSettingsClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "카메라 권한이 꺼져 있어요",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "앱 설정 > 권한에서 카메라 권한을 허용해주시면 스마일 트레이닝을 시작할 수 있습니다.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onOpenSettingsClick, modifier = Modifier.fillMaxWidth()) {
            Text("앱 설정 열기")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBackClick, modifier = Modifier.fillMaxWidth()) {
            Text("돌아가기")
        }
    }
}

@Composable
private fun ModelPreparingContent(
    progressPercent: Int?,
    onCancelClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.CloudDownload,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "온디바이스 얼굴 인식 모듈 준비 중",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "최초 실행 시 Google Play 서비스를 통해 모델을 준비합니다.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        if (progressPercent != null) {
            LinearProgressIndicator(
                progress = { progressPercent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text("$progressPercent%", style = MaterialTheme.typography.labelLarge)
        } else {
            CircularProgressIndicator()
        }
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onCancelClick) {
            Text("취소")
        }
    }
}

@Composable
private fun ModelUnavailableContent(
    reason: String,
    onRetryClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = reason,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetryClick, modifier = Modifier.fillMaxWidth()) {
            Text("다시 시도")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBackClick, modifier = Modifier.fillMaxWidth()) {
            Text("돌아가기")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReflectionContent(
    uiState: SmileTrainingUiState,
    onSelectEmoji: (Int?) -> Unit,
    onSaveClick: () -> Unit,
    onSkipClick: () -> Unit,
) {
    val moodOptions = remember {
        listOf(
            1 to "어색해요",
            2 to "차분해요",
            3 to "괜찮아요",
            4 to "산뜻해요",
            5 to "따뜻해요",
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        uiState.error?.let { err ->
            InlineErrorBanner(error = err, onDismiss = null)
            Spacer(Modifier.height(16.dp))
        }

        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "${uiState.targetReps}회 미소 연습을 마쳤어요!",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        SessionOutcomeSummaryBanner(uiState = uiState)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "지금 기분이 어떤가요? (선택 사항)",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            moodOptions.forEach { (score, label) ->
                FilterChip(
                    selected = uiState.selectedReflectionEmoji == score,
                    onClick = {
                        val next = if (uiState.selectedReflectionEmoji == score) null else score
                        onSelectEmoji(next)
                    },
                    label = { Text("$score · $label") },
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onSaveClick,
            enabled = !uiState.isSavingSession,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(if (uiState.isSavingSession) "저장 중..." else "기록 저장하기")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = onSkipClick,
            enabled = !uiState.isSavingSession,
        ) {
            Text("기분 선택 건너뛰고 저장")
        }
    }
}

@Composable
private fun CompletedContent(
    uiState: SmileTrainingUiState,
    onPracticeAgainClick: () -> Unit,
    onDoneClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.SentimentSatisfiedAlt,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "오늘의 스마일 트레이닝 완료",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "연속 ${uiState.summaryStats.currentStreakDays}일째 마음 연습을 이어가고 있어요.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        SessionOutcomeSummaryBanner(uiState = uiState)
        Spacer(Modifier.height(28.dp))
        Button(onClick = onDoneClick, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("완료")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onPracticeAgainClick, modifier = Modifier.fillMaxWidth()) {
            Text("한 번 더 연습하기")
        }
    }
}

@Composable
private fun SessionOutcomeSummaryBanner(uiState: SmileTrainingUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${uiState.selectedRoutine.displayName} · ${uiState.selectedDifficulty.displayName}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "${uiState.targetReps}회 완료 · ${formatDurationKorean(uiState.sessionDurationSec)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun InlineErrorBanner(
    error: AppError,
    onDismiss: (() -> Unit)?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = error.userMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier
                    .weight(1f)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (onDismiss != null) {
                TextButton(onClick = onDismiss) {
                    Text("확인", color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}

internal fun formatDurationKorean(seconds: Int): String {
    val safeSec = seconds.coerceAtLeast(0)
    val mins = safeSec / 60
    val remSec = safeSec % 60
    return when {
        mins > 0 && remSec > 0 -> "${mins}분 ${remSec}초"
        mins > 0 -> "${mins}분"
        else -> "${remSec}초"
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
