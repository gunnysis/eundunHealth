package com.gunnys.eundunhealth.ui.auth

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.model.reportToSentry
import com.gunnys.eundunhealth.domain.model.toAppError
import com.gunnys.eundunhealth.domain.repository.AuthRepository
import com.gunnys.eundunhealth.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    private val userRepo: UserRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _error = MutableStateFlow<AppError?>(null)
    val error: StateFlow<AppError?> = _error.asStateFlow()

    fun clearError() { _error.value = null }

    init {
        checkSession()
    }

    private fun checkSession() = viewModelScope.launch {
        runCatching {
            val userId = authRepo.restoreSession()
            if (userId != null) {
                val hasProfile = userRepo.getProfile().getOrNull() != null
                AuthState.Authenticated(userId, needsOnboarding = !hasProfile)
            } else {
                AuthState.Unauthenticated
            }
        }
            .onSuccess { _authState.value = it }
            .onFailure {
                // 세션 복원 실패는 단순히 비로그인 상태로 처리 — Sentry/에러 표시 없음
                _authState.value = AuthState.Unauthenticated
            }
    }

    fun login(email: String, password: String) = viewModelScope.launch {
        _authState.value = AuthState.Loading
        authRepo.signIn(email, password)
            .onSuccess { userId ->
                val hasProfile = userRepo.getProfile().getOrNull() != null
                _authState.value = AuthState.Authenticated(userId, needsOnboarding = !hasProfile)
            }
            .onFailure {
                _authState.value = AuthState.Unauthenticated
                // signIn은 AuthRepositoryImpl에서 한국어 메시지로 매핑된 예외를 던지므로
                // userMessage를 보존하기 위해 message가 있으면 그대로 Auth 에러로 사용
                val appErr = it.message
                    ?.let { msg -> AppError.Auth(msg) }
                    ?: it.toAppError()
                appErr.reportToSentry()
                _error.value = appErr
            }
    }

    fun signup(email: String, password: String) = viewModelScope.launch {
        _authState.value = AuthState.Loading
        authRepo.signUp(email, password)
            .onSuccess { userId ->
                _authState.value = AuthState.Authenticated(userId, needsOnboarding = true)
            }
            .onFailure {
                _authState.value = AuthState.Unauthenticated
                val appErr = it.message
                    ?.let { msg -> AppError.Auth(msg) }
                    ?: it.toAppError()
                appErr.reportToSentry()
                _error.value = appErr
            }
    }

    fun logout() = viewModelScope.launch {
        authRepo.signOut()
        _authState.value = AuthState.Unauthenticated
    }

    private val _resetState = MutableStateFlow<ResetState>(ResetState.Idle)
    val resetState: StateFlow<ResetState> = _resetState.asStateFlow()

    fun clearResetState() { _resetState.value = ResetState.Idle }

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
}
