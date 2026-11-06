package com.gunnys.eundunhealth.ui.smile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gunnys.eundunhealth.ui.util.HapticFeedbackHelper
import com.gunnys.eundunhealth.ui.util.ObserveAsEvents
import com.gunnys.eundunhealth.ui.util.findActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmileTrainingScreen(
    onBack: () -> Unit,
    viewModel: SmileTrainingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(context) {
        val hasCameraHardware = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT) ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        val hasCameraPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onInitialEntry(
            hasCameraHardware = hasCameraHardware,
            hasCameraPermission = hasCameraPermission,
        )
    }

    // 앱 설정(PERMISSION_BLOCKED)에서 카메라 권한을 허용하고 돌아왔을 때 자동으로 훈련 준비 단계로 진입한다(T-3 가드).
    LifecycleResumeEffect(uiState.phase, context) {
        if (uiState.phase == SmileSessionPhase.PERMISSION_BLOCKED) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                viewModel.onCameraPermissionResult(isGranted = true, shouldShowRationale = false)
            }
        }
        onPauseOrDispose {}
    }

    SmileSideEffectsHandler(
        viewModel = viewModel,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onBack,
    )

    val isCameraPhase = uiState.phase.isCameraActivePhase()

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
                    onExit = onBack,
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
    onNavigateBack: () -> Unit,
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
            SmileTrainingSideEffect.NavigateBack -> {
                onNavigateBack()
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
}

@Composable
private fun SmileNonCameraPhaseContent(
    uiState: SmileTrainingUiState,
    viewModel: SmileTrainingViewModel,
    onExit: () -> Unit,
) {
    when (uiState.phase) {
        SmileSessionPhase.PERMISSION_RATIONALE -> PermissionRationaleContent(
            onAllowClick = viewModel::onConfirmPermissionRationale,
            onCancelClick = onExit,
        )
        SmileSessionPhase.PERMISSION_BLOCKED -> PermissionBlockedContent(
            onOpenSettingsClick = viewModel::onOpenSettingsClicked,
            onBackClick = onExit,
        )
        SmileSessionPhase.MODEL_PREPARING -> ModelPreparingContent(
            progressPercent = uiState.modelDownloadPercent,
            onCancelClick = onExit,
        )
        SmileSessionPhase.MODEL_UNAVAILABLE -> ModelUnavailableContent(
            reason = uiState.modelUnavailableReason ?: "얼굴 인식 모듈을 준비할 수 없습니다.",
            onRetryClick = viewModel::prepareModelAndProceed,
            onBackClick = onExit,
        )
        SmileSessionPhase.READY,
        SmileSessionPhase.ACTIVE,
        SmileSessionPhase.REP_SUCCESS,
        -> Unit
    }
}
