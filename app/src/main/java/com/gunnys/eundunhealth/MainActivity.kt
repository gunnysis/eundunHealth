package com.gunnys.eundunhealth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
            val themeMode by themePreferences.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
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

    // INC: configuration change / process death 후에도 동일 deep link intent 가 재발사되면
    // 이미 소비된 PKCE code 로 exchange 를 시도하여 실패한다. consumedDeepLinkUri 를
    // savedInstanceState 에 영속화하여 재처리 차단.
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        consumedDeepLinkUri?.let { outState.putString(KEY_CONSUMED_DEEP_LINK_URI, it.toString()) }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        savedInstanceState.getString(KEY_CONSUMED_DEEP_LINK_URI)?.let {
            consumedDeepLinkUri = Uri.parse(it)
        }
    }

    private fun handleAuthDeepLink(intent: Intent?) {
        val uri = intent?.data
            ?: return
        if (uri == consumedDeepLinkUri) return
        consumedDeepLinkUri = uri

        // 이미 Authenticated 상태면 옛 메일 링크 클릭 무시 (재진입 안전)
        if (authViewModel.sessionState.value is com.gunnys.eundunhealth.ui.auth.SessionState.Authenticated) return

        // Critical fix: supabase-kt 3.6.0 `handleDeeplinks` 는 PKCE 분기 진입 직후
        // `auth.handledUrlParameterError { data.getQueryParameter(it) }` 를 호출하며,
        // ?error_code=... / ?error=... 가 있으면 라이브러리 내부에서 처리하고 두 콜백
        // (onSessionSuccess / onError) 모두 호출하지 않는다. 결과적으로 만료 링크 클릭이
        // silent 가 되어 사용자가 아무 피드백도 받지 못함. 사전 검사로 명시 라우팅.
        val errorCode = uri.getQueryParameter("error_code") ?: uri.getQueryParameter("error")
        if (errorCode != null) {
            val message = uri.getQueryParameter("error_description") ?: errorCode
            authViewModel.onDeepLinkError(
                com.gunnys.eundunhealth.data.auth.AppErrorException(
                    com.gunnys.eundunhealth.domain.model.AppError.Auth(
                        when {
                            errorCode.contains("otp_expired") || errorCode.contains("expired") ->
                                "인증 링크가 만료되었습니다. 다시 가입해주세요"
                            errorCode.contains("access_denied") ->
                                "인증이 거부되었습니다. 다시 시도해주세요"
                            else -> "인증에 실패했습니다 ($message)"
                        },
                    ),
                ),
            )
            return
        }

        // cold start race 회피: checkSession 의 Unauthenticated 가 onDeepLinkSuccess 의
        // Authenticated 보다 먼저 fire 하여 Login 화면이 잠깐 보이는 문제를 막기 위해
        // sessionState 를 Unknown 으로 명시 전환 (AppNavigation 의 Unknown 분기는 Splash 유지).
        authViewModel.beginDeepLinkProcessing()

        // supabase-kt 가 PKCE 분기 + code 추출 + exchange 까지 담당 (에러 URL 파라미터는 위에서 사전 처리)
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

    companion object {
        private const val KEY_CONSUMED_DEEP_LINK_URI = "consumed_deep_link_uri"
    }
}
