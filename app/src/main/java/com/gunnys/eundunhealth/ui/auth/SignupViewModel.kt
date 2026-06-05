package com.gunnys.eundunhealth.ui.auth

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.model.reportToSentry
import com.gunnys.eundunhealth.domain.model.toAppError
import com.gunnys.eundunhealth.domain.repository.AuthRepository
import com.gunnys.eundunhealth.domain.repository.SignupResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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

    private val _resendCooldownSec = MutableStateFlow(0)
    val resendCooldownSec: StateFlow<Int> = _resendCooldownSec.asStateFlow()

    private val _resendError = MutableStateFlow<AppError?>(null)
    val resendError: StateFlow<AppError?> = _resendError.asStateFlow()

    fun clearSignupError() {
        if (_uiState.value is SignupUiState.Failed) {
            _uiState.value = SignupUiState.Form
        }
    }

    fun resetSignupState() {
        _uiState.value = SignupUiState.Form
    }

    fun clearResendError() {
        _resendError.value = null
    }

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
                val appErr = (e as? com.gunnys.eundunhealth.data.auth.AppErrorException)?.appError
                    ?: e.toAppError().also { it.reportToSentry() }
                _uiState.value = SignupUiState.Failed(appErr)
            }
    }

    fun resendConfirmation(email: String) = viewModelScope.launch {
        if (_resendCooldownSec.value > 0) return@launch
        authRepo.resendConfirmation(email)
            .onSuccess {
                _resendCooldownSec.value = 60
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
