package com.gunnys.eundunhealth.ui.auth

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.model.reportToSentry
import com.gunnys.eundunhealth.domain.model.toAppError
import com.gunnys.eundunhealth.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
sealed class ForgotPasswordUiState {
    data object Idle : ForgotPasswordUiState()
    data object Loading : ForgotPasswordUiState()
    data class Failed(val error: AppError) : ForgotPasswordUiState()
}

@Immutable
sealed class ForgotPasswordSideEffect {
    data object ResetSent : ForgotPasswordSideEffect()
}

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val authRepo: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ForgotPasswordUiState>(ForgotPasswordUiState.Idle)
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    private val _sideEffect = Channel<ForgotPasswordSideEffect>(Channel.BUFFERED)
    val sideEffect = _sideEffect.receiveAsFlow()

    fun consumeError() {
        if (_uiState.value is ForgotPasswordUiState.Failed) {
            _uiState.value = ForgotPasswordUiState.Idle
        }
    }

    fun resetPassword(email: String) = viewModelScope.launch {
        _uiState.value = ForgotPasswordUiState.Loading
        authRepo.resetPassword(email)
            .onSuccess {
                _uiState.value = ForgotPasswordUiState.Idle
                _sideEffect.send(ForgotPasswordSideEffect.ResetSent)
            }
            .onFailure { e ->
                val appErr = (e as? com.gunnys.eundunhealth.data.auth.AppErrorException)?.appError
                    ?: e.toAppError().also { it.reportToSentry() }
                _uiState.value = ForgotPasswordUiState.Failed(appErr)
            }
    }
}
