package com.gunnys.eundunhealth.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.ui.components.AuthErrorBanner
import com.gunnys.eundunhealth.ui.util.ObserveAsEvents

@Composable
fun LoginScreen(
    onNavigateToSignup: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    authViewModel: AuthViewModel,
    loginViewModel: LoginViewModel = hiltViewModel(),
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val uiState by loginViewModel.uiState.collectAsStateWithLifecycle()
    val pendingEmail by authViewModel.pendingEmail.collectAsStateWithLifecycle()
    val deepLinkError by authViewModel.deepLinkError.collectAsStateWithLifecycle()
    val resendCooldownSec by loginViewModel.resendCooldownSec.collectAsStateWithLifecycle()
    val resendError by loginViewModel.resendError.collectAsStateWithLifecycle()
    val isLoading = uiState is LoginUiState.Loading
    val lastError = (uiState as? LoginUiState.Failed)?.error
    val formValid = email.isNotBlank() && password.isNotBlank()

    // Deep link 에러를 LoginVM 으로 import (최초 1회)
    LaunchedEffect(deepLinkError) {
        deepLinkError?.let {
            loginViewModel.setExternalError(it)
            authViewModel.consumeDeepLinkError()
        }
    }

    LaunchedEffect(Unit) {
        pendingEmail?.let {
            email = it
            authViewModel.clearPendingEmail()
        }
    }

    // LoginSuccess SideEffect → AuthViewModel 세션 전환
    ObserveAsEvents(loginViewModel.sideEffect) { effect ->
        when (effect) {
            is LoginSideEffect.LoginSuccess ->
                authViewModel.onAuthSuccess(effect.userId, effect.needsOnboarding)
        }
    }

    // D4: button enabled (formValid) 시점에 banner 자동 dismiss.
    // EmailNotConfirmed 는 inline 재전송 UI 가 sticky 유지하므로 dismiss 분기.
    LaunchedEffect(formValid, lastError) {
        val e = lastError
        if (formValid && e != null && e !is AppError.EmailNotConfirmed) {
            loginViewModel.consumeError()
        }
    }

    // D10: resendError 도 같은 dismiss 정책.
    LaunchedEffect(formValid, resendError) {
        if (formValid && resendError != null) {
            loginViewModel.clearResendError()
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Default.FitnessCenter,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "은둔헬스",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "혼자서도 효과적인 운동",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(48.dp))

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
            )

            // D3 / D4: 일반 AppError 의 Banner — password input 아래, 로그인 버튼 위.
            // EmailNotConfirmed 는 inline 재전송 UI (아래 분기) 가 처리하므로 제외.
            val errorForBanner = lastError
            if (errorForBanner != null && errorForBanner !is AppError.EmailNotConfirmed) {
                Spacer(modifier = Modifier.height(8.dp))
                AuthErrorBanner(error = errorForBanner, screen = "login")
            }

            val notConfirmedEmail = (lastError as? AppError.EmailNotConfirmed)?.email
            if (notConfirmedEmail != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    lastError.userMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(
                    enabled = resendCooldownSec == 0,
                    onClick = { loginViewModel.resendConfirmation(notConfirmedEmail) },
                ) {
                    Text(
                        if (resendCooldownSec == 0) {
                            "인증 메일 다시 보내기"
                        } else {
                            "${resendCooldownSec}초 후 다시 보낼 수 있어요"
                        },
                    )
                }
                // D10: resend 실패 시 같은 영역에 Banner 추가.
                resendError?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    AuthErrorBanner(error = it, screen = "login_resend")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { loginViewModel.login(email.trim(), password) },
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
                Text("로그인")
            }

            TextButton(onClick = onNavigateToForgotPassword) {
                Text("비밀번호를 잊으셨나요?")
            }

            TextButton(onClick = onNavigateToSignup) {
                Text("계정이 없으신가요? 회원가입")
            }
        }
    }
}
