package com.gunnys.eundunhealth.ui.smile

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gunnys.eundunhealth.data.preferences.ThemePreferences
import com.gunnys.eundunhealth.data.smile.MlKitFaceModuleManager
import com.gunnys.eundunhealth.data.smile.MlKitModuleState
import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.model.SmileSessionRecord
import com.gunnys.eundunhealth.domain.model.SmileSummaryStats
import com.gunnys.eundunhealth.domain.model.SmileTrainingSpec
import com.gunnys.eundunhealth.domain.model.toReportedAppError
import com.gunnys.eundunhealth.domain.repository.SmileTrainingRepository
import com.gunnys.eundunhealth.domain.smile.FrameSignal
import com.gunnys.eundunhealth.domain.smile.SmileSignalEvaluator
import dagger.hilt.android.lifecycle.HiltViewModel
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.roundToInt

private val KST_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

@Immutable
enum class SmileSessionPhase {
    INTRO,
    PERMISSION_RATIONALE,
    PERMISSION_BLOCKED,
    MODEL_PREPARING,
    MODEL_UNAVAILABLE,
    READY,
    ACTIVE,
    REP_SUCCESS,
    COMPLETED,
}

@Immutable
sealed interface SmileTrainingSideEffect {
    data object RequestCameraPermission : SmileTrainingSideEffect

    data object OpenAppSettings : SmileTrainingSideEffect

    data object TriggerRepSuccessHaptic : SmileTrainingSideEffect

    data class AnnounceAccessibility(val message: String) : SmileTrainingSideEffect

    data class ShowSnackbar(val message: String) : SmileTrainingSideEffect
}

@Immutable
data class SmileTrainingUiState(
    val phase: SmileSessionPhase = SmileSessionPhase.INTRO,
    val isPaused: Boolean = false,
    val readyAlignmentProgress: Float = 0f,
    val targetReps: Int = SmileTrainingSpec.TARGET_REPS,
    val currentRep: Int = 0,
    val holdProgress: Float = 0f,
    val heldDurationMs: Long = 0L,
    val targetHoldDurationMs: Long = SmileTrainingSpec.HOLD_DURATION_MS,
    val isInSmileState: Boolean = false,
    val isFaceAligned: Boolean = false,
    val guideMessage: String = "얼굴을 가이드 타원 안에 맞춰주세요",
    val modelDownloadPercent: Int? = null,
    val modelUnavailableReason: String? = null,
    val sessionDurationSec: Int = 0,
    val summaryStats: SmileSummaryStats = SmileSummaryStats(),
    val recentSessions: List<SmileSessionRecord> = emptyList(),
    val isSavingSession: Boolean = false,
    val isDeletingHistory: Boolean = false,
    val showExitConfirmDialog: Boolean = false,
    val showDeleteConfirmDialog: Boolean = false,
    val error: AppError? = null,
)

@HiltViewModel
class SmileTrainingViewModel @Inject constructor(
    private val repository: SmileTrainingRepository,
    private val moduleManager: MlKitFaceModuleManager,
    private val themePreferences: ThemePreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SmileTrainingUiState())
    val uiState: StateFlow<SmileTrainingUiState> = _uiState.asStateFlow()

    private val _sideEffect = Channel<SmileTrainingSideEffect>(Channel.BUFFERED)
    val sideEffect = _sideEffect.receiveAsFlow()

    private var evaluator: SmileSignalEvaluator = SmileSignalEvaluator()
    private var activeSessionStartedMs: Long? = null
    private var repTransitionJob: Job? = null
    private var hasSavedCurrentSession: Boolean = false

    init {
        observeRepositoryData()
        triggerOpportunisticSync()
    }

    private fun observeRepositoryData() {
        viewModelScope.launch {
            repository.observeSummaryStats().collectLatest { stats ->
                _uiState.update { it.copy(summaryStats = stats) }
            }
        }
        viewModelScope.launch {
            repository.observeSessions().collectLatest { records ->
                _uiState.update { it.copy(recentSessions = records.take(MAX_RECENT_HISTORY)) }
            }
        }
    }

    private fun triggerOpportunisticSync() {
        viewModelScope.launch {
            runCatching { repository.refreshRemoteHistoryIfEmpty() }
            runCatching { repository.syncPendingSessions() }
        }
    }

    fun onStartClicked(hasCameraPermission: Boolean) {
        _uiState.update { it.copy(error = null) }
        if (hasCameraPermission) {
            prepareModelAndProceed()
        } else {
            _uiState.update { it.copy(phase = SmileSessionPhase.PERMISSION_RATIONALE) }
        }
    }

    fun onConfirmPermissionRationale() {
        _sideEffect.trySend(SmileTrainingSideEffect.RequestCameraPermission)
    }

    fun onCameraPermissionResult(isGranted: Boolean, shouldShowRationale: Boolean) {
        if (isGranted) {
            prepareModelAndProceed()
        } else if (shouldShowRationale) {
            _uiState.update { it.copy(phase = SmileSessionPhase.PERMISSION_RATIONALE) }
        } else {
            _uiState.update { it.copy(phase = SmileSessionPhase.PERMISSION_BLOCKED) }
        }
    }

    fun onOpenSettingsClicked() {
        _sideEffect.trySend(SmileTrainingSideEffect.OpenAppSettings)
    }

    @Suppress("TooGenericExceptionCaught")
    fun prepareModelAndProceed() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    phase = SmileSessionPhase.MODEL_PREPARING,
                    modelDownloadPercent = null,
                    modelUnavailableReason = null,
                    error = null,
                )
            }
            val result = try {
                moduleManager.ensureModuleAvailable { installing ->
                    _uiState.update { it.copy(modelDownloadPercent = installing.progressPercent) }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                t.toReportedAppError()
                MlKitModuleState.Unavailable(
                    "얼굴 인식 모듈을 준비하지 못했습니다. 네트워크와 Google Play 서비스를 확인한 뒤 다시 시도해주세요.",
                )
            }
            when (result) {
                MlKitModuleState.Available -> moveToReadyPhase()
                is MlKitModuleState.Installing -> Unit
                is MlKitModuleState.Unavailable -> {
                    Sentry.addBreadcrumb(
                        Breadcrumb().apply {
                            category = "smile.model_unavailable"
                            level = SentryLevel.WARNING
                            message = result.reason
                        },
                    )
                    _uiState.update {
                        it.copy(
                            phase = SmileSessionPhase.MODEL_UNAVAILABLE,
                            modelUnavailableReason = result.reason,
                        )
                    }
                }
            }
        }
    }

    private fun moveToReadyPhase() {
        evaluator = SmileSignalEvaluator(targetReps = _uiState.value.targetReps)
        activeSessionStartedMs = null
        hasSavedCurrentSession = false
        _uiState.update {
            it.copy(
                phase = SmileSessionPhase.READY,
                isPaused = false,
                readyAlignmentProgress = 0f,
                currentRep = 0,
                holdProgress = 0f,
                heldDurationMs = 0L,
                targetHoldDurationMs = SmileTrainingSpec.HOLD_DURATION_MS,
                isInSmileState = false,
                isFaceAligned = false,
                guideMessage = "얼굴을 가이드 타원 안에 맞추면 자동으로 시작됩니다",
            )
        }
    }

    fun beginActiveExercise(nowElapsedMs: Long? = null) {
        val state = _uiState.value
        val isResumingPausedSession = state.isPaused || state.currentRep > 0
        if (!isResumingPausedSession) {
            evaluator = SmileSignalEvaluator(targetReps = state.targetReps)
            activeSessionStartedMs = nowElapsedMs
            hasSavedCurrentSession = false
        } else {
            evaluator.clearActiveHold()
            evaluator.resetReadyAlignment()
            if (activeSessionStartedMs == null && nowElapsedMs != null) {
                activeSessionStartedMs = nowElapsedMs
            }
        }
        _uiState.update {
            it.copy(
                phase = SmileSessionPhase.ACTIVE,
                isPaused = false,
                readyAlignmentProgress = 0f,
                currentRep = if (isResumingPausedSession) state.currentRep else 0,
                holdProgress = 0f,
                heldDurationMs = 0L,
                targetHoldDurationMs = SmileTrainingSpec.HOLD_DURATION_MS,
                isInSmileState = false,
                isFaceAligned = false,
                guideMessage = "정면을 바라보며 입꼬리를 부드럽게 올려보세요",
                error = null,
            )
        }
        val announceMsg = if (isResumingPausedSession) {
            "스마일 트레이닝 연습을 이어서 시작합니다."
        } else {
            "스마일 트레이닝 연습을 시작합니다."
        }
        _sideEffect.trySend(SmileTrainingSideEffect.AnnounceAccessibility(announceMsg))
    }

    fun pauseActiveExercise(
        reasonMessage: String = "일시정지됨 · 얼굴을 맞추고 이어서 시작을 눌러주세요",
    ) {
        val currentPhase = _uiState.value.phase
        if (currentPhase != SmileSessionPhase.ACTIVE && currentPhase != SmileSessionPhase.REP_SUCCESS) {
            return
        }
        repTransitionJob?.cancel()
        evaluator.clearActiveHold()
        evaluator.resetReadyAlignment()
        _uiState.update {
            it.copy(
                phase = SmileSessionPhase.READY,
                isPaused = true,
                readyAlignmentProgress = 0f,
                holdProgress = 0f,
                heldDurationMs = 0L,
                isInSmileState = false,
                isFaceAligned = false,
                guideMessage = reasonMessage,
            )
        }
    }

    fun onCameraLifecycleStopped() {
        val currentPhase = _uiState.value.phase
        if (currentPhase == SmileSessionPhase.ACTIVE || currentPhase == SmileSessionPhase.REP_SUCCESS) {
            pauseActiveExercise("화면 전환으로 일시정지되었습니다. 준비되면 이어서 시작해주세요")
        }
    }

    fun onCameraError(message: String) {
        repTransitionJob?.cancel()
        evaluator.reset()
        activeSessionStartedMs = null
        hasSavedCurrentSession = false
        Sentry.addBreadcrumb(
            Breadcrumb().apply {
                category = "smile.camera_error"
                level = SentryLevel.WARNING
                this.message = message
            },
        )
        _uiState.update { current ->
            current.copy(
                phase = SmileSessionPhase.INTRO,
                isPaused = false,
                readyAlignmentProgress = 0f,
                currentRep = 0,
                holdProgress = 0f,
                heldDurationMs = 0L,
                targetHoldDurationMs = SmileTrainingSpec.HOLD_DURATION_MS,
                isInSmileState = false,
                isFaceAligned = false,
                showExitConfirmDialog = false,
                error = AppError.Unknown(throwable = IllegalStateException(message), userMessage = message),
            )
        }
    }

    fun onFrameSignal(signal: FrameSignal) {
        when (_uiState.value.phase) {
            SmileSessionPhase.READY -> handleReadyFrame(signal)
            SmileSessionPhase.ACTIVE -> handleActiveFrame(signal)
            else -> Unit
        }
    }

    private fun handleReadyFrame(signal: FrameSignal) {
        val state = _uiState.value
        val readySnapshot = evaluator.evaluateReadyFrame(signal, allowAutoStart = !state.isPaused)
        _uiState.update { current ->
            current.copy(
                isFaceAligned = readySnapshot.isFaceAligned,
                readyAlignmentProgress = readySnapshot.alignmentProgress,
                guideMessage = if (current.isPaused && !readySnapshot.isFaceAligned) {
                    readySnapshot.guideMessage
                } else if (current.isPaused) {
                    "일시정지됨 · 얼굴이 정렬되었습니다. 이어서 시작을 눌러주세요"
                } else {
                    readySnapshot.guideMessage
                },
            )
        }
        if (readySnapshot.shouldAutoStart) {
            _sideEffect.trySend(SmileTrainingSideEffect.TriggerRepSuccessHaptic)
            beginActiveExercise(nowElapsedMs = signal.timestampElapsedMs)
        }
    }

    private fun handleActiveFrame(signal: FrameSignal) {
        val currentStart = activeSessionStartedMs
        if (currentStart == null || signal.timestampElapsedMs < currentStart) {
            activeSessionStartedMs = signal.timestampElapsedMs
        }

        val snapshot = evaluator.onFrame(signal)
        _uiState.update { current ->
            current.copy(
                currentRep = snapshot.currentRep,
                holdProgress = snapshot.holdProgress,
                heldDurationMs = snapshot.heldDurationMs,
                targetHoldDurationMs = snapshot.targetHoldDurationMs,
                isInSmileState = snapshot.isInSmileState,
                isFaceAligned = snapshot.isFaceAligned,
                guideMessage = snapshot.guideMessage,
            )
        }

        if (snapshot.justCompletedRep) {
            _sideEffect.trySend(SmileTrainingSideEffect.TriggerRepSuccessHaptic)
            if (snapshot.isSessionCompleted) {
                val elapsedSec = activeSessionStartedMs?.let { started ->
                    ((signal.timestampElapsedMs - started).coerceAtLeast(1000L) / 1000.0).roundToInt()
                } ?: ((snapshot.targetHoldDurationMs * snapshot.targetReps) / 1000L).toInt().coerceAtLeast(1)

                val safeDurationSec = elapsedSec.coerceAtLeast(1)
                val safeCompletedReps = snapshot.currentRep.coerceAtLeast(1)

                _uiState.update {
                    it.copy(
                        phase = SmileSessionPhase.COMPLETED,
                        sessionDurationSec = safeDurationSec,
                        error = null,
                    )
                }
                _sideEffect.trySend(
                    SmileTrainingSideEffect.AnnounceAccessibility("모든 미소 연습을 완료했습니다!"),
                )
                saveCompletedSessionAutomatically(
                    durationSec = safeDurationSec,
                    completedReps = safeCompletedReps,
                )
            } else {
                _uiState.update { it.copy(phase = SmileSessionPhase.REP_SUCCESS) }
                _sideEffect.trySend(
                    SmileTrainingSideEffect.AnnounceAccessibility(
                        "${snapshot.currentRep}번째 미소 완료. 잠시 후 다음 반복을 시작합니다.",
                    ),
                )
                repTransitionJob?.cancel()
                repTransitionJob = viewModelScope.launch {
                    delay(REP_REST_DELAY_MS)
                    if (_uiState.value.phase == SmileSessionPhase.REP_SUCCESS) {
                        _uiState.update {
                            it.copy(
                                phase = SmileSessionPhase.ACTIVE,
                                holdProgress = 0f,
                                heldDurationMs = 0L,
                                isInSmileState = false,
                                guideMessage = "다음 미소를 준비해주세요",
                            )
                        }
                    }
                }
            }
        }
    }

    private fun saveCompletedSessionAutomatically(
        durationSec: Int,
        completedReps: Int,
    ) {
        if (hasSavedCurrentSession) return
        hasSavedCurrentSession = true

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingSession = true, error = null) }
            repository.saveCompletedSession(
                durationSec = durationSec,
                completedReps = completedReps,
            ).onSuccess {
                runCatching { themePreferences.markSmileShortcutDismissed(LocalDate.now(KST_ZONE)) }
                _uiState.update { it.copy(isSavingSession = false) }
                viewModelScope.launch {
                    runCatching { repository.syncPendingSessions() }
                }
            }.onFailure { throwable ->
                hasSavedCurrentSession = false
                val appErr = throwable.toReportedAppError()
                _uiState.update { it.copy(isSavingSession = false, error = appErr) }
            }
        }
    }

    fun retrySaveCompletedSession() {
        val state = _uiState.value
        if (state.phase != SmileSessionPhase.COMPLETED || state.isSavingSession) return
        saveCompletedSessionAutomatically(
            durationSec = state.sessionDurationSec.coerceAtLeast(1),
            completedReps = state.currentRep.coerceAtLeast(1),
        )
    }

    fun retryBlockedSync() {
        viewModelScope.launch {
            repository.retryBlockedSessions()
                .onSuccess { count ->
                    if (count > 0) {
                        _sideEffect.trySend(
                            SmileTrainingSideEffect.ShowSnackbar("동기화 보류 기록 ${count}건을 다시 전송합니다."),
                        )
                        runCatching { repository.syncPendingSessions() }
                    }
                }
                .onFailure { throwable ->
                    val appErr = throwable.toReportedAppError()
                    _uiState.update { it.copy(error = appErr) }
                }
        }
    }

    fun requestDeleteAllHistory() {
        _uiState.update { it.copy(showDeleteConfirmDialog = true) }
    }

    fun dismissDeleteAllHistoryDialog() {
        _uiState.update { it.copy(showDeleteConfirmDialog = false) }
    }

    fun confirmDeleteAllHistory() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    showDeleteConfirmDialog = false,
                    isDeletingHistory = true,
                    error = null,
                )
            }
            repository.deleteAllSessions()
                .onSuccess {
                    _uiState.update { it.copy(isDeletingHistory = false) }
                    _sideEffect.trySend(
                        SmileTrainingSideEffect.ShowSnackbar("모든 스마일 트레이닝 기록이 삭제되었습니다."),
                    )
                }
                .onFailure { throwable ->
                    val appErr = throwable.toReportedAppError()
                    _uiState.update { it.copy(isDeletingHistory = false, error = appErr) }
                }
        }
    }

    fun onBackOrExitRequested(): Boolean {
        val currentPhase = _uiState.value.phase
        return when (currentPhase) {
            SmileSessionPhase.ACTIVE,
            SmileSessionPhase.REP_SUCCESS,
            -> {
                _uiState.update { it.copy(showExitConfirmDialog = true) }
                false
            }
            SmileSessionPhase.READY,
            SmileSessionPhase.PERMISSION_RATIONALE,
            SmileSessionPhase.PERMISSION_BLOCKED,
            SmileSessionPhase.MODEL_PREPARING,
            SmileSessionPhase.MODEL_UNAVAILABLE,
            SmileSessionPhase.COMPLETED,
            -> {
                resetToIntro()
                false
            }
            SmileSessionPhase.INTRO -> true
        }
    }

    fun dismissExitConfirmDialog() {
        _uiState.update { it.copy(showExitConfirmDialog = false) }
    }

    fun confirmAbortActiveSession() {
        repTransitionJob?.cancel()
        resetToIntro()
    }

    fun resetToIntro() {
        repTransitionJob?.cancel()
        evaluator.reset()
        activeSessionStartedMs = null
        hasSavedCurrentSession = false
        _uiState.update { current ->
            current.copy(
                phase = SmileSessionPhase.INTRO,
                isPaused = false,
                readyAlignmentProgress = 0f,
                currentRep = 0,
                holdProgress = 0f,
                heldDurationMs = 0L,
                targetHoldDurationMs = SmileTrainingSpec.HOLD_DURATION_MS,
                isInSmileState = false,
                isFaceAligned = false,
                sessionDurationSec = 0,
                showExitConfirmDialog = false,
                error = null,
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    companion object {
        const val REP_REST_DELAY_MS = 1200L
        private const val MAX_RECENT_HISTORY = 10
    }
}
