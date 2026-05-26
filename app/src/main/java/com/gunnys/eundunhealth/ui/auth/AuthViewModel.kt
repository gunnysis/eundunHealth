package com.gunnys.eundunhealth.ui.auth

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.model.reportToSentry
import com.gunnys.eundunhealth.domain.model.toAppError
import com.gunnys.eundunhealth.domain.repository.AuthRepository
import com.gunnys.eundunhealth.domain.repository.SignupResult
import com.gunnys.eundunhealth.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SessionState {
    @Immutable data object Unknown : SessionState()

    @Immutable
    data class Authenticated(val userId: String, val needsOnboarding: Boolean = false) : SessionState()

    @Immutable data object Unauthenticated : SessionState()
}

sealed class AuthOpState {
    @Immutable data object Idle : AuthOpState()

    @Immutable data object Loading : AuthOpState()

    @Immutable data class Failed(val error: AppError) : AuthOpState()
}

sealed class SignupState {
    @Immutable data object Form : SignupState()

    @Immutable data object Loading : SignupState()

    @Immutable data class AwaitingEmailConfirmation(val email: String) : SignupState()

    @Immutable data class Failed(val error: AppError) : SignupState()
}

sealed class AuthState {
    @Immutable
    data object Loading : AuthState()

    @Immutable
    data class Authenticated(val userId: String, val needsOnboarding: Boolean = false) : AuthState()

    @Immutable
    data object Unauthenticated : AuthState()
}

sealed class ResetState {
    @Immutable data object Idle : ResetState()

    @Immutable data object Loading : ResetState()

    @Immutable data object Success : ResetState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val userRepo: UserRepository,
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Unknown)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _signupState = MutableStateFlow<SignupState>(SignupState.Form)
    val signupState: StateFlow<SignupState> = _signupState.asStateFlow()

    private val _authOpState = MutableStateFlow<AuthOpState>(AuthOpState.Idle)
    val authOpState: StateFlow<AuthOpState> = _authOpState.asStateFlow()

    private val _error = MutableStateFlow<AppError?>(null)
    val error: StateFlow<AppError?> = _error.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    init {
        checkSession()
    }

    private fun checkSession() = viewModelScope.launch {
        runCatching {
            val userId = authRepo.restoreSession()
            if (userId != null) {
                val hasProfile = userRepo.getProfile().getOrNull() != null
                Pair(
                    AuthState.Authenticated(userId, needsOnboarding = !hasProfile),
                    SessionState.Authenticated(userId, needsOnboarding = !hasProfile),
                )
            } else {
                Pair(AuthState.Unauthenticated, SessionState.Unauthenticated)
            }
        }
            .onSuccess { (auth, session) ->
                _authState.value = auth
                _sessionState.value = session
            }
            .onFailure {
                // 세션 복원 실패는 단순히 비로그인 상태로 처리 — Sentry/에러 표시 없음
                _authState.value = AuthState.Unauthenticated
                _sessionState.value = SessionState.Unauthenticated
            }
    }

    fun login(email: String, password: String) = viewModelScope.launch {
        _authOpState.value = AuthOpState.Loading
        authRepo.signIn(email, password)
            .onSuccess { userId ->
                val hasProfile = userRepo.getProfile().getOrNull() != null
                _sessionState.value = SessionState.Authenticated(userId, needsOnboarding = !hasProfile)
                _authOpState.value = AuthOpState.Idle
            }
            .onFailure { e ->
                val appErr = (e as? com.gunnys.eundunhealth.data.auth.AppErrorException)?.appError
                    ?: e.toAppError().also { it.reportToSentry() }
                _authOpState.value = AuthOpState.Failed(appErr)
            }
    }

    fun signup(email: String, password: String) = viewModelScope.launch {
        _signupState.value = SignupState.Loading
        authRepo.signUp(email, password)
            .onSuccess { result ->
                when (result) {
                    is SignupResult.AutoSignedIn -> {
                        _sessionState.value =
                            SessionState.Authenticated(result.userId, needsOnboarding = true)
                        _signupState.value = SignupState.Form
                    }
                    is SignupResult.AwaitingConfirmation -> {
                        _signupState.value = SignupState.AwaitingEmailConfirmation(result.email)
                    }
                }
            }
            .onFailure { e ->
                val appErr = (e as? com.gunnys.eundunhealth.data.auth.AppErrorException)?.appError
                    ?: e.toAppError().also { it.reportToSentry() }
                _signupState.value = SignupState.Failed(appErr)
            }
    }

    fun logout() = viewModelScope.launch {
        authRepo.signOut()
        _authState.value = AuthState.Unauthenticated
    }

    private val _resetState = MutableStateFlow<ResetState>(ResetState.Idle)
    val resetState: StateFlow<ResetState> = _resetState.asStateFlow()

    fun clearResetState() {
        _resetState.value = ResetState.Idle
    }

    fun resetPassword(email: String) = viewModelScope.launch {
        _resetState.value = ResetState.Loading
        authRepo.resetPassword(email)
            .onSuccess { _resetState.value = ResetState.Success }
            .onFailure {
                _resetState.value = ResetState.Idle
                val appErr = it.message
                    ?.let { msg -> AppError.Auth(msg) }
                    ?: it.toAppError()
                appErr.reportToSentry()
                _error.value = appErr
            }
    }

    private val _resendCooldownSec = MutableStateFlow(0)
    val resendCooldownSec: StateFlow<Int> = _resendCooldownSec.asStateFlow()

    // 마지막으로 보낸 resend의 에러를 한 번만 노출하기 위한 transient state
    private val _resendError = MutableStateFlow<AppError?>(null)
    val resendError: StateFlow<AppError?> = _resendError.asStateFlow()

    fun clearResendError() {
        _resendError.value = null
    }

    fun resendConfirmation(email: String) = viewModelScope.launch {
        if (_resendCooldownSec.value > 0) return@launch
        authRepo.resendConfirmation(email)
            .onSuccess {
                _resendCooldownSec.value = 60
                // 카운트다운
                while (_resendCooldownSec.value > 0) {
                    delay(1_000)
                    _resendCooldownSec.value = (_resendCooldownSec.value - 1).coerceAtLeast(0)
                }
            }
            .onFailure { e ->
                val appErr = (e as? com.gunnys.eundunhealth.data.auth.AppErrorException)?.appError
                    ?: e.toAppError().also { it.reportToSentry() }
                _resendError.value = appErr
            }
    }
}
