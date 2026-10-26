package com.gunnys.eundunhealth.data.smile

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.gunnys.eundunhealth.domain.smile.FrameSignal
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraX `ImageAnalysis.Analyzer` 구현체.
 *
 * - 온디바이스 ML Kit Face Detection 으로 `smilingProbability`, `left/rightEyeOpenProbability`,
 *   `headEulerAngleY/Z`, 얼굴 너비 비율만 추출하여 [FrameSignal] 로 변환한다.
 * - 원본 비트맵이나 이미지를 디스크·네트워크·메모리 버퍼에 일절 저장하지 않는다.
 * - `STRATEGY_KEEP_ONLY_LATEST` 파이프라인이 멈추지 않도록 모든 성공/실패/예외 경로에서
 *   반드시 `imageProxy.close()` 를 호출한다.
 */
class MlKitSmileSignalAnalyzer(
    private val onSignal: (FrameSignal) -> Unit,
    detectorOverride: FaceDetector? = null,
) : ImageAnalysis.Analyzer,
    Closeable {

    private val isClosed = AtomicBoolean(false)

    private val detector: FaceDetector = detectorOverride ?: FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setMinFaceSize(MIN_FACE_SIZE)
            .enableTracking()
            .build(),
    )

    @Suppress("TooGenericExceptionCaught")
    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (isClosed.get()) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val frameWidth = if (rotationDegrees == 90 || rotationDegrees == 270) {
            imageProxy.height
        } else {
            imageProxy.width
        }

        val inputImage = try {
            InputImage.fromMediaImage(mediaImage, rotationDegrees)
        } catch (_: Exception) {
            imageProxy.close()
            return
        }

        val timestampMs = SystemClock.elapsedRealtime()
        try {
            detector.process(inputImage)
                .addOnSuccessListener { faces ->
                    if (!isClosed.get()) {
                        onSignal(faces.toFrameSignal(timestampMs, frameWidth))
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } catch (_: Throwable) {
            imageProxy.close()
        }
    }

    override fun close() {
        if (isClosed.compareAndSet(false, true)) {
            runCatching { detector.close() }
        }
    }

    companion object {
        private const val MIN_FACE_SIZE = 0.25f

        internal fun List<Face>.toFrameSignal(
            timestampElapsedMs: Long,
            rotatedFrameWidth: Int,
        ): FrameSignal {
            if (isEmpty()) {
                return FrameSignal(
                    timestampElapsedMs = timestampElapsedMs,
                    faceCount = 0,
                    faceWidthRatio = null,
                    yawDeg = null,
                    rollDeg = null,
                    smileProbability = null,
                    leftEyeOpenProbability = null,
                    rightEyeOpenProbability = null,
                    trackingId = null,
                )
            }
            if (size > 1) {
                return FrameSignal(
                    timestampElapsedMs = timestampElapsedMs,
                    faceCount = size,
                    faceWidthRatio = null,
                    yawDeg = null,
                    rollDeg = null,
                    smileProbability = null,
                    leftEyeOpenProbability = null,
                    rightEyeOpenProbability = null,
                    trackingId = null,
                )
            }

            val face = first()
            val safeWidth = rotatedFrameWidth.coerceAtLeast(1)
            val widthRatio = (face.boundingBox.width().toFloat() / safeWidth.toFloat()).coerceIn(0f, 1f)

            return FrameSignal(
                timestampElapsedMs = timestampElapsedMs,
                faceCount = 1,
                faceWidthRatio = widthRatio,
                yawDeg = face.headEulerAngleY,
                rollDeg = face.headEulerAngleZ,
                smileProbability = face.smilingProbability,
                leftEyeOpenProbability = face.leftEyeOpenProbability,
                rightEyeOpenProbability = face.rightEyeOpenProbability,
                trackingId = face.trackingId,
            )
        }
    }
}
