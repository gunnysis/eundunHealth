package com.gunnys.eundunhealth.ui.update

import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo
import com.gunnys.eundunhealth.domain.model.runSuspendCatching
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 단말의 Google Play 스토어 인앱 업데이트(`com.google.android.play:app-update-ktx:2.1.0`) 감지 및 실행 추상화.
 *
 * 설계: `docs/plans/logs/android.md` (옛 페어 `2026-10-29-mandatory-app-update-design.md` §5.2 흡수)
 * - 백엔드 `/app-compatibility` 와 이중(OR) 결합되어, 백엔드 환경변수 갱신 없이 Android 단독 릴리스가
 *   이루어진 경우에도 Play 스토어가 새 버전을 감지한 즉시 강제 업데이트로 전환한다.
 * - 진행 중이던 강제 업데이트가 백그라운드 이탈로 중단된 경우(`DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS`)도
 *   함께 감지하여 업데이트 흐름을 재개한다.
 * - 로컬 디버그/사이드로드 APK 에서 발생하는 `InstallException`(`ERROR_APP_NOT_OWNED`)은 안전하게 `false` 로
 *   폴백하여 크래시 및 Sentry 노이즈를 유발하지 않는다.
 */
interface InAppUpdateChecker {
    suspend fun isImmediateUpdateAvailable(): Boolean

    suspend fun launchImmediateUpdateIfAvailable(
        launcher: ActivityResultLauncher<IntentSenderRequest>,
    ): Boolean
}

@Singleton
class PlayInAppUpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) : InAppUpdateChecker {

    // 룰 14 (콜드 스타트 생성자 무거운 초기화 금지): Binder/IPC 서비스 핸들은 실제 코루틴 호출 시점에 지연 생성한다.
    private val appUpdateManager by lazy { AppUpdateManagerFactory.create(context) }

    override suspend fun isImmediateUpdateAvailable(): Boolean = runSuspendCatching {
        val info = appUpdateManager.requestAppUpdateInfo()
        isImmediateFlowEligible(info)
    }.getOrDefault(false)

    override suspend fun launchImmediateUpdateIfAvailable(
        launcher: ActivityResultLauncher<IntentSenderRequest>,
    ): Boolean = runSuspendCatching {
        val info = appUpdateManager.requestAppUpdateInfo()
        if (!isImmediateFlowEligible(info)) {
            false
        } else {
            appUpdateManager.startUpdateFlowForResult(
                info,
                launcher,
                AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
            )
        }
    }.getOrDefault(false)
}

internal fun isImmediateFlowEligible(info: AppUpdateInfo): Boolean {
    val availability = info.updateAvailability()
    val isReadyOrInProgress =
        availability == UpdateAvailability.UPDATE_AVAILABLE ||
            availability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
    return isReadyOrInProgress && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
}
