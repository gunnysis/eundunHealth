package com.gunnys.eundunhealth.ui.smile

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gunnys.eundunhealth.data.smile.MlKitSmileSignalAnalyzer
import com.gunnys.eundunhealth.domain.smile.FrameSignal
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import android.util.Size as AndroidSize

private val DarkHudSurface = Color(0xCC10141D)
private val VignetteScrim = Color(0x990B0E14)
private val GuideBorderIdle = Color(0x99FFFFFF)
private val GuideBorderAligned = Color(0xFF40C4FF)
private val ProgressStartColor = Color(0xFF40C4FF)
private val ProgressEndColor = Color(0xFF69F0AE)
private val WarningBadgeColor = Color(0xFFFFD54F)
private val AnalysisTargetResolution = AndroidSize(640, 480)

@Composable
internal fun SmileCameraStage(
    uiState: SmileTrainingUiState,
    onFrameSignal: (FrameSignal) -> Unit,
    onStartExercise: () -> Unit,
    onPauseExercise: () -> Unit,
    onLifecycleStopped: () -> Unit,
    onPermissionRevoked: () -> Unit,
    onCameraError: (String) -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnFrameSignal by rememberUpdatedState(onFrameSignal)
    val currentOnLifecycleStopped by rememberUpdatedState(onLifecycleStopped)
    val currentOnPermissionRevoked by rememberUpdatedState(onPermissionRevoked)
    val currentOnCameraError by rememberUpdatedState(onCameraError)
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> currentOnLifecycleStopped()
                Lifecycle.Event.ON_RESUME -> {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!granted) {
                        currentOnPermissionRevoked()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        val analysisExecutor = Executors.newSingleThreadExecutor()
        val analyzer = MlKitSmileSignalAnalyzer(
            onSignal = { signal -> currentOnFrameSignal(signal) },
        )
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)

        val bindRunnable = Runnable {
            val provider = runCatching { cameraProviderFuture.get() }.getOrNull()
            if (provider == null) {
                currentOnCameraError("카메라 서비스를 초기화할 수 없습니다.")
                return@Runnable
            }
            bindFrontCameraUseCases(
                provider = provider,
                lifecycleOwner = lifecycleOwner,
                analysisExecutor = analysisExecutor,
                analyzer = analyzer,
                onSurfaceRequest = { surfaceRequest = it },
                onBindError = { msg -> currentOnCameraError(msg) },
            )
        }
        cameraProviderFuture.addListener(bindRunnable, mainExecutor)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            surfaceRequest = null
            runCatching { cameraProviderFuture.get().unbindAll() }
            analyzer.close()
            analysisExecutor.shutdown()
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = uiState.holdProgress.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "smileHoldProgress",
    )

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        surfaceRequest?.let { req ->
            CameraXViewfinder(
                surfaceRequest = req,
                modifier = Modifier.fillMaxSize(),
            )
        }

        FaceOvalVignetteOverlay(
            phase = uiState.phase,
            isFaceAligned = uiState.isFaceAligned,
            isInSmileState = uiState.isInSmileState,
            holdProgress = animatedProgress,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            TopHudBar(
                routineName = uiState.selectedRoutine.displayName,
                difficultyName = uiState.selectedDifficulty.displayName,
                currentRep = uiState.currentRep,
                targetReps = uiState.targetReps,
                onCloseClick = onCloseClick,
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(16.dp),
            )

            BottomCoachingOverlay(
                uiState = uiState,
                animatedProgress = animatedProgress,
                onStartExercise = onStartExercise,
                onPauseExercise = onPauseExercise,
                onStopExercise = onCloseClick,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(20.dp),
            )
        }
    }
}

private fun bindFrontCameraUseCases(
    provider: ProcessCameraProvider,
    lifecycleOwner: LifecycleOwner,
    analysisExecutor: Executor,
    analyzer: MlKitSmileSignalAnalyzer,
    onSurfaceRequest: (SurfaceRequest) -> Unit,
    onBindError: (String) -> Unit,
) {
    val selector = when {
        provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
        provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
        else -> {
            onBindError("이 기기에서 사용 가능한 카메라를 찾을 수 없습니다.")
            return
        }
    }

    val preview = Preview.Builder().build().also { previewUseCase ->
        previewUseCase.setSurfaceProvider(onSurfaceRequest)
    }
    val resolutionSelector = ResolutionSelector.Builder()
        .setResolutionStrategy(
            ResolutionStrategy(
                AnalysisTargetResolution,
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
            ),
        )
        .build()

    val imageAnalysis = ImageAnalysis.Builder()
        .setResolutionSelector(resolutionSelector)
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()
        .also { it.setAnalyzer(analysisExecutor, analyzer) }

    runCatching {
        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, selector, preview, imageAnalysis)
    }.onFailure {
        onBindError("카메라를 시작할 수 없습니다. 다른 앱에서 카메라를 사용 중인지 확인해주세요.")
    }
}

@Composable
private fun FaceOvalVignetteOverlay(
    phase: SmileSessionPhase,
    isFaceAligned: Boolean,
    isInSmileState: Boolean,
    holdProgress: Float,
    modifier: Modifier = Modifier,
) {
    val borderColor = when {
        phase == SmileSessionPhase.REP_SUCCESS || isInSmileState -> ProgressEndColor
        isFaceAligned -> GuideBorderAligned
        else -> GuideBorderIdle
    }
    val progressBrush = remember {
        Brush.sweepGradient(
            listOf(ProgressStartColor, ProgressEndColor, ProgressStartColor),
        )
    }

    Canvas(
        modifier = modifier.graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
        },
    ) {
        drawRect(color = VignetteScrim)

        val ovalWidth = size.width * 0.68f
        val ovalHeight = ovalWidth * 1.28f
        val topLeft = Offset(
            x = (size.width - ovalWidth) / 2f,
            y = (size.height - ovalHeight) * 0.40f,
        )
        val ovalSize = Size(width = ovalWidth, height = ovalHeight)

        drawOval(
            color = Color.Transparent,
            topLeft = topLeft,
            size = ovalSize,
            blendMode = BlendMode.Clear,
        )

        drawOval(
            color = borderColor,
            topLeft = topLeft,
            size = ovalSize,
            style = Stroke(width = 3.dp.toPx()),
        )

        val effectiveProgress = if (phase == SmileSessionPhase.REP_SUCCESS) 1f else holdProgress
        if (effectiveProgress > 0.01f) {
            val ringPadding = 8.dp.toPx()
            drawArc(
                brush = progressBrush,
                startAngle = -90f,
                sweepAngle = 360f * effectiveProgress,
                useCenter = false,
                topLeft = Offset(topLeft.x - ringPadding, topLeft.y - ringPadding),
                size = Size(ovalSize.width + ringPadding * 2, ovalSize.height + ringPadding * 2),
                style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun TopHudBar(
    routineName: String,
    difficultyName: String,
    currentRep: Int,
    targetReps: Int,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = DarkHudSurface,
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "$routineName · $difficultyName",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                    )
                    Text(
                        text = "$currentRep / ${targetReps}회",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = ProgressEndColor,
                    )
                }
                RepStepIndicatorRow(currentRep = currentRep, targetReps = targetReps)
            }
        }

        Surface(
            color = DarkHudSurface,
            shape = CircleShape,
        ) {
            IconButton(onClick = onCloseClick) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "스마일 트레이닝 닫기",
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun RepStepIndicatorRow(
    currentRep: Int,
    targetReps: Int,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(targetReps.coerceIn(1, 5)) { index ->
            val completed = index < currentRep
            Box(
                modifier = Modifier
                    .size(width = if (completed) 18.dp else 8.dp, height = 6.dp)
                    .background(
                        color = if (completed) ProgressEndColor else Color(0x4DFFFFFF),
                        shape = CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun BottomCoachingOverlay(
    uiState: SmileTrainingUiState,
    animatedProgress: Float,
    onStartExercise: () -> Unit,
    onPauseExercise: () -> Unit,
    onStopExercise: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = DarkHudSurface,
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AlignmentStatusBadge(
                phase = uiState.phase,
                isFaceAligned = uiState.isFaceAligned,
                isInSmileState = uiState.isInSmileState,
            )

            if (uiState.phase == SmileSessionPhase.REP_SUCCESS) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = ProgressEndColor,
                    modifier = Modifier.size(36.dp),
                )
            }

            Text(
                text = uiState.guideMessage,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )

            if (uiState.phase == SmileSessionPhase.ACTIVE) {
                ActiveHoldProgressSection(
                    animatedProgress = animatedProgress,
                    heldDurationMs = uiState.heldDurationMs,
                    targetHoldDurationMs = uiState.targetHoldDurationMs,
                    onPauseExercise = onPauseExercise,
                    onStopExercise = onStopExercise,
                )
            }

            if (uiState.phase == SmileSessionPhase.READY) {
                ReadyStartButton(
                    currentRep = uiState.currentRep,
                    targetReps = uiState.targetReps,
                    onStartExercise = onStartExercise,
                )
            }
        }
    }
}

@Composable
private fun AlignmentStatusBadge(
    phase: SmileSessionPhase,
    isFaceAligned: Boolean,
    isInSmileState: Boolean,
) {
    val (label, badgeColor) = when {
        phase == SmileSessionPhase.REP_SUCCESS -> "반복 성공!" to ProgressEndColor
        phase == SmileSessionPhase.READY -> "준비 단계 · 가이드에 얼굴 맞추기" to GuideBorderAligned
        isInSmileState -> "미소 유지 감지 중" to ProgressEndColor
        isFaceAligned -> "정면 정렬 완료 · 미소를 지어보세요" to GuideBorderAligned
        else -> "가이드 타원에 얼굴을 맞춰주세요" to WarningBadgeColor
    }
    Surface(
        color = badgeColor.copy(alpha = 0.18f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.55f)),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = badgeColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ActiveHoldProgressSection(
    animatedProgress: Float,
    heldDurationMs: Long,
    targetHoldDurationMs: Long,
    onPauseExercise: () -> Unit,
    onStopExercise: () -> Unit,
) {
    val heldSec = String.format(Locale.US, "%.1f", heldDurationMs / 1000f)
    val targetSec = String.format(Locale.US, "%.1f", targetHoldDurationMs / 1000f)

    LinearProgressIndicator(
        progress = { animatedProgress },
        modifier = Modifier.fillMaxWidth().height(8.dp),
        color = ProgressEndColor,
        trackColor = Color(0x33FFFFFF),
        strokeCap = StrokeCap.Round,
    )

    Text(
        text = "유지 시간 ${heldSec}초 / 목표 ${targetSec}초",
        style = MaterialTheme.typography.bodyMedium,
        color = Color(0xFFCFD8DC),
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onPauseExercise,
            modifier = Modifier.weight(1f).height(44.dp),
            border = BorderStroke(1.dp, Color(0x66FFFFFF)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        ) {
            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(" 일시정지", style = MaterialTheme.typography.labelLarge)
        }
        TextButton(
            onClick = onStopExercise,
            modifier = Modifier.height(44.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFB0BEC5)),
        ) {
            Text("연습 중단", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ReadyStartButton(
    currentRep: Int,
    targetReps: Int,
    onStartExercise: () -> Unit,
) {
    Spacer(Modifier.height(4.dp))
    val buttonLabel = if (currentRep > 0) {
        " 이어서 연습 시작 ($currentRep/${targetReps}회 완료)"
    } else {
        " 준비 완료 · 연습 시작"
    }
    Button(
        onClick = onStartExercise,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ProgressEndColor,
            contentColor = Color(0xFF0A1912),
        ),
    ) {
        Icon(Icons.Default.PlayArrow, contentDescription = null)
        Text(
            text = buttonLabel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}
