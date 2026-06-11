package com.gunnys.eundunhealth.ui.auth

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.repository.AuthRepository
import com.gunnys.eundunhealth.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
sealed class SessionState {
    data object Unknown : SessionState()
    data class Authenticated(val userId: String, val needsOnboarding: Boolean = false) : SessionState()
    data object Unauthenticated : SessionState()
}

/**
 * 앱 전역 세션 관리 + deep link 처리 전용 ViewModel.
 * 로그인/회원가입/비밀번호 재설정 로직은 각 화면별 ViewModel (LoginViewModel,
 * SignupViewModel, ForgotPasswordViewModel) 로 분리됨.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val userRepo: UserRepository,
) : ViewModel() {

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Unknown)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _deepLinkError = MutableStateFlow<AppError?>(null)
    val deepLinkError: StateFlow<AppError?> = _deepLinkError.asStateFlow()

    private val _pendingEmail = MutableStateFlow<String?>(null)
    val pendingEmail: StateFlow<String?> = _pendingEmail.asStateFlow()

    fun setPendingEmail(email: String) {
        _pendingEmail.value = email
    }

    fun clearPendingEmail() {
        _pendingEmail.value = null
    }

    fun consumeDeepLinkError() {
        _deepLinkError.value = null
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
                _sessionState.value = SessionState.Unauthenticated
            }
    }

    /**
     * 로그인/회원가입 성공 후 per-screen VM 의 SideEffect 를 받아 세션 전환.
     * Screen composable 이 mediator 로서 호출한다.
     */
    fun onAuthSuccess(userId: String, needsOnboarding: Boolean) {
        _sessionState.value = SessionState.Authenticated(userId, needsOnboarding)
        _pendingEmail.value = null
    }

    fun logout() = viewModelScope.launch {
        authRepo.signOut()
        _sessionState.value = SessionState.Unauthenticated
    }

    /**
     * Deep link 처리 시작 시 호출. cold start 시 [checkSession] 의 Unauthenticated 결과가
     * [onDeepLinkSuccess] 의 Authenticated 보다 먼저 fire 하여 사용자가 Login 화면을 잠깐
     * 보는 race 를 회피한다. AppNavigation 의 Unknown 분기는 Splash 유지.
     */
    fun beginDeepLinkProcessing() {
        _sessionState.value = SessionState.Unknown
    }

    fun onDeepLinkSuccess(userId: String) = viewModelScope.launch {
        val hasProfile = userRepo.getProfile().getOrNull() != null
        _sessionState.value = SessionState.Authenticated(userId, needsOnboarding = !hasProfile)
        _pendingEmail.value = null
    }

    fun onDeepLinkError(e: Throwable) {
        val appErr = e.toAppErrorReporting()
        _deepLinkError.value = appErr
        _sessionState.value = SessionState.Unauthenticated
    }
}
