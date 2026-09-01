package com.gunnys.eundunhealth.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gunnys.eundunhealth.ui.components.AuthErrorBanner
import com.gunnys.eundunhealth.ui.theme.EundunHealthTheme

/**
 * 인증 게이트 — 앱에 남은 유일한 인증 화면.
 *
 * 이메일/비밀번호 입력, 검증, 재발송, 비밀번호 재설정은 전부 Entra 호스팅 페이지로 넘어갔다.
 * 표준 user flow 는 로그인과 가입을 구분하지 않고 한 페이지 안에서 전환하므로,
 * **앱의 CTA 도 하나뿐이다**(설계 §5.2). 버튼을 둘로 두면 눌러도 같은 화면이 떠서 혼란스럽다.
 */
@Composable
fun AuthGateScreen(
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val uiState by authViewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()

    AuthGateContent(
        uiState = uiState,
        onAuthenticate = { activity?.let(authViewModel::authenticate) },
    )
}

@Composable
private fun AuthGateContent(
    uiState: AuthGateUiState,
    onAuthenticate: () -> Unit,
) {
    val isBusy = uiState is AuthGateUiState.Authenticating

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "은둔헬스",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "집에서 시작하는 주간 운동 계획",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(40.dp))

        // 룰 8 — inline + persistent. 사용자가 다시 시도할 때까지 유지된다.
        if (uiState is AuthGateUiState.Failed) {
            AuthErrorBanner(error = uiState.error, screen = "auth_gate")
            Spacer(modifier = Modifier.height(24.dp))
        }

        Button(
            onClick = onAuthenticate,
            enabled = !isBusy,
            modifier = Modifier
                .fillMaxWidth()
                // 예고 없는 컨텍스트 전환은 스크린리더 사용자에게 특히 혼란스럽다(설계 §5.7).
                .semantics {
                    contentDescription = "로그인 또는 회원가입. 누르면 브라우저에서 로그인 화면이 열립니다"
                },
        ) {
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("로그인 / 회원가입")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "계정이 없으시면 열린 화면에서 바로 가입하실 수 있습니다",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * MSAL 의 대화형 인증은 Custom Tab 을 띄울 호스트 `Activity` 를 요구한다.
 * Compose 의 `LocalContext` 는 `ContextWrapper` 일 수 있으므로 풀어서 찾는다.
 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Preview(showBackground = true)
@Composable
private fun AuthGateIdlePreview() {
    EundunHealthTheme {
        AuthGateContent(uiState = AuthGateUiState.Idle, onAuthenticate = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun AuthGateFailedPreview() {
    EundunHealthTheme {
        AuthGateContent(
            uiState = AuthGateUiState.Failed(
                com.gunnys.eundunhealth.domain.model.AppError.Network(),
            ),
            onAuthenticate = {},
        )
    }
}
