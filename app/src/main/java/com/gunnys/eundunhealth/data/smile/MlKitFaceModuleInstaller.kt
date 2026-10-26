package com.gunnys.eundunhealth.data.smile

import android.content.Context
import androidx.compose.runtime.Immutable
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallClient
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
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
        var detector: FaceDetector? = null
        return try {
            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build()
            val activeDetector = FaceDetection.getClient(options)
            detector = activeDetector
            val moduleInstallClient = ModuleInstall.getClient(context)

            val alreadyAvailable = checkAvailability(moduleInstallClient, activeDetector)
            if (alreadyAvailable) {
                MlKitModuleState.Available
            } else {
                onProgress(MlKitModuleState.Installing(progressPercent = null))
                requestInstall(moduleInstallClient, activeDetector, onProgress)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Sentry.addBreadcrumb(
                Breadcrumb().apply {
                    category = "smile.mlkit_init_failed"
                    level = SentryLevel.ERROR
                    message = t.message ?: t::class.java.simpleName
                },
            )
            Sentry.captureException(t)
            MlKitModuleState.Unavailable(
                "얼굴 인식 모듈을 준비하지 못했습니다. 네트워크와 Google Play 서비스를 확인한 뒤 다시 시도해주세요.",
            )
        } finally {
            runCatching { detector?.close() }
        }
    }

    private suspend fun checkAvailability(
        client: ModuleInstallClient,
        detector: FaceDetector,
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
        client: ModuleInstallClient,
        detector: FaceDetector,
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
            .addOnFailureListener {
                client.unregisterListener(listener)
                if (cont.isActive) {
                    cont.resume(
                        MlKitModuleState.Unavailable(
                            "얼굴 인식 모듈 설치 요청에 실패했습니다. 네트워크와 Google Play 서비스를 확인해주세요.",
                        ),
                    )
                }
            }

        cont.invokeOnCancellation {
            runCatching { client.unregisterListener(listener) }
        }
    }
}
