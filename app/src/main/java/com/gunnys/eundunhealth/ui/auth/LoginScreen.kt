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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.gunnys.eundunhealth.domain.model.AppError

@Composable
fun LoginScreen(
    onNavigateToSignup: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    authViewModel: AuthViewModel,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val authOpState by authViewModel.authOpState.collectAsState()
    val pendingEmail by authViewModel.pendingEmail.collectAsState()
    val resendCooldownSec by authViewModel.resendCooldownSec.collectAsState()
    val resendError by authViewModel.resendError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val isLoading = authOpState is AuthOpState.Loading
    val lastError = (authOpState as? AuthOpState.Failed)?.error

    LaunchedEffect(Unit) {
        pendingEmail?.let {
            email = it
            authViewModel.clearPendingEmail()
        }
    }

    LaunchedEffect(lastError) {
        val e = lastError ?: return@LaunchedEffect
        if (e !is AppError.EmailNotConfirmed) {
            snackbarHostState.showSnackbar(e.userMessage)
        }
    }
    LaunchedEffect(resendError) {
        val e = resendError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(e.userMessage)
        authViewModel.clearResendError()
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
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
                    onClick = { authViewModel.resendConfirmation(notConfirmedEmail) },
                ) {
                    Text(
                        if (resendCooldownSec == 0) {
                            "인증 메일 다시 보내기"
                        } else {
                            "${resendCooldownSec}초 후 다시 보낼 수 있어요"
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { authViewModel.login(email.trim(), password) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
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
