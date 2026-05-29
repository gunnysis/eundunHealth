package com.gunnys.eundunhealth.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gunnys.eundunhealth.domain.model.AppError
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

@Composable
fun SignupScreen(
    onNavigateToLogin: () -> Unit,
    authViewModel: AuthViewModel,
) {
    val signupState by authViewModel.signupState.collectAsState()
    val resendCooldownSec by authViewModel.resendCooldownSec.collectAsState()
    val resendError by authViewModel.resendError.collectAsState()

    Scaffold { padding ->
        when (val state = signupState) {
            is SignupState.AwaitingEmailConfirmation -> AwaitingConfirmationCard(
                email = state.email,
                cooldownSec = resendCooldownSec,
                resendError = resendError,
                onResend = {
                    authViewModel.clearResendError()
                    authViewModel.resendConfirmation(state.email)
                },
                onGoToLogin = {
                    authViewModel.setPendingEmail(state.email)
                    authViewModel.resetSignupState()
                    onNavigateToLogin()
                },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            else -> SignupForm(
                isLoading = signupState is SignupState.Loading,
                error = (signupState as? SignupState.Failed)?.error,
                onClearError = authViewModel::clearSignupError,
                onSubmit = { email, password ->
                    authViewModel.signup(email.trim(), password)
                },
                onNavigateToLogin = onNavigateToLogin,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

@Composable
private fun SignupForm(
    isLoading: Boolean,
    error: AppError?,
    onClearError: () -> Unit,
    onSubmit: (email: String, password: String) -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }

    val formValid = email.isNotBlank() &&
        password.length >= 6 &&
        password == confirmPassword

    // D1: button enabled (= 모든 validation pass) 시점에 banner 자동 dismiss.
    // input 1글자 typo 수정 같은 미세 변경 시에는 dismiss 안 함 — 메시지 다시 보고 싶을 때 보존.
    LaunchedEffect(formValid, error) {
        if (formValid && error != null) {
            onClearError()
        }
    }

    Column(
        modifier = modifier
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("회원가입", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(32.dp))

        // D5 위치: headline 아래, email input 위. error 있을 때만 표시.
        error?.let {
            AuthErrorBanner(error = it, screen = "signup")
            Spacer(modifier = Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("이메일") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("비밀번호") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = password.isNotEmpty() && password.length < 6,
            supportingText = {
                if (password.isNotEmpty() && password.length < 6) {
                    Text("비밀번호는 6자 이상이어야 합니다")
                }
            },
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("비밀번호 확인") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = confirmPassword.isNotEmpty() && password != confirmPassword,
            supportingText = {
                if (confirmPassword.isNotEmpty() && password != confirmPassword) {
                    Text("비밀번호가 일치하지 않습니다")
                }
            },
        )
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { onSubmit(email, password) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && formValid,
        ) {
            AnimatedVisibility(visible = isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp).padding(end = 8.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Text("가입하기")
        }

        TextButton(onClick = onNavigateToLogin) {
            Text("이미 계정이 있으신가요? 로그인")
        }
    }
}

@Composable
private fun AwaitingConfirmationCard(
    email: String,
    cooldownSec: Int,
    resendError: AppError?,
    onResend: () -> Unit,
    onGoToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("메일을 보냈습니다", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        // D7: resendError 도 같은 Banner 재사용. headline 아래, 본문/버튼 위.
        resendError?.let {
            AuthErrorBanner(error = it, screen = "awaiting_confirmation")
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(
            "$email 로 인증 메일을 보냈습니다.\n메일함을 확인하고 인증을 완료해주세요.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedButton(
            onClick = onResend,
            enabled = cooldownSec == 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (cooldownSec == 0) "메일 다시 보내기" else "${cooldownSec}초 후 다시 보낼 수 있어요")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onGoToLogin, modifier = Modifier.fillMaxWidth()) {
            Text("로그인하러 가기")
        }
    }
}

/**
 * Inline error banner for auth flows (signup form / awaiting confirmation card).
 *
 * D5 YAGNI: SignupScreen.kt 안 private 으로 시작. LoginScreen 등 다른 화면이
 * 같은 패턴 마이그레이션 시점에 `ui/components/` 로 promote.
 *
 * 동작:
 * - 첫 composition 시 [Sentry] breadcrumb (`auth.error_banner_shown`, level INFO) — D10
 * - `liveRegion = Polite` — TalkBack 사용자에게 즉시 알림 (a11y)
 * - Material 3 `errorContainer` color scheme
 *
 * @param error 표시할 에러. [AppError.userMessage] 가 한국어로 노출됨.
 * @param screen Sentry breadcrumb 의 `screen` data — "signup" 또는 "awaiting_confirmation".
 */
@Composable
private fun AuthErrorBanner(
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
