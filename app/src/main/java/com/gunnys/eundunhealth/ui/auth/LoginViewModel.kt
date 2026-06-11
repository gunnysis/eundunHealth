package com.gunnys.eundunhealth.ui.auth

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.repository.AuthRepository
import com.gunnys.eundunhealth.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
sealed class LoginUiState {
    data object Idle : LoginUiState()
    data object Loading : LoginUiState()
    data class Failed(val error: AppError) : LoginUiState()
}

@Immutable
sealed class LoginSideEffect {
    data class LoginSuccess(val userId: String, val needsOnboarding: Boolean) : LoginSideEffect()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val userRepo: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _sideEffect = Channel<LoginSideEffect>(Channel.BUFFERED)
    val sideEffect = _sideEffect.receiveAsFlow()

    private val resend = ResendConfirmationController(authRepo, viewModelScope)
    val resendCooldownSec: StateFlow<Int> get() = resend.cooldownSec
    val resendError: StateFlow<AppError?> get() = resend.error

    fun setExternalError(error: AppError) {
        _uiState.value = LoginUiState.Failed(error)
    }

    fun consumeError() {
        if (_uiState.value is LoginUiState.Failed) {
            _uiState.value = LoginUiState.Idle
        }
    }

    fun clearResendError() = resend.clearError()

    fun login(email: String, password: String) = viewModelScope.launch {
        _uiState.value = LoginUiState.Loading
        authRepo.signIn(email, password)
            .onSuccess { userId ->
                val hasProfile = userRepo.getProfile().getOrNull() != null
                _uiState.value = LoginUiState.Idle
                _sideEffect.send(LoginSideEffect.LoginSuccess(userId, needsOnboarding = !hasProfile))
            }
            .onFailure { e ->
                val appErr = e.toAppErrorReporting()
                _uiState.value = LoginUiState.Failed(appErr)
            }
    }

    fun resendConfirmation(email: String) = resend.resend(email)
}
