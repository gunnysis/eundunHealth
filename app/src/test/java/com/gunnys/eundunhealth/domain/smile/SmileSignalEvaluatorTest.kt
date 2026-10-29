package com.gunnys.eundunhealth.domain.smile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmileSignalEvaluatorTest {

    @Test
    fun `unmeasurable reasons are prioritized in order of face count, size, yaw, roll`() {
        val rule = SmileStandardRule()

        val noFace = rule.evaluate(signal(faceCount = 0), currentlySmiling = false)
        assertEquals(FrameVerdict.Unmeasurable(UnmeasurableReason.NO_FACE), noFace)

        val multiFace = rule.evaluate(signal(faceCount = 2), currentlySmiling = false)
        assertEquals(FrameVerdict.Unmeasurable(UnmeasurableReason.MULTIPLE_FACES), multiFace)

        val smallFace = rule.evaluate(signal(faceWidthRatio = 0.15f), currentlySmiling = false)
        assertEquals(FrameVerdict.Unmeasurable(UnmeasurableReason.FACE_TOO_SMALL), smallFace)

        val turnedHead = rule.evaluate(signal(yawDeg = 22f), currentlySmiling = false)
        assertEquals(FrameVerdict.Unmeasurable(UnmeasurableReason.NOT_FRONT_FACING), turnedHead)

        val tiltedHead = rule.evaluate(signal(rollDeg = -18f), currentlySmiling = false)
        assertEquals(FrameVerdict.Unmeasurable(UnmeasurableReason.HEAD_TILTED), tiltedHead)
    }

    @Test
    fun `hysteresis keeps smile state between exit and enter thresholds`() {
        val rule = SmileStandardRule()
        // v2 standard: enter=0.55, exit=0.45
        val belowEnterWhenIdle = rule.evaluate(signal(smile = 0.50f), currentlySmiling = false)
        assertTrue(belowEnterWhenIdle is FrameVerdict.BelowThreshold)

        val keptWhenAlreadySmiling = rule.evaluate(signal(smile = 0.50f), currentlySmiling = true)
        assertEquals(FrameVerdict.Satisfied, keptWhenAlreadySmiling)

        val droppedBelowExit = rule.evaluate(signal(smile = 0.40f), currentlySmiling = true)
        assertTrue(droppedBelowExit is FrameVerdict.BelowThreshold)
    }

    @Test
    fun `standard rule requires both eyes open`() {
        val rule = SmileStandardRule()

        val eyesNull = rule.evaluate(
            signal(smile = 0.80f, leftEye = null, rightEye = 0.90f),
            currentlySmiling = false,
        )
        assertEquals(FrameVerdict.Unmeasurable(UnmeasurableReason.EYES_NOT_VISIBLE), eyesNull)

        val oneEyeClosed = rule.evaluate(
            signal(smile = 0.80f, leftEye = 0.20f, rightEye = 0.90f),
            currentlySmiling = false,
        )
        assertTrue(oneEyeClosed is FrameVerdict.BelowThreshold)

        val bothOpen = rule.evaluate(
            signal(smile = 0.80f, leftEye = 0.85f, rightEye = 0.90f),
            currentlySmiling = false,
        )
        assertEquals(FrameVerdict.Satisfied, bothOpen)
    }

    @Test
    fun `evaluateReadyFrame triggers shouldAutoStart after 1000ms continuous alignment when allowAutoStart is true`() {
        val evaluator = SmileSignalEvaluator()

        val r0 = evaluator.evaluateReadyFrame(signal(t = 0L), allowAutoStart = true)
        assertTrue(r0.isFaceAligned)
        assertEquals(0f, r0.alignmentProgress, 0.001f)
        assertFalse(r0.shouldAutoStart)

        val r1 = evaluator.evaluateReadyFrame(signal(t = 300L), allowAutoStart = true)
        assertEquals(0.30f, r1.alignmentProgress, 0.001f)
        assertFalse(r1.shouldAutoStart)

        val r2 = evaluator.evaluateReadyFrame(signal(t = 650L), allowAutoStart = true)
        assertEquals(0.65f, r2.alignmentProgress, 0.001f)
        assertFalse(r2.shouldAutoStart)

        val r3 = evaluator.evaluateReadyFrame(signal(t = 1000L), allowAutoStart = true)
        assertEquals(1.0f, r3.alignmentProgress, 0.001f)
        assertTrue(r3.shouldAutoStart)

        // When allowAutoStart is false (paused state), alignment is reported but shouldAutoStart stays false
        evaluator.resetReadyAlignment()
        evaluator.evaluateReadyFrame(signal(t = 2000L), allowAutoStart = false)
        val paused = evaluator.evaluateReadyFrame(signal(t = 3200L), allowAutoStart = false)
        assertTrue(paused.isFaceAligned)
        assertFalse(paused.shouldAutoStart)
    }

    @Test
    fun `completes rep when monotonic hold duration reaches 2000ms and tolerates single noisy frame via EMA and window`() {
        val evaluator = SmileSignalEvaluator(targetReps = 2)

        // Frame 0 at t=0ms
        val s0 = evaluator.onFrame(signal(t = 0L, smile = 0.75f))
        assertEquals(0, s0.currentRep)
        assertEquals(0L, s0.heldDurationMs)

        // Hold for 1500ms (t=300, 600, 900, 1200, 1500)
        evaluator.onFrame(signal(t = 300L, smile = 0.75f))
        evaluator.onFrame(signal(t = 600L, smile = 0.75f))
        evaluator.onFrame(signal(t = 900L, smile = 0.75f))
        evaluator.onFrame(signal(t = 1200L, smile = 0.75f))
        val s5 = evaluator.onFrame(signal(t = 1500L, smile = 0.75f))
        assertEquals(1500L, s5.heldDurationMs)

        // 1 brief dip at t=1600ms -> window still >= 75% satisfied, hold progress does not reset
        val dip = evaluator.onFrame(signal(t = 1600L, smile = 0.10f))
        assertFalse(dip.justCompletedRep)
        assertTrue(dip.isInSmileState)
        assertEquals("좋아요, 미소를 그대로 유지해보세요", dip.guideMessage)

        // Next frames reach >= 2000ms total held duration -> completes rep 1!
        evaluator.onFrame(signal(t = 1900L, smile = 0.80f))
        val rep1 = evaluator.onFrame(signal(t = 2200L, smile = 0.80f))
        assertTrue(rep1.justCompletedRep)
        assertEquals(1, rep1.currentRep)
        assertFalse(rep1.isSessionCompleted)
    }

    @Test
    fun `trackingId switch clears active hold progress to prevent cross-person carry-over`() {
        val evaluator = SmileSignalEvaluator(targetReps = 1)

        evaluator.onFrame(signal(t = 0L, smile = 0.80f, trackingId = 10))
        val held = evaluator.onFrame(signal(t = 300L, smile = 0.80f, trackingId = 10))
        assertEquals(300L, held.heldDurationMs)

        // Different person (trackingId = 20) enters frame at t=600L
        val switched = evaluator.onFrame(signal(t = 600L, smile = 0.80f, trackingId = 20))
        assertEquals(0L, switched.heldDurationMs)
        val nextForNewPerson = evaluator.onFrame(signal(t = 900L, smile = 0.80f, trackingId = 20))
        assertEquals(300L, nextForNewPerson.heldDurationMs)
    }

    private fun signal(
        t: Long = 1000L,
        faceCount: Int = 1,
        faceWidthRatio: Float? = 0.40f,
        yawDeg: Float? = 0f,
        rollDeg: Float? = 0f,
        smile: Float? = 0.80f,
        leftEye: Float? = 0.90f,
        rightEye: Float? = 0.90f,
        trackingId: Int? = 1,
    ) = FrameSignal(
        timestampElapsedMs = t,
        faceCount = faceCount,
        faceWidthRatio = faceWidthRatio,
        yawDeg = yawDeg,
        rollDeg = rollDeg,
        smileProbability = smile,
        leftEyeOpenProbability = leftEye,
        rightEyeOpenProbability = rightEye,
        trackingId = trackingId,
    )
}
