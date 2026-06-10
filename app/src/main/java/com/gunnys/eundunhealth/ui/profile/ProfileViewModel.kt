package com.gunnys.eundunhealth.ui.profile

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gunnys.eundunhealth.domain.model.AppError
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
sealed class ProfileUiState {
    @Immutable data object Loading : ProfileUiState()

    @Immutable data class Loaded(
        val profile: UserProfile,
        val isSaving: Boolean = false,
        val isDeleting: Boolean = false,
    ) : ProfileUiState()

    @Immutable data object Empty : ProfileUiState()

    @Immutable data class Error(val error: AppError) : ProfileUiState()
}

@Immutable
sealed class ProfileSideEffect {
    @Immutable data class ShowSnackbar(val message: String) : ProfileSideEffect()

    @Immutable data object SavedAndNavigateBack : ProfileSideEffect()

    @Immutable data object NavigateToLogin : ProfileSideEffect()
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepo: UserRepository,
    private val authRepo: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _sideEffect = Channel<ProfileSideEffect>(Channel.BUFFERED)
    val sideEffect = _sideEffect.receiveAsFlow()

    init {
        loadProfile()
    }

    fun loadProfile() = viewModelScope.launch {
        _uiState.value = ProfileUiState.Loading
        userRepo.getProfile()
            .onSuccess { profile ->
                _uiState.value = profile?.let {
                    ProfileUiState.Loaded(it)
                } ?: ProfileUiState.Empty
            }
            .onFailure {
                val appErr = it.toAppError()
                appErr.reportToSentry()
                _uiState.value = ProfileUiState.Error(appErr)
            }
    }

    fun saveProfile(
        heightCm: Float,
        weightKg: Float,
        bodyFatPct: Float,
        muscleMassKg: Float,
        restDay: Int = 7,
    ) = viewModelScope.launch {
        val current = _uiState.value
        if (current is ProfileUiState.Loaded) {
            _uiState.value = current.copy(isSaving = true)
        }
        val userId = authRepo.getCurrentUserId()
        if (userId == null) {
            _sideEffect.send(ProfileSideEffect.ShowSnackbar("로그인이 필요합니다"))
            if (current is ProfileUiState.Loaded) {
                _uiState.value = current.copy(isSaving = false)
            }
            return@launch
        }
        runCatching {
            userRepo.saveProfile(
                UserProfile(userId, heightCm, weightKg, bodyFatPct, muscleMassKg, restDay),
            ).getOrThrow()
        }
            .onSuccess {
                _sideEffect.send(ProfileSideEffect.ShowSnackbar("신체 정보가 저장되었습니다"))
                _sideEffect.send(ProfileSideEffect.SavedAndNavigateBack)
            }
            .onFailure {
                val appErr = it.toAppError()
                appErr.reportToSentry()
                _sideEffect.send(ProfileSideEffect.ShowSnackbar(appErr.userMessage))
            }
        if (current is ProfileUiState.Loaded) {
            _uiState.value = current.copy(isSaving = false)
        }
    }

    fun deleteAccount() = viewModelScope.launch {
        val current = _uiState.value
        if (current is ProfileUiState.Loaded) {
            _uiState.value = current.copy(isDeleting = true)
        }
        authRepo.deleteAccount()
            .onSuccess { _sideEffect.send(ProfileSideEffect.NavigateToLogin) }
            .onFailure {
                if (current is ProfileUiState.Loaded) {
                    _uiState.value = current.copy(isDeleting = false)
                }
                val appErr = it.toAppError()
                appErr.reportToSentry()
                _sideEffect.send(ProfileSideEffect.ShowSnackbar(appErr.userMessage))
            }
    }
}
