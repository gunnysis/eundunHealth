package com.gunnys.eundunhealth.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gunnys.eundunhealth.domain.model.AppError
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

/**
 * Inline error banner for auth flows.
 *
 * v0.1.6 에서 SignupScreen 내 private 으로 시작. v0.1.7 에서 LoginScreen +
 * ForgotPasswordScreen 마이그레이션 시점에 promote — 룰 8 의
 * "두 번째 화면 마이그레이션 시점" 트리거 (CLAUDE.md 룰 8).
 *
 * 동작:
 * - 첫 composition 시 [Sentry] breadcrumb (`auth.error_banner_shown`, level INFO)
 * - `liveRegion = Polite` — TalkBack 사용자에게 즉시 음성 알림 (a11y)
 * - Material 3 `errorContainer` color scheme
 *
 * @param error 표시할 에러. [AppError.userMessage] 가 한국어로 노출됨.
 * @param screen Sentry breadcrumb 의 `screen` data — "signup" / "awaiting_confirmation" /
 *   "login" / "login_resend" / "forgot_password".
 */
@Composable
fun AuthErrorBanner(
    error: AppError,
    screen: String,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(error, screen) {
        Sentry.addBreadcrumb(
            Breadcrumb().apply {
                category = "auth.error_banner_shown"
                level = SentryLevel.INFO
                setData("error_type", error::class.simpleName ?: "Unknown")
                setData("screen", screen)
            },
        )
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = error.userMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}
