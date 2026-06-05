package com.gunnys.eundunhealth.ui.onboarding

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gunnys.eundunhealth.domain.model.UserProfile
import com.gunnys.eundunhealth.domain.model.reportToSentry
import com.gunnys.eundunhealth.domain.model.toAppError
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
data class OnboardingUiState(
    val isLoading: Boolean = false,
)

@Immutable
sealed class OnboardingSideEffect {
    data object NavigateToHome : OnboardingSideEffect()
    data class ShowSnackbar(val message: String) : OnboardingSideEffect()
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userRepo: UserRepository,
    private val authRepo: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _sideEffect = Channel<OnboardingSideEffect>(Channel.BUFFERED)
    val sideEffect = _sideEffect.receiveAsFlow()

    fun saveProfile(heightCm: Float, weightKg: Float, bodyFatPct: Float, muscleMassKg: Float) = viewModelScope.launch {
        _uiState.value = OnboardingUiState(isLoading = true)
        val userId = authRepo.getCurrentUserId()
        if (userId == null) {
            _sideEffect.send(OnboardingSideEffect.ShowSnackbar("로그인이 필요합니다"))
            _uiState.value = OnboardingUiState(isLoading = false)
            return@launch
        }
        runCatching {
            userRepo.saveProfile(
                UserProfile(userId, heightCm, weightKg, bodyFatPct, muscleMassKg),
            ).getOrThrow()
        }
            .onSuccess { _sideEffect.send(OnboardingSideEffect.NavigateToHome) }
            .onFailure {
                val appErr = it.toAppError()
                appErr.reportToSentry()
                _sideEffect.send(OnboardingSideEffect.ShowSnackbar(appErr.userMessage))
            }
        _uiState.value = OnboardingUiState(isLoading = false)
    }
}
