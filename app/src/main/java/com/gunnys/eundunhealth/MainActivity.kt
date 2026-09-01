package com.gunnys.eundunhealth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
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

    private val healthPermissionLauncher = registerForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract(),
    ) { /* permissions granted or denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by themePreferences.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            EundunHealthTheme(themeMode = themeMode) {
                AppNavigation(
                    onRequestHealthPermissions = {
                        healthPermissionLauncher.launch(HealthConnectDataSource.PERMISSIONS)
                    },
                )
            }
        }
    }
}
