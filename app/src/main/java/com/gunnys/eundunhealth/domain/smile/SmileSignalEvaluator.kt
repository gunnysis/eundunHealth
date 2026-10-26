package com.gunnys.eundunhealth.domain.smile

import androidx.compose.runtime.Immutable
import com.gunnys.eundunhealth.domain.model.SmileDifficultyLevel
import com.gunnys.eundunhealth.domain.model.SmileRoutineType
import kotlin.math.abs
import kotlin.math.roundToLong

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
        val userGuide: String = "입꼬리를 부드럽게 올리고 유지해보세요",
    ) : FrameVerdict

    data class Unmeasurable(val reason: UnmeasurableReason) : FrameVerdict
}

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

class SmileRoutineRule(
    val routine: SmileRoutineType,
    val difficulty: SmileDifficultyLevel,
    val minFaceWidthRatio: Float = DEFAULT_MIN_FACE_WIDTH_RATIO,
    val eyeOpenThreshold: Float = DEFAULT_EYE_OPEN_THRESHOLD,
) {
    val enterSmileThreshold: Float =
        (routine.baseEnterSmileThreshold + difficulty.smileThresholdDelta).coerceIn(0.35f, 0.90f)
    val exitSmileThreshold: Float =
        (routine.baseExitSmileThreshold + difficulty.smileThresholdDelta).coerceIn(0.25f, enterSmileThreshold - 0.05f)
    val targetHoldDurationMs: Long =
        (routine.baseHoldDurationMs * difficulty.holdMultiplier).roundToLong().coerceAtLeast(800L)

    fun evaluate(signal: FrameSignal, currentlySmiling: Boolean): FrameVerdict {
        val precheck = checkFaceGeometry(signal)
        if (precheck != null) return precheck

        val eyeCheck = checkEyeRequirement(signal)
        if (eyeCheck != null) return eyeCheck

        val smile = signal.smileProbability
            ?: return FrameVerdict.Unmeasurable(UnmeasurableReason.SIGNAL_UNAVAILABLE)

        val threshold = if (currentlySmiling) exitSmileThreshold else enterSmileThreshold
        return if (smile >= threshold) {
            FrameVerdict.Satisfied
        } else {
            val guide = if (routine == SmileRoutineType.SMILE_AND_FRONT) {
                "정면을 바라보며 미소를 지어보세요"
            } else {
                "입꼬리를 부드럽게 올리고 유지해보세요"
            }
            FrameVerdict.BelowThreshold(guide)
        }
    }

    private fun checkFaceGeometry(signal: FrameSignal): FrameVerdict.Unmeasurable? = when {
        signal.faceCount <= 0 -> FrameVerdict.Unmeasurable(UnmeasurableReason.NO_FACE)
        signal.faceCount > 1 -> FrameVerdict.Unmeasurable(UnmeasurableReason.MULTIPLE_FACES)
        signal.faceWidthRatio == null || signal.faceWidthRatio < minFaceWidthRatio ->
            FrameVerdict.Unmeasurable(UnmeasurableReason.FACE_TOO_SMALL)
        signal.yawDeg == null || abs(signal.yawDeg) > difficulty.maxYawDeg ->
            FrameVerdict.Unmeasurable(UnmeasurableReason.NOT_FRONT_FACING)
        signal.rollDeg == null || abs(signal.rollDeg) > difficulty.maxRollDeg ->
            FrameVerdict.Unmeasurable(UnmeasurableReason.HEAD_TILTED)
        else -> null
    }

    private fun checkEyeRequirement(signal: FrameSignal): FrameVerdict? {
        if (!routine.requiresEyesOpen) return null
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

    companion object {
        const val DEFAULT_MIN_FACE_WIDTH_RATIO = 0.22f
        const val DEFAULT_EYE_OPEN_THRESHOLD = 0.60f
    }
}

/**
 * 순수 도메인 신호 평가기.
 *
 * - 프레임 수가 아니라 `timestampElapsedMs`(단조 시간) 기준으로 유지 시간을 계산한다.
 * - 최근 1,000ms 슬라이딩 윈도우 내 측정 가능 프레임의 충족 비율(>= 75%)과 히스테리시스를 결합해
 *   미세한 프레임 노이즈로 게이지가 즉시 0으로 튀는 현상을 방지한다.
 */
class SmileSignalEvaluator(
    private val rule: SmileRoutineRule,
    private val targetReps: Int,
    private val windowSizeMs: Long = DEFAULT_WINDOW_SIZE_MS,
    private val minWindowSatisfiedRatio: Float = DEFAULT_MIN_WINDOW_RATIO,
) {
    private data class TimedVerdict(val timestampMs: Long, val isSatisfied: Boolean)

    private val recentWindow = ArrayDeque<TimedVerdict>()
    private var completedReps: Int = 0
    private var currentlySmiling: Boolean = false
    private var accumulatedHoldMs: Long = 0L
    private var lastFrameTimestampMs: Long? = null
    private var lastTrackingId: Int? = null

    fun reset() {
        recentWindow.clear()
        completedReps = 0
        currentlySmiling = false
        accumulatedHoldMs = 0L
        lastFrameTimestampMs = null
        lastTrackingId = null
    }

    fun clearActiveHold() {
        recentWindow.clear()
        currentlySmiling = false
        accumulatedHoldMs = 0L
        lastFrameTimestampMs = null
    }

    fun onFrame(signal: FrameSignal): SmileEvaluatorSnapshot {
        if (completedReps >= targetReps) {
            return buildSnapshot(
                isFaceAligned = true,
                guideMessage = "모든 연습을 마쳤어요!",
                justCompletedRep = false,
            )
        }

        // 다른 얼굴 trackingId 로 바뀌면 진행 중이던 홀드 게이지를 초기화
        if (signal.trackingId != null && lastTrackingId != null && signal.trackingId != lastTrackingId) {
            clearActiveHold()
        }
        if (signal.trackingId != null) {
            lastTrackingId = signal.trackingId
        }

        val verdict = rule.evaluate(signal, currentlySmiling)
        val deltaMs = lastFrameTimestampMs?.let { prev ->
            (signal.timestampElapsedMs - prev).coerceIn(0L, MAX_FRAME_DELTA_MS)
        } ?: 0L
        lastFrameTimestampMs = signal.timestampElapsedMs

        return when (verdict) {
            is FrameVerdict.Unmeasurable -> {
                clearActiveHold()
                buildSnapshot(
                    isFaceAligned = false,
                    guideMessage = verdict.reason.userGuide,
                    justCompletedRep = false,
                )
            }
            is FrameVerdict.BelowThreshold -> {
                recordMeasurableVerdict(signal.timestampElapsedMs, isSatisfied = false)
                if (!isWindowSatisfied()) {
                    currentlySmiling = false
                    accumulatedHoldMs = (accumulatedHoldMs - deltaMs * DECAY_MULTIPLIER).coerceAtLeast(0L)
                }
                buildSnapshot(
                    isFaceAligned = true,
                    guideMessage = verdict.userGuide,
                    justCompletedRep = false,
                )
            }
            FrameVerdict.Satisfied -> {
                recordMeasurableVerdict(signal.timestampElapsedMs, isSatisfied = true)
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
                    buildSnapshot(
                        isFaceAligned = true,
                        guideMessage = msg,
                        justCompletedRep = true,
                    )
                } else {
                    buildSnapshot(
                        isFaceAligned = true,
                        guideMessage = "좋아요, 미소를 그대로 유지해보세요",
                        justCompletedRep = false,
                    )
                }
            }
        }
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
