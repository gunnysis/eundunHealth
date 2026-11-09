package com.gunnys.eundunhealth.data.smile

import android.graphics.Rect
import com.google.mlkit.vision.face.Face
import com.gunnys.eundunhealth.data.smile.MlKitSmileSignalAnalyzer.Companion.toFrameSignal
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MlKitSmileSignalAnalyzerTest {

    @Test
    fun `toFrameSignal returns faceCount 0 and null metrics when face list is empty`() {
        val signal = emptyList<Face>().toFrameSignal(timestampElapsedMs = 1500L, rotatedFrameWidth = 480)

        assertEquals(1500L, signal.timestampElapsedMs)
        assertEquals(0, signal.faceCount)
        assertNull(signal.faceWidthRatio)
        assertNull(signal.smileProbability)
        assertNull(signal.leftEyeOpenProbability)
        assertNull(signal.rightEyeOpenProbability)
        assertNull(signal.trackingId)
    }

    @Test
    fun `toFrameSignal returns faceCount greater than 1 and null metrics when multiple faces detected`() {
        val f1 = mockk<Face>(relaxed = true)
        val f2 = mockk<Face>(relaxed = true)
        val signal = listOf(f1, f2).toFrameSignal(timestampElapsedMs = 2000L, rotatedFrameWidth = 480)

        assertEquals(2, signal.faceCount)
        assertNull(signal.faceWidthRatio)
        assertNull(signal.smileProbability)
    }

    @Test
    fun `toFrameSignal extracts single face width ratio, euler angles, probabilities, and guards zero frame width`() {
        val rect = mockk<Rect>()
        every { rect.width() } returns 240

        val face = mockk<Face>()
        every { face.boundingBox } returns rect
        every { face.headEulerAngleY } returns -4.5f
        every { face.headEulerAngleZ } returns 2.0f
        every { face.smilingProbability } returns 0.82f
        every { face.leftEyeOpenProbability } returns 0.95f
        every { face.rightEyeOpenProbability } returns 0.91f
        every { face.trackingId } returns 42

        val signal = listOf(face).toFrameSignal(timestampElapsedMs = 3000L, rotatedFrameWidth = 480)

        assertEquals(1, signal.faceCount)
        assertEquals(0.50f, signal.faceWidthRatio ?: 0f, 0.001f)
        assertEquals(-4.5f, signal.yawDeg ?: 0f, 0.001f)
        assertEquals(2.0f, signal.rollDeg ?: 0f, 0.001f)
        assertEquals(0.82f, signal.smileProbability ?: 0f, 0.001f)
        assertEquals(0.95f, signal.leftEyeOpenProbability ?: 0f, 0.001f)
        assertEquals(0.91f, signal.rightEyeOpenProbability ?: 0f, 0.001f)
        assertEquals(42, signal.trackingId)

        // Zero rotatedFrameWidth safety guard clamps ratio to 1.0f without division by zero
        val zeroWidthSignal = listOf(face).toFrameSignal(timestampElapsedMs = 3100L, rotatedFrameWidth = 0)
        assertEquals(1.0f, zeroWidthSignal.faceWidthRatio ?: 0f, 0.001f)
    }
}
