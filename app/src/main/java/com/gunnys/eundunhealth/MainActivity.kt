package com.gunnys.eundunhealth

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gunnys.eundunhealth.data.healthconnect.HealthConnectDataSource
import com.gunnys.eundunhealth.data.preferences.ThemeMode
import com.gunnys.eundunhealth.data.preferences.ThemePreferences
import com.gunnys.eundunhealth.ui.navigation.AppNavigation
import com.gunnys.eundunhealth.ui.theme.EundunHealthTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 인증 딥링크 처리가 이 클래스에서 사라진 이유:
 *
 * Supabase 시절에는 확인 메일의 링크가 App Links 로 앱에 돌아왔기 때문에 여기서 intent 를 받아
 * PKCE code 를 교환하고, 재처리 방지를 위해 소비한 URI 를 `savedInstanceState` 에 영속화해야
 * 했다. Entra 의 표준 흐름은 검증 코드를 **브라우저 안에서** 입력받고(설계 F11), 인증 결과는
 * MSAL 이 자체 `BrowserTabActivity` 로 받는다. 따라서 앱이 다룰 인증 intent 자체가 없다.
 *
 * 재진입 안전성도 MSAL 계정 캐시가 대신한다 — 복귀 시 무음 갱신이 먼저 성공하면 그대로 진입한다.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var themePreferences: ThemePreferences

    private var healthPermissionsRevision by mutableIntStateOf(0)
    private var lastPermissionLaunchRealtimeMs: Long = 0L

    private val healthPermissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { grantedPermissions ->
        val elapsedMs = SystemClock.elapsedRealtime() - lastPermissionLaunchRealtimeMs
        // 허용·거부 어느 결과든 실제 grant set을 다시 읽어 UI를 갱신한다.
        healthPermissionsRevision++

        // Android / Health Connect 2회 거부(또는 취소) 잠금 시 시스템이 권한 다이얼로그를 띄우지 않고
        // 즉시 빈 결과를 반환하여 사용자에게 '연동 버튼 무반응'으로 보이는 현상을 방지한다.
        // 다이얼로그가 표시되지 않고 즉시 종료되었거나(<350ms), Android 14+ 에서 영구 잠금
        // (결과 후에도 shouldShowRequestPermissionRationale == false) 상태라면
        // 공식 가이드에 따라 Health Connect 데이터/권한 관리 화면으로 안내한다.
        if (!grantedPermissions.containsAll(HealthConnectDataSource.PERMISSIONS)) {
            val isSilentlySuppressed = elapsedMs in 0L..SILENT_DISMISS_THRESHOLD_MS ||
                isHealthPermissionLockedOutOnApi34()
            if (isSilentlySuppressed) {
                openHealthConnectManageData()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by themePreferences.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val dynamicColor by themePreferences.dynamicColorEnabled.collectAsStateWithLifecycle(initialValue = false)
            EundunHealthTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColor,
            ) {
                AppNavigation(
                    healthPermissionsRevision = healthPermissionsRevision,
                    onRequestHealthPermissions = ::requestHealthPermissions,
                    onOpenHealthConnectManagement = ::openHealthConnectManageData,
                )
            }
        }
    }

    private fun requestHealthPermissions() {
        lastPermissionLaunchRealtimeMs = SystemClock.elapsedRealtime()
        runCatching {
            healthPermissionLauncher.launch(HealthConnectDataSource.PERMISSIONS)
        }.onFailure {
            openHealthConnectManageData()
        }
    }

    private fun isHealthPermissionLockedOutOnApi34(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        return HealthConnectDataSource.PERMISSIONS.any { permission ->
            !ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
        }
    }

    private fun openHealthConnectManageData() {
        runCatching {
            startActivity(HealthConnectClient.getHealthConnectManageDataIntent(this))
        }.onFailure {
            runCatching {
                startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
            }
        }
    }

    companion object {
        // 사용자 눈으로 다이얼로그 진입 애니메이션을 보고 거부 버튼을 누르기까지 물리적으로 불가능한 시간 임계값.
        // 시스템 PermissionController 가 UI 없이 즉시 finish() 한 경우를 판별한다.
        private const val SILENT_DISMISS_THRESHOLD_MS = 350L
    }
}
