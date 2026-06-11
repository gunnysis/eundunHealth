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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.ui.components.AuthErrorBanner

@Composable
fun SignupScreen(
    onNavigateToLogin: () -> Unit,
    authViewModel: AuthViewModel,
    signupViewModel: SignupViewModel = hiltViewModel(),
) {
    val uiState by signupViewModel.uiState.collectAsStateWithLifecycle()
    val resendCooldownSec by signupViewModel.resendCooldownSec.collectAsStateWithLifecycle()
    val resendError by signupViewModel.resendError.collectAsStateWithLifecycle()

    // AutoSignedIn SideEffect → AuthViewModel 세션 전환
    LaunchedEffect(Unit) {
        signupViewModel.sideEffect.collect { effect ->
            when (effect) {
                is SignupSideEffect.AutoSignedIn ->
                    authViewModel.onAuthSuccess(effect.userId, needsOnboarding = true)
            }
        }
    }

    Scaffold { padding ->
        when (val state = uiState) {
            is SignupUiState.AwaitingEmailConfirmation -> AwaitingConfirmationCard(
                email = state.email,
                cooldownSec = resendCooldownSec,
                resendError = resendError,
                onResend = {
                    signupViewModel.clearResendError()
                    signupViewModel.resendConfirmation(state.email)
                },
                onGoToLogin = {
                    authViewModel.setPendingEmail(state.email)
                    signupViewModel.resetSignupState()
                    onNavigateToLogin()
                },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            else -> SignupForm(
                isLoading = uiState is SignupUiState.Loading,
                error = (uiState as? SignupUiState.Failed)?.error,
                onClearError = signupViewModel::clearSignupError,
                onSubmit = { email, password ->
                    signupViewModel.signup(email.trim(), password)
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
