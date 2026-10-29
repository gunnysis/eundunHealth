package com.gunnys.eundunhealth.domain.smile

import androidx.compose.runtime.Immutable
import com.gunnys.eundunhealth.domain.model.SmileTrainingSpec
import kotlin.math.abs

@Immutable
data class FrameSignal(
    val timestampElapsedMs: Long,
    val faceCount: Int,
    val faceWidthRatio: Float?,
    val yawDeg: Float?,
    val rollDeg: Float?,
    val smileProbability: Float?,
    val leftEyeOpenProbability: Float?,
    val rightEyeOpenProbability: Float?,
    val trackingId: Int? = null,
)

enum class UnmeasurableReason(val userGuide: String) {
    NO_FACE("얼굴을 가이드 타원 안에 맞춰주세요"),
    MULTIPLE_FACES("화면에 한 사람만 보이도록 위치를 조정해주세요"),
    FACE_TOO_SMALL("카메라에 조금 더 가까이 다가와 주세요"),
    NOT_FRONT_FACING("카메라를 정면으로 편안하게 바라봐 주세요"),
    HEAD_TILTED("고개를 수평으로 편안하게 맞춰주세요"),
    EYES_NOT_VISIBLE("눈이 잘 보이도록 정면과 조명을 확인해주세요"),
    SIGNAL_UNAVAILABLE("더 밝은 곳에서 정면을 바라봐 주세요"),
}

sealed interface FrameVerdict {
    data object Satisfied : FrameVerdict

    data class BelowThreshold(
        val userGuide: String = "입꼬리를 부드럽게 올리고 2초간 유지해보세요",
    ) : FrameVerdict

    data class Unmeasurable(val reason: UnmeasurableReason) : FrameVerdict
}

@Immutable
data class ReadyAlignmentSnapshot(
    val isFaceAligned: Boolean,
    val alignmentProgress: Float,
    val shouldAutoStart: Boolean,
    val guideMessage: String,
)

@Immutable
data class SmileEvaluatorSnapshot(
    val currentRep: Int,
    val targetReps: Int,
    val holdProgress: Float,
    val heldDurationMs: Long,
    val targetHoldDurationMs: Long,
    val isInSmileState: Boolean,
    val isFaceAligned: Boolean,
    val guideMessage: String,
    val justCompletedRep: Boolean = false,
    val isSessionCompleted: Boolean = false,
)

class SmileStandardRule(
    val enterSmileThreshold: Float = SmileTrainingSpec.ENTER_SMILE_THRESHOLD,
    val exitSmileThreshold: Float = SmileTrainingSpec.EXIT_SMILE_THRESHOLD,
    val targetHoldDurationMs: Long = SmileTrainingSpec.HOLD_DURATION_MS,
    val minFaceWidthRatio: Float = SmileTrainingSpec.MIN_FACE_WIDTH_RATIO,
    val eyeOpenThreshold: Float = SmileTrainingSpec.MIN_EYE_OPEN_PROBABILITY,
    val maxYawDeg: Float = SmileTrainingSpec.MAX_YAW_DEG,
    val maxRollDeg: Float = SmileTrainingSpec.MAX_ROLL_DEG,
) {
    fun evaluateReadyAlignment(signal: FrameSignal): FrameVerdict {
        val precheck = checkFaceGeometry(signal)
        if (precheck != null) return precheck

        val eyeCheck = checkEyeRequirement(signal)
        if (eyeCheck != null) return eyeCheck

        return FrameVerdict.Satisfied
    }

    fun evaluate(signal: FrameSignal, currentlySmiling: Boolean, smoothedSmileProbability: Float? = signal.smileProbability): FrameVerdict {
        val precheck = checkFaceGeometry(signal)
        if (precheck != null) return precheck

        val eyeCheck = checkEyeRequirement(signal)
        if (eyeCheck != null) return eyeCheck

        val smile = smoothedSmileProbability
            ?: return FrameVerdict.Unmeasurable(UnmeasurableReason.SIGNAL_UNAVAILABLE)

        val threshold = if (currentlySmiling) exitSmileThreshold else enterSmileThreshold
        return if (smile >= threshold) {
            FrameVerdict.Satisfied
        } else {
            FrameVerdict.BelowThreshold("정면을 바라보며 입꼬리를 부드럽게 올려보세요")
        }
    }

    private fun checkFaceGeometry(signal: FrameSignal): FrameVerdict.Unmeasurable? = when {
        signal.faceCount <= 0 -> FrameVerdict.Unmeasurable(UnmeasurableReason.NO_FACE)
        signal.faceCount > 1 -> FrameVerdict.Unmeasurable(UnmeasurableReason.MULTIPLE_FACES)
        signal.faceWidthRatio == null || signal.faceWidthRatio < minFaceWidthRatio ->
            FrameVerdict.Unmeasurable(UnmeasurableReason.FACE_TOO_SMALL)
        signal.yawDeg == null || abs(signal.yawDeg) > maxYawDeg ->
            FrameVerdict.Unmeasurable(UnmeasurableReason.NOT_FRONT_FACING)
        signal.rollDeg == null || abs(signal.rollDeg) > maxRollDeg ->
            FrameVerdict.Unmeasurable(UnmeasurableReason.HEAD_TILTED)
        else -> null
    }

    private fun checkEyeRequirement(signal: FrameSignal): FrameVerdict? {
        val left = signal.leftEyeOpenProbability
        val right = signal.rightEyeOpenProbability
        if (left == null || right == null) {
            return FrameVerdict.Unmeasurable(UnmeasurableReason.EYES_NOT_VISIBLE)
        }
        return if (left < eyeOpenThreshold || right < eyeOpenThreshold) {
            FrameVerdict.BelowThreshold("두 눈을 편안하게 뜨고 정면을 바라봐 주세요")
        } else {
            null
        }
    }
}

/**
 * 순수 도메인 신호 평가기 (v2 단일 표준 스펙 2초 × 3회).
 *
 * - 프레임 수가 아니라 `timestampElapsedMs`(단조 시간) 기준으로 유지 시간을 계산한다.
 * - 지수 이동 평균(EMA, alpha=0.6) 스무딩 + 최근 1,000ms 슬라이딩 윈도우 충족 비율(>= 75%) + 히스테리시스를 결합해
 *   미세한 프레임 노이즈로 게이지가 튀는 현상을 방지한다.
 * - READY 단계에서는 얼굴 정렬이 1,000ms 연속 유지되면 자동 시작(`shouldAutoStart = true`)을 트리거한다.
 */
class SmileSignalEvaluator(
    private val rule: SmileStandardRule = SmileStandardRule(),
    private val targetReps: Int = SmileTrainingSpec.TARGET_REPS,
    private val windowSizeMs: Long = DEFAULT_WINDOW_SIZE_MS,
    private val minWindowSatisfiedRatio: Float = DEFAULT_MIN_WINDOW_RATIO,
    private val readyAutoStartHoldMs: Long = SmileTrainingSpec.READY_AUTO_START_HOLD_MS,
    private val emaAlpha: Float = SmileTrainingSpec.EMA_SMOOTHING_ALPHA,
) {
    private data class TimedVerdict(val timestampMs: Long, val isSatisfied: Boolean)

    private val recentWindow = ArrayDeque<TimedVerdict>()
    private var completedReps: Int = 0
    private var currentlySmiling: Boolean = false
    private var accumulatedHoldMs: Long = 0L
    private var lastFrameTimestampMs: Long? = null
    private var lastTrackingId: Int? = null
    private var smoothedSmileProbability: Float? = null

    private var readyAlignedAccumulatedMs: Long = 0L
    private var lastReadyTimestampMs: Long? = null

    fun reset() {
        recentWindow.clear()
        completedReps = 0
        currentlySmiling = false
        accumulatedHoldMs = 0L
        lastFrameTimestampMs = null
        lastTrackingId = null
        smoothedSmileProbability = null
        resetReadyAlignment()
    }

    fun resetReadyAlignment() {
        readyAlignedAccumulatedMs = 0L
        lastReadyTimestampMs = null
    }

    fun clearActiveHold() {
        recentWindow.clear()
        currentlySmiling = false
        accumulatedHoldMs = 0L
        lastFrameTimestampMs = null
        smoothedSmileProbability = null
    }

    fun evaluateReadyFrame(signal: FrameSignal, allowAutoStart: Boolean = true): ReadyAlignmentSnapshot {
        val deltaMs = lastReadyTimestampMs?.let { prev ->
            (signal.timestampElapsedMs - prev).coerceIn(0L, MAX_FRAME_DELTA_MS)
        } ?: 0L
        lastReadyTimestampMs = signal.timestampElapsedMs

        return when (val verdict = rule.evaluateReadyAlignment(signal)) {
            is FrameVerdict.Unmeasurable -> {
                readyAlignedAccumulatedMs = 0L
                ReadyAlignmentSnapshot(
                    isFaceAligned = false,
                    alignmentProgress = 0f,
                    shouldAutoStart = false,
                    guideMessage = verdict.reason.userGuide,
                )
            }
            is FrameVerdict.BelowThreshold -> {
                readyAlignedAccumulatedMs = 0L
                ReadyAlignmentSnapshot(
                    isFaceAligned = false,
                    alignmentProgress = 0f,
                    shouldAutoStart = false,
                    guideMessage = verdict.userGuide,
                )
            }
            FrameVerdict.Satisfied -> {
                if (allowAutoStart) {
                    readyAlignedAccumulatedMs += deltaMs
                } else {
                    readyAlignedAccumulatedMs = 0L
                }
                val progress = (readyAlignedAccumulatedMs.toFloat() / readyAutoStartHoldMs.toFloat()).coerceIn(0f, 1f)
                val shouldStart = allowAutoStart && readyAlignedAccumulatedMs >= readyAutoStartHoldMs
                val guide = if (allowAutoStart) {
                    "자세가 좋아요! 잠시 후 연습이 자동으로 시작됩니다"
                } else {
                    "연습이 일시정지되었습니다. 아래 버튼을 눌러 이어서 시작하세요"
                }
                ReadyAlignmentSnapshot(
                    isFaceAligned = true,
                    alignmentProgress = progress,
                    shouldAutoStart = shouldStart,
                    guideMessage = guide,
                )
            }
        }
    }

    fun onFrame(signal: FrameSignal): SmileEvaluatorSnapshot {
        if (completedReps >= targetReps) {
            return buildSnapshot(
                isFaceAligned = true,
                guideMessage = "모든 연습을 마쳤어요!",
                justCompletedRep = false,
            )
        }

        updateTrackingId(signal.trackingId)
        val smoothedSmile = computeSmoothedSmile(signal.smileProbability)
        val verdict = rule.evaluate(signal, currentlySmiling, smoothedSmile)
        val deltaMs = computeFrameDeltaMs(signal.timestampElapsedMs)

        return when (verdict) {
            is FrameVerdict.Unmeasurable -> {
                clearActiveHold()
                buildSnapshot(
                    isFaceAligned = false,
                    guideMessage = verdict.reason.userGuide,
                    justCompletedRep = false,
                )
            }
            is FrameVerdict.BelowThreshold -> handleBelowThresholdVerdict(
                verdict = verdict,
                timestampElapsedMs = signal.timestampElapsedMs,
                deltaMs = deltaMs,
            )
            FrameVerdict.Satisfied -> handleSatisfiedVerdict(
                timestampElapsedMs = signal.timestampElapsedMs,
                deltaMs = deltaMs,
            )
        }
    }

    private fun updateTrackingId(trackingId: Int?) {
        // 다른 얼굴 trackingId 로 바뀌면 진행 중이던 홀드 게이지를 초기화
        if (trackingId != null && lastTrackingId != null && trackingId != lastTrackingId) {
            clearActiveHold()
        }
        if (trackingId != null) {
            lastTrackingId = trackingId
        }
    }

    private fun computeSmoothedSmile(rawProbability: Float?): Float? = rawProbability?.let { raw ->
        val clamped = raw.coerceIn(0f, 1f)
        val next = smoothedSmileProbability?.let { prev ->
            emaAlpha * clamped + (1f - emaAlpha) * prev
        } ?: clamped
        smoothedSmileProbability = next
        next
    }

    private fun computeFrameDeltaMs(timestampElapsedMs: Long): Long {
        val deltaMs = lastFrameTimestampMs?.let { prev ->
            (timestampElapsedMs - prev).coerceIn(0L, MAX_FRAME_DELTA_MS)
        } ?: 0L
        lastFrameTimestampMs = timestampElapsedMs
        return deltaMs
    }

    private fun handleBelowThresholdVerdict(
        verdict: FrameVerdict.BelowThreshold,
        timestampElapsedMs: Long,
        deltaMs: Long,
    ): SmileEvaluatorSnapshot {
        recordMeasurableVerdict(timestampElapsedMs, isSatisfied = false)
        val windowStillSatisfied = isWindowSatisfied()
        if (!windowStillSatisfied) {
            currentlySmiling = false
            accumulatedHoldMs = (accumulatedHoldMs - deltaMs * DECAY_MULTIPLIER).coerceAtLeast(0L)
        }
        val guideMessage = if (windowStillSatisfied && currentlySmiling) {
            "좋아요, 미소를 그대로 유지해보세요"
        } else {
            verdict.userGuide
        }
        return buildSnapshot(
            isFaceAligned = true,
            guideMessage = guideMessage,
            justCompletedRep = false,
        )
    }

    private fun handleSatisfiedVerdict(
        timestampElapsedMs: Long,
        deltaMs: Long,
    ): SmileEvaluatorSnapshot {
        recordMeasurableVerdict(timestampElapsedMs, isSatisfied = true)
        currentlySmiling = true
        if (isWindowSatisfied()) {
            accumulatedHoldMs += deltaMs
        }
        if (accumulatedHoldMs >= rule.targetHoldDurationMs) {
            completedReps += 1
            clearActiveHold()
            val finishedAll = completedReps >= targetReps
            val msg = if (finishedAll) {
                "모든 연습을 마쳤어요!"
            } else {
                "좋아요! 잠시 숨을 고르고 다음 반복을 이어가요"
            }
            return buildSnapshot(
                isFaceAligned = true,
                guideMessage = msg,
                justCompletedRep = true,
            )
        }
        return buildSnapshot(
            isFaceAligned = true,
            guideMessage = "좋아요, 미소를 그대로 유지해보세요",
            justCompletedRep = false,
        )
    }

    private fun recordMeasurableVerdict(timestampMs: Long, isSatisfied: Boolean) {
        recentWindow.addLast(TimedVerdict(timestampMs, isSatisfied))
        val cutoff = timestampMs - windowSizeMs
        while (recentWindow.isNotEmpty() && recentWindow.first().timestampMs < cutoff) {
            recentWindow.removeFirst()
        }
    }

    private fun isWindowSatisfied(): Boolean {
        if (recentWindow.isEmpty()) return false
        val satisfiedCount = recentWindow.count { it.isSatisfied }
        val ratio = satisfiedCount.toFloat() / recentWindow.size.toFloat()
        return ratio >= minWindowSatisfiedRatio
    }

    private fun buildSnapshot(
        isFaceAligned: Boolean,
        guideMessage: String,
        justCompletedRep: Boolean,
    ): SmileEvaluatorSnapshot {
        val progress = (accumulatedHoldMs.toFloat() / rule.targetHoldDurationMs.toFloat()).coerceIn(0f, 1f)
        return SmileEvaluatorSnapshot(
            currentRep = completedReps,
            targetReps = targetReps,
            holdProgress = progress,
            heldDurationMs = accumulatedHoldMs,
            targetHoldDurationMs = rule.targetHoldDurationMs,
            isInSmileState = currentlySmiling,
            isFaceAligned = isFaceAligned,
            guideMessage = guideMessage,
            justCompletedRep = justCompletedRep,
            isSessionCompleted = completedReps >= targetReps,
        )
    }

    companion object {
        const val DEFAULT_WINDOW_SIZE_MS = 1000L
        const val DEFAULT_MIN_WINDOW_RATIO = 0.75f
        private const val MAX_FRAME_DELTA_MS = 350L
        private const val DECAY_MULTIPLIER = 2L
    }
}
