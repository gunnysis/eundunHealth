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

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val userRepo: UserRepository,
) : ViewModel() {

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Unknown)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _signupState = MutableStateFlow<SignupState>(SignupState.Form)
    val signupState: StateFlow<SignupState> = _signupState.asStateFlow()

    private val _authOpState = MutableStateFlow<AuthOpState>(AuthOpState.Idle)
    val authOpState: StateFlow<AuthOpState> = _authOpState.asStateFlow()

    private val _pendingEmail = MutableStateFlow<String?>(null)
    val pendingEmail: StateFlow<String?> = _pendingEmail.asStateFlow()

    fun setPendingEmail(email: String) {
        _pendingEmail.value = email
    }

    fun clearPendingEmail() {
        _pendingEmail.value = null
    }

    fun resetSignupState() {
        _signupState.value = SignupState.Form
    }

    init {
        checkSession()
    }

    private fun checkSession() = viewModelScope.launch {
        runCatching {
            val userId = authRepo.restoreSession()
            if (userId != null) {
                val hasProfile = userRepo.getProfile().getOrNull() != null
                SessionState.Authenticated(userId, needsOnboarding = !hasProfile)
            } else {
                SessionState.Unauthenticated
            }
        }
            .onSuccess { session ->
                _sessionState.value = session
            }
            .onFailure {
                // 세션 복원 실패는 단순히 비로그인 상태로 처리 — Sentry/에러 표시 없음
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
        _sessionState.value = SessionState.Unauthenticated
    }

    private val _passwordResetSent = MutableStateFlow(false)
    val passwordResetSent: StateFlow<Boolean> = _passwordResetSent.asStateFlow()

    fun consumePasswordResetSent() {
        _passwordResetSent.value = false
    }

    fun resetPassword(email: String) = viewModelScope.launch {
        _authOpState.value = AuthOpState.Loading
        authRepo.resetPassword(email)
            .onSuccess {
                _passwordResetSent.value = true
                _authOpState.value = AuthOpState.Idle
            }
            .onFailure { e ->
                val appErr = (e as? com.gunnys.eundunhealth.data.auth.AppErrorException)?.appError
                    ?: e.toAppError().also { it.reportToSentry() }
                _authOpState.value = AuthOpState.Failed(appErr)
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

    /**
     * 인증 작업 에러를 소비. EmailNotConfirmed는 inline UI가 직접 다루므로 [LoginScreen]은
     * 일반 에러 스낵바를 띄운 직후에만 호출한다.
     */
    fun consumeAuthOpError() {
        if (_authOpState.value is AuthOpState.Failed) {
            _authOpState.value = AuthOpState.Idle
        }
    }

    /**
     * Deep link (메일 인증 링크) 가 MainActivity 의 supabaseClient.handleDeeplinks
     * 콜백을 통해 세션 import 성공을 보고할 때 호출. userId 를 받아 프로필 조회 후
     * sessionState 를 Authenticated 로 전환한다.
     */
    fun onDeepLinkSuccess(userId: String) = viewModelScope.launch {
        val hasProfile = userRepo.getProfile().getOrNull() != null
        _sessionState.value = SessionState.Authenticated(userId, needsOnboarding = !hasProfile)
        _signupState.value = SignupState.Form
        _authOpState.value = AuthOpState.Idle
    }

    /**
     * Deep link 처리 중 supabase-kt 가 보고한 에러를 받아 한국어 메시지로 변환 후
     * authOpState 에 보존. sessionState 는 Unauthenticated 로 명시 전환하여
     * AppNavigation 이 Login 화면으로 이동하도록 한다.
     */
    fun onDeepLinkError(e: Throwable) {
        val appErr = (e as? com.gunnys.eundunhealth.data.auth.AppErrorException)?.appError
            ?: e.toAppError().also { it.reportToSentry() }
        _authOpState.value = AuthOpState.Failed(appErr)
        _sessionState.value = SessionState.Unauthenticated
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
