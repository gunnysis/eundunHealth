package com.gunnys.eundunhealth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import com.gunnys.eundunhealth.data.preferences.ThemeMode
import com.gunnys.eundunhealth.data.preferences.ThemePreferences
import com.gunnys.eundunhealth.ui.navigation.AppNavigation
import com.gunnys.eundunhealth.ui.theme.EundunHealthTheme
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.handleDeeplinks
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var themePreferences: ThemePreferences

    @Inject lateinit var supabaseClient: SupabaseClient

    private val authViewModel: com.gunnys.eundunhealth.ui.auth.AuthViewModel by viewModels()

    private var consumedDeepLinkUri: Uri? = null

    private val healthPermissions = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
    )

    private val permissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* Health Connect uses its own permission contract */ }

    private val healthPermissionLauncher = registerForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract(),
    ) { /* permissions granted or denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleAuthDeepLink(intent)
        setContent {
            val themeMode by themePreferences.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            EundunHealthTheme(themeMode = themeMode) {
                AppNavigation(
                    onRequestHealthPermissions = {
                        healthPermissionLauncher.launch(healthPermissions)
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthDeepLink(intent)
    }

    private fun handleAuthDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri == consumedDeepLinkUri) return
        consumedDeepLinkUri = uri

        // 이미 Authenticated 상태면 옛 메일 링크 클릭 무시 (재진입 안전)
        if (authViewModel.sessionState.value is com.gunnys.eundunhealth.ui.auth.SessionState.Authenticated) return

        // supabase-kt 가 PKCE 분기 + code 추출 + exchange + 에러 URL 파라미터 처리까지 담당
        supabaseClient.handleDeeplinks(
            intent = intent,
            onSessionSuccess = { session ->
                val userId = session.user?.id
                if (userId != null) {
                    authViewModel.onDeepLinkSuccess(userId)
                } else {
                    authViewModel.onDeepLinkError(IllegalStateException("session.user is null"))
                }
            },
            onError = { authViewModel.onDeepLinkError(it) },
        )
    }
}
