package com.gunnys.eundunhealth.ui.auth

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.repository.AuthRepository
import com.gunnys.eundunhealth.domain.repository.SignupResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
sealed class SignupUiState {
    data object Form : SignupUiState()
    data object Loading : SignupUiState()
    data class AwaitingEmailConfirmation(val email: String) : SignupUiState()
    data class Failed(val error: AppError) : SignupUiState()
}

@Immutable
sealed class SignupSideEffect {
    data class AutoSignedIn(val userId: String) : SignupSideEffect()
}

@HiltViewModel
class SignupViewModel @Inject constructor(
    private val authRepo: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SignupUiState>(SignupUiState.Form)
    val uiState: StateFlow<SignupUiState> = _uiState.asStateFlow()

    private val _sideEffect = Channel<SignupSideEffect>(Channel.BUFFERED)
    val sideEffect = _sideEffect.receiveAsFlow()

    private val resend = ResendConfirmationController(authRepo, viewModelScope)
    val resendCooldownSec: StateFlow<Int> get() = resend.cooldownSec
    val resendError: StateFlow<AppError?> get() = resend.error

    fun clearSignupError() {
        if (_uiState.value is SignupUiState.Failed) {
            _uiState.value = SignupUiState.Form
        }
    }

    fun resetSignupState() {
        _uiState.value = SignupUiState.Form
    }

    fun clearResendError() = resend.clearError()

    fun signup(email: String, password: String) = viewModelScope.launch {
        _uiState.value = SignupUiState.Loading
        authRepo.signUp(email, password)
            .onSuccess { result ->
                when (result) {
                    is SignupResult.AutoSignedIn -> {
                        _uiState.value = SignupUiState.Form
                        _sideEffect.send(SignupSideEffect.AutoSignedIn(result.userId))
                    }
                    is SignupResult.AwaitingConfirmation -> {
                        _uiState.value = SignupUiState.AwaitingEmailConfirmation(result.email)
                    }
                }
            }
            .onFailure { e ->
                val appErr = e.toAppErrorReporting()
                _uiState.value = SignupUiState.Failed(appErr)
            }
    }

    fun resendConfirmation(email: String) = resend.resend(email)
}
