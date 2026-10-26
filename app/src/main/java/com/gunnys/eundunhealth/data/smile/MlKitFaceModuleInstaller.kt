package com.gunnys.eundunhealth.data.smile

import android.content.Context
import androidx.compose.runtime.Immutable
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Immutable
sealed interface MlKitModuleState {
    data object Available : MlKitModuleState

    data class Installing(val progressPercent: Int? = null) : MlKitModuleState

    data class Unavailable(val reason: String) : MlKitModuleState
}

interface MlKitFaceModuleManager {
    suspend fun ensureModuleAvailable(onProgress: (MlKitModuleState.Installing) -> Unit = {}): MlKitModuleState
}

@Singleton
class MlKitFaceModuleInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
) : MlKitFaceModuleManager {

    @Suppress("TooGenericExceptionCaught")
    override suspend fun ensureModuleAvailable(
        onProgress: (MlKitModuleState.Installing) -> Unit,
    ): MlKitModuleState {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
        val detector = FaceDetection.getClient(options)
        val moduleInstallClient = ModuleInstall.getClient(context)

        return try {
            val alreadyAvailable = checkAvailability(moduleInstallClient, detector)
            if (alreadyAvailable) {
                detector.close()
                return MlKitModuleState.Available
            }
            onProgress(MlKitModuleState.Installing(progressPercent = null))
            val result = requestInstall(moduleInstallClient, detector, onProgress)
            detector.close()
            result
        } catch (ce: CancellationException) {
            runCatching { detector.close() }
            throw ce
        } catch (e: Exception) {
            runCatching { detector.close() }
            MlKitModuleState.Unavailable(
                e.localizedMessage ?: "얼굴 인식 모듈을 준비하지 못했습니다. 네트워크와 Google Play 서비스를 확인해주세요.",
            )
        }
    }

    private suspend fun checkAvailability(
        client: com.google.android.gms.common.moduleinstall.ModuleInstallClient,
        detector: com.google.mlkit.vision.face.FaceDetector,
    ): Boolean = suspendCancellableCoroutine { cont ->
        client.areModulesAvailable(detector)
            .addOnSuccessListener { response ->
                if (cont.isActive) cont.resume(response.areModulesAvailable())
            }
            .addOnFailureListener {
                if (cont.isActive) cont.resume(false)
            }
    }

    private suspend fun requestInstall(
        client: com.google.android.gms.common.moduleinstall.ModuleInstallClient,
        detector: com.google.mlkit.vision.face.FaceDetector,
        onProgress: (MlKitModuleState.Installing) -> Unit,
    ): MlKitModuleState = suspendCancellableCoroutine { cont ->
        val listener = object : com.google.android.gms.common.moduleinstall.InstallStatusListener {
            override fun onInstallStatusUpdated(update: ModuleInstallStatusUpdate) {
                val progressInfo = update.progressInfo
                if (progressInfo != null && progressInfo.totalBytesToDownload > 0) {
                    val pct = ((progressInfo.bytesDownloaded * 100L) / progressInfo.totalBytesToDownload).toInt()
                    onProgress(MlKitModuleState.Installing(pct.coerceIn(0, 100)))
                }
                when (update.installState) {
                    ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED -> {
                        client.unregisterListener(this)
                        if (cont.isActive) cont.resume(MlKitModuleState.Available)
                    }
                    ModuleInstallStatusUpdate.InstallState.STATE_FAILED,
                    ModuleInstallStatusUpdate.InstallState.STATE_CANCELED,
                    -> {
                        client.unregisterListener(this)
                        if (cont.isActive) {
                            cont.resume(
                                MlKitModuleState.Unavailable(
                                    "온디바이스 얼굴 인식 모듈 다운로드가 완료되지 않았습니다. 잠시 후 다시 시도해주세요.",
                                ),
                            )
                        }
                    }
                    else -> Unit
                }
            }
        }

        val request = ModuleInstallRequest.newBuilder()
            .addApi(detector)
            .setListener(listener)
            .build()

        client.installModules(request)
            .addOnSuccessListener { response ->
                if (response.areModulesAlreadyInstalled()) {
                    client.unregisterListener(listener)
                    if (cont.isActive) cont.resume(MlKitModuleState.Available)
                }
            }
            .addOnFailureListener { err ->
                client.unregisterListener(listener)
                if (cont.isActive) {
                    cont.resume(
                        MlKitModuleState.Unavailable(
                            err.localizedMessage ?: "얼굴 인식 모듈 설치 요청에 실패했습니다.",
                        ),
                    )
                }
            }

        cont.invokeOnCancellation {
            runCatching { client.unregisterListener(listener) }
        }
    }
}
