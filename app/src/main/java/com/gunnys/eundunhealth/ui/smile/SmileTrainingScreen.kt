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
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.gunnys.eundunhealth.domain.model.SmileSessionRecord
import com.gunnys.eundunhealth.domain.model.SmileSummaryStats
import com.gunnys.eundunhealth.domain.model.SmileSyncStatus
import com.gunnys.eundunhealth.ui.components.AuthErrorBanner
import com.gunnys.eundunhealth.ui.theme.EundunTheme
import com.gunnys.eundunhealth.ui.util.HapticFeedbackHelper
import com.gunnys.eundunhealth.ui.util.ObserveAsEvents
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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
                val activity = context.findActivity()
                val shouldShowRationale = activity != null &&
                    ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
                viewModel.onCameraPermissionResult(isGranted = false, shouldShowRationale = shouldShowRationale)
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
                HapticFeedbackHelper.performConfirm(view)
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
            text = {
                Text(
                    text = "지금 나가면 현재 진행 중인 반복 횟수는 저장되지 않습니다.",
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
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
            text = {
                Text(
                    text = "기기와 서버에 저장된 모든 스마일 트레이닝 기록이 영구적으로 삭제됩니다.",
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
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
            onStartClick = {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA,
                ) == PackageManager.PERMISSION_GRANTED
                viewModel.onStartClicked(hasCameraPermission = granted)
            },
            onRetryBlockedSync = viewModel::retryBlockedSync,
            onDeleteAllClick = viewModel::requestDeleteAllHistory,
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
        SmileSessionPhase.COMPLETED -> CompletedContent(
            uiState = uiState,
            onRetrySaveClick = viewModel::retrySaveCompletedSession,
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
    onStartClick: () -> Unit,
    onRetryBlockedSync: () -> Unit,
    onDeleteAllClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        uiState.error?.let { err ->
            AuthErrorBanner(error = err, screen = "smile_intro")
        }

        SummaryHeroCard(
            stats = uiState.summaryStats,
            onRetryBlockedSync = onRetryBlockedSync,
        )

        SmileStartActionCard(
            hasCameraHardware = hasCameraHardware,
            onStartClick = onStartClick,
        )

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
private fun SummaryHeroCard(
    stats: SmileSummaryStats,
    onRetryBlockedSync: () -> Unit,
) {
    val containerColor = if (stats.isCompletedToday) {
        EundunTheme.extendedColors.successContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = if (stats.isCompletedToday) {
        EundunTheme.extendedColors.onSuccessContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = if (stats.isCompletedToday) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Default.SentimentSatisfiedAlt
                        },
                        contentDescription = null,
                        tint = if (stats.isCompletedToday) {
                            EundunTheme.extendedColors.success
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                    Text(
                        text = if (stats.isCompletedToday) {
                            "오늘의 미소 연습 완료!"
                        } else {
                            "오늘 첫 미소로 표정 근육을 깨워보세요"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SmileMetricItem(
                    label = "연속 달성",
                    value = "${stats.currentStreakDays}일",
                    subValue = "최장 ${stats.longestStreakDays}일",
                )
                SmileMetricItem(
                    label = "누적 완료",
                    value = "${stats.totalSessions}회",
                    subValue = if (stats.pendingSyncCount > 0) "대기 ${stats.pendingSyncCount}건" else "동기화 완료",
                )
                SmileMetricItem(
                    label = "누적 시간",
                    value = formatDurationKorean(stats.totalDurationSec),
                    subValue = "표준 2초×3회",
                )
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
private fun SmileMetricItem(
    label: String,
    value: String,
    subValue: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = subValue,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SmileStartActionCard(
    hasCameraHardware: Boolean,
    onStartClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "오늘의 스마일 코스",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        text = "2초 유지 × 3회",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            Text(
                text = "화면을 편안하게 바라보며 입꼬리를 올려 2초씩 3번 유지합니다. 얼굴이 가이드에 맞춰지면 1초 후 자동으로 시작됩니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = onStartClick,
                enabled = hasCameraHardware,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                val buttonLabel = if (hasCameraHardware) {
                    "스마일 트레이닝 시작하기"
                } else {
                    "이 기기에서 카메라를 사용할 수 없습니다"
                }
                Text(
                    text = buttonLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
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
                fontWeight = FontWeight.SemiBold,
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
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "${record.completedReps}회 완료 · ${formatDurationKorean(record.durationSec)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        val dateText = formatter.format(Instant.ofEpochMilli(record.occurredAtEpochMs))
                        Text(
                            text = dateText,
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

@Composable
private fun CompletedContent(
    uiState: SmileTrainingUiState,
    onRetrySaveClick: () -> Unit,
    onPracticeAgainClick: () -> Unit,
    onDoneClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        uiState.error?.let { err ->
            AuthErrorBanner(error = err, screen = "smile_completed")
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onRetrySaveClick,
                enabled = !uiState.isSavingSession,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("기록 다시 저장하기")
            }
            Spacer(Modifier.height(16.dp))
        }

        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = EundunTheme.extendedColors.success,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "오늘의 스마일 트레이닝 완료!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "연속 ${uiState.summaryStats.currentStreakDays.coerceAtLeast(1)}일째 마음 연습을 이어가고 있어요.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        SessionOutcomeSummaryBanner(uiState = uiState)
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onDoneClick,
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("완료하고 돌아가기", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onPracticeAgainClick,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("한 번 더 연습하기")
        }
    }
}

@Composable
private fun SessionOutcomeSummaryBanner(uiState: SmileTrainingUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "표준 스마일 코스 (2초 유지)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "${uiState.targetReps}회 완료 · ${formatDurationKorean(uiState.sessionDurationSec)}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
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

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
