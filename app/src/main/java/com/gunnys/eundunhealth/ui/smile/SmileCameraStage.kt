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
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gunnys.eundunhealth.data.smile.MlKitSmileSignalAnalyzer
import com.gunnys.eundunhealth.domain.smile.FrameSignal
import com.gunnys.eundunhealth.ui.theme.MotionTokens
import com.gunnys.eundunhealth.ui.util.findActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import android.util.Size as AndroidSize

// Modern Forest & Mint HUD Tokens (v0.7.0)
private val DarkHudSurface = Color(0xCC0D1812)
private val VignetteScrim = Color(0x9908110C)
private val GuideBorderIdle = Color(0x99FFFFFF)
private val GuideBorderAligned = Color(0xFF9CF1B6)
private val ProgressStartColor = Color(0xFF7CDA9C)
private val ProgressEndColor = Color(0xFF9CF1B6)
private val WarningBadgeColor = Color(0xFFF59E0B)
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
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnFrameSignal by rememberUpdatedState(onFrameSignal)
    val currentOnLifecycleStopped by rememberUpdatedState(onLifecycleStopped)
    val currentOnPermissionRevoked by rememberUpdatedState(onPermissionRevoked)
    val currentOnCameraError by rememberUpdatedState(onCameraError)
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    val activeAnalyzerRef = remember { mutableStateOf<MlKitSmileSignalAnalyzer?>(null) }

    SideEffect {
        activeAnalyzerRef.value?.isPaused = uiState.phase == SmileSessionPhase.REP_SUCCESS
    }

    DisposableEffect(view, context) {
        val window = context.findActivity()?.window
        val controller = if (window != null) WindowCompat.getInsetsController(window, view) else null
        val prevLightStatusBars = controller?.isAppearanceLightStatusBars
        val prevLightNavigationBars = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            if (prevLightStatusBars != null) {
                controller.isAppearanceLightStatusBars = prevLightStatusBars
            }
            if (prevLightNavigationBars != null) {
                controller.isAppearanceLightNavigationBars = prevLightNavigationBars
            }
        }
    }

    LifecycleResumeEffect(context) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            currentOnPermissionRevoked()
        }
        onPauseOrDispose {}
    }

    LifecycleStartEffect(Unit) {
        onStopOrDispose {
            // T-1 방어: 세션 완료(NavigateBack)로 인해 컴포저블이 정상 Dispose되는 경우에는
            // lifecycle 상태가 여전히 STARTED 이상이므로 일시정지를 호출하지 않는다.
            // 오직 실제 Activity/화면이 백그라운드(STOPPED)로 내려간 경우에만 일시정지한다.
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                currentOnLifecycleStopped()
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    LaunchedEffect(lifecycleOwner, context) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val analysisExecutor = Executors.newSingleThreadExecutor()
        var analyzer: MlKitSmileSignalAnalyzer? = null
        var cameraProvider: ProcessCameraProvider? = null
        try {
            val createdAnalyzer = MlKitSmileSignalAnalyzer(
                onSignal = { signal ->
                    mainExecutor.execute {
                        currentOnFrameSignal(signal)
                    }
                },
                callbackExecutor = analysisExecutor,
            )
            analyzer = createdAnalyzer
            activeAnalyzerRef.value = createdAnalyzer

            val provider = ProcessCameraProvider.awaitInstance(context)
            cameraProvider = provider
            val bound = bindFrontCameraUseCases(
                provider = provider,
                lifecycleOwner = lifecycleOwner,
                analysisExecutor = analysisExecutor,
                analyzer = createdAnalyzer,
                onSurfaceRequest = { req -> surfaceRequest = req },
                onBindError = { msg -> currentOnCameraError(msg) },
            )
            if (bound) {
                awaitCancellation()
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: SecurityException) {
            currentOnPermissionRevoked()
        } catch (_: Throwable) {
            currentOnCameraError("카메라 서비스를 초기화할 수 없습니다. 잠시 후 다시 시도해주세요.")
        } finally {
            activeAnalyzerRef.value = null
            surfaceRequest = null
            runCatching { cameraProvider?.unbindAll() }
            val analyzerToClose = analyzer
            if (analyzerToClose != null) {
                runCatching { analysisExecutor.execute { analyzerToClose.close() } }
                    .onFailure { runCatching { analyzerToClose.close() } }
            }
            analysisExecutor.shutdown()
        }
    }

    val rawTargetProgress = when (uiState.phase) {
        SmileSessionPhase.READY -> uiState.readyAlignmentProgress
        SmileSessionPhase.REP_SUCCESS -> 1f
        else -> uiState.holdProgress
    }
    val animatedProgressState = animateFloatAsState(
        targetValue = rawTargetProgress.coerceIn(0f, 1f),
        animationSpec = MotionTokens.progressSpring(),
        label = "smileHoldProgress",
    )
    val progressProvider = remember(animatedProgressState) { { animatedProgressState.value } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .keepScreenOn()
            .background(Color.Black),
    ) {
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
            progressProvider = progressProvider,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            TopHudBar(
                currentRep = uiState.currentRep,
                targetReps = uiState.targetReps,
                onCloseClick = onCloseClick,
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(16.dp),
            )

            BottomCoachingOverlay(
                phase = uiState.phase,
                isPaused = uiState.isPaused,
                isFaceAligned = uiState.isFaceAligned,
                isInSmileState = uiState.isInSmileState,
                guideMessage = uiState.guideMessage,
                currentRep = uiState.currentRep,
                targetReps = uiState.targetReps,
                heldTenthsSec = uiState.heldTenthsSec,
                targetTenthsSec = uiState.targetTenthsSec,
                progressProvider = progressProvider,
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
): Boolean = runCatching {
    val selector = when {
        provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
        provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
        else -> {
            onBindError("이 기기에서 사용 가능한 카메라를 찾을 수 없습니다.")
            return false
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

    provider.unbindAll()
    provider.bindToLifecycle(lifecycleOwner, selector, preview, imageAnalysis)
    true
}.getOrElse {
    onBindError("카메라를 시작할 수 없습니다. 다른 앱에서 카메라를 사용 중인지 확인해주세요.")
    false
}

@Composable
private fun FaceOvalVignetteOverlay(
    phase: SmileSessionPhase,
    isFaceAligned: Boolean,
    isInSmileState: Boolean,
    progressProvider: () -> Float,
    modifier: Modifier = Modifier,
) {
    val isSmileHighlighted = phase == SmileSessionPhase.REP_SUCCESS || isInSmileState
    val targetBorderColor = when {
        isSmileHighlighted -> ProgressEndColor
        isFaceAligned -> GuideBorderAligned
        else -> GuideBorderIdle
    }
    val animatedBorderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = MotionTokens.quickSpring(),
        label = "ovalBorderColor",
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (isSmileHighlighted) 0.24f else 0f,
        animationSpec = MotionTokens.quickSpring(),
        label = "ovalGlowAlpha",
    )
    val progressBrush = remember {
        Brush.sweepGradient(
            listOf(ProgressStartColor, ProgressEndColor, ProgressStartColor),
        )
    }

    Spacer(
        modifier = modifier
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithCache {
                // T-6 방어: 폴더블 펼침/가로 모드/분할 화면에서도 상·하단 HUD와 겹치지 않도록
                // 타원 높이를 화면 높이의 44% 이하로 클램핑한다.
                val ovalHeight = minOf(size.width * 0.68f * 1.28f, size.height * 0.44f)
                val ovalWidth = ovalHeight / 1.28f
                val topLeft = Offset(
                    x = (size.width - ovalWidth) / 2f,
                    y = (size.height - ovalHeight) * 0.40f,
                )
                val ovalSize = Size(width = ovalWidth, height = ovalHeight)
                val borderStroke = Stroke(width = 3.dp.toPx())
                val glowStroke = Stroke(width = 12.dp.toPx())
                val ringPadding = 8.dp.toPx()
                val arcTopLeft = Offset(topLeft.x - ringPadding, topLeft.y - ringPadding)
                val arcSize = Size(ovalSize.width + ringPadding * 2, ovalSize.height + ringPadding * 2)
                val arcStroke = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)

                onDrawBehind {
                    drawRect(color = VignetteScrim)

                    drawOval(
                        color = Color.Transparent,
                        topLeft = topLeft,
                        size = ovalSize,
                        blendMode = BlendMode.Clear,
                    )

                    if (glowAlpha > 0.01f) {
                        drawOval(
                            color = animatedBorderColor.copy(alpha = glowAlpha),
                            topLeft = topLeft,
                            size = ovalSize,
                            style = glowStroke,
                        )
                    }

                    drawOval(
                        color = animatedBorderColor,
                        topLeft = topLeft,
                        size = ovalSize,
                        style = borderStroke,
                    )

                    val holdProgress = progressProvider()
                    if (holdProgress > 0.01f) {
                        drawArc(
                            brush = progressBrush,
                            startAngle = -90f,
                            sweepAngle = 360f * holdProgress,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = arcStroke,
                        )
                    }
                }
            },
    )
}

@Composable
private fun TopHudBar(
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
                        text = "스마일 트레이닝 · 2초 유지",
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
            val indicatorWidth by animateDpAsState(
                targetValue = if (completed) 20.dp else 8.dp,
                animationSpec = MotionTokens.quickSpring(),
                label = "repStepWidth",
            )
            val indicatorColor by animateColorAsState(
                targetValue = if (completed) ProgressEndColor else Color(0x4DFFFFFF),
                animationSpec = MotionTokens.quickSpring(),
                label = "repStepColor",
            )
            Box(
                modifier = Modifier
                    .size(width = indicatorWidth, height = 6.dp)
                    .background(
                        color = indicatorColor,
                        shape = CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun BottomCoachingOverlay(
    phase: SmileSessionPhase,
    isPaused: Boolean,
    isFaceAligned: Boolean,
    isInSmileState: Boolean,
    guideMessage: String,
    currentRep: Int,
    targetReps: Int,
    heldTenthsSec: Int,
    targetTenthsSec: Int,
    progressProvider: () -> Float,
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
            modifier = Modifier
                .animateContentSize(animationSpec = MotionTokens.standardSpring())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AlignmentStatusBadge(
                phase = phase,
                isPaused = isPaused,
                isFaceAligned = isFaceAligned,
                isInSmileState = isInSmileState,
            )

            AnimatedVisibility(visible = phase == SmileSessionPhase.REP_SUCCESS) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = ProgressEndColor,
                    modifier = Modifier.size(36.dp),
                )
            }

            Text(
                text = guideMessage,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )

            AnimatedVisibility(visible = phase == SmileSessionPhase.ACTIVE) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ActiveHoldProgressSection(
                        progressProvider = progressProvider,
                        heldTenthsSec = heldTenthsSec,
                        targetTenthsSec = targetTenthsSec,
                        onPauseExercise = onPauseExercise,
                        onStopExercise = onStopExercise,
                    )
                }
            }

            AnimatedVisibility(visible = phase == SmileSessionPhase.READY) {
                ReadyStartButton(
                    isPaused = isPaused,
                    currentRep = currentRep,
                    targetReps = targetReps,
                    onStartExercise = onStartExercise,
                )
            }
        }
    }
}

@Composable
private fun AlignmentStatusBadge(
    phase: SmileSessionPhase,
    isPaused: Boolean,
    isFaceAligned: Boolean,
    isInSmileState: Boolean,
) {
    val (label, badgeColor) = when {
        phase == SmileSessionPhase.REP_SUCCESS -> "반복 성공!" to ProgressEndColor
        phase == SmileSessionPhase.READY && isPaused -> "일시정지 상태" to WarningBadgeColor
        phase == SmileSessionPhase.READY && isFaceAligned -> "정렬 완료 · 1초 후 자동 시작" to GuideBorderAligned
        phase == SmileSessionPhase.READY -> "준비 단계 · 얼굴 정렬 시 자동 시작" to GuideBorderAligned
        isInSmileState -> "미소 유지 감지 중" to ProgressEndColor
        isFaceAligned -> "정면 정렬 완료 · 미소를 지어보세요" to GuideBorderAligned
        else -> "가이드 타원에 얼굴을 맞춰주세요" to WarningBadgeColor
    }
    val animatedBadgeColor by animateColorAsState(
        targetValue = badgeColor,
        animationSpec = MotionTokens.quickSpring(),
        label = "hudBadgeColor",
    )
    Surface(
        color = animatedBadgeColor.copy(alpha = 0.18f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, animatedBadgeColor.copy(alpha = 0.55f)),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = animatedBadgeColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ActiveHoldProgressSection(
    progressProvider: () -> Float,
    heldTenthsSec: Int,
    targetTenthsSec: Int,
    onPauseExercise: () -> Unit,
    onStopExercise: () -> Unit,
) {
    val safeHeldTenths = heldTenthsSec.coerceAtLeast(0)
    val safeTargetTenths = targetTenthsSec.coerceAtLeast(1)
    val heldSecText = "${safeHeldTenths / 10}.${safeHeldTenths % 10}"
    val targetSecText = "${safeTargetTenths / 10}.${safeTargetTenths % 10}"

    LinearProgressIndicator(
        progress = progressProvider,
        modifier = Modifier.fillMaxWidth().height(8.dp),
        color = ProgressEndColor,
        trackColor = Color(0x33FFFFFF),
        strokeCap = StrokeCap.Round,
    )

    Text(
        text = "유지 시간 ${heldSecText}초 / 목표 ${targetSecText}초",
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
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0x66FFFFFF)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        ) {
            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(" 일시정지", style = MaterialTheme.typography.labelLarge)
        }
        TextButton(
            onClick = onStopExercise,
            modifier = Modifier.heightIn(min = 48.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFB0BEC5)),
        ) {
            Text("연습 중단", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ReadyStartButton(
    isPaused: Boolean,
    currentRep: Int,
    targetReps: Int,
    onStartExercise: () -> Unit,
) {
    Spacer(Modifier.height(4.dp))
    val buttonLabel = if (isPaused || currentRep > 0) {
        " 이어서 시작하기 ($currentRep/${targetReps}회 완료)"
    } else {
        " 바로 시작하기"
    }
    Button(
        onClick = onStartExercise,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ProgressEndColor,
            contentColor = Color(0xFF062112),
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
