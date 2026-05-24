package com.gunnys.eundunhealth.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.model.UserProfile
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

sealed class ProfileUiState {
    data object Loading : ProfileUiState()
    data class Loaded(val profile: UserProfile) : ProfileUiState()
    data object Empty : ProfileUiState() // 프로필 미존재 (정상 케이스 — 에러 아님)
}

sealed class SaveState {
    data object Idle : SaveState()
    data object Success : SaveState()
}

sealed class DeleteState {
    data object Idle : DeleteState()
    data object Loading : DeleteState()
    data object Success : DeleteState() // 화면 측에서 Login으로 navigate
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepo: UserRepository,
    private val authRepo: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    private val _error = MutableStateFlow<AppError?>(null)
    val error: StateFlow<AppError?> = _error.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    init {
        loadProfile()
    }

    fun loadProfile() = viewModelScope.launch {
        _uiState.value = ProfileUiState.Loading
        userRepo.getProfile()
            .onSuccess { profile ->
                _uiState.value = profile?.let { ProfileUiState.Loaded(it) } ?: ProfileUiState.Empty
            }
            .onFailure {
                val appErr = it.toAppError()
                appErr.reportToSentry()
                _error.value = appErr
                // 로드 실패 시 Empty로 폴백 — 화면은 ErrorContent 사용
                _uiState.value = ProfileUiState.Empty
            }
    }

    fun saveProfile(heightCm: Float, weightKg: Float, bodyFatPct: Float, muscleMassKg: Float) = viewModelScope.launch {
        _isSaving.value = true
        _saveState.value = SaveState.Idle
        val userId = authRepo.getCurrentUserId()
        if (userId == null) {
            _error.value = AppError.Auth("로그인이 필요합니다")
            _isSaving.value = false
            return@launch
        }
        runCatching {
            userRepo.saveProfile(
                UserProfile(userId, heightCm, weightKg, bodyFatPct, muscleMassKg),
            ).getOrThrow()
        }
            .onSuccess { _saveState.value = SaveState.Success }
            .onFailure {
                val appErr = it.toAppError()
                appErr.reportToSentry()
                _error.value = appErr
            }
        _isSaving.value = false
    }

    fun clearSaveState() {
        _saveState.value = SaveState.Idle
    }

    private val _deleteState = MutableStateFlow<DeleteState>(DeleteState.Idle)
    val deleteState: StateFlow<DeleteState> = _deleteState.asStateFlow()

    fun deleteAccount() = viewModelScope.launch {
        _deleteState.value = DeleteState.Loading
        authRepo.deleteAccount()
            .onSuccess { _deleteState.value = DeleteState.Success }
            .onFailure {
                _deleteState.value = DeleteState.Idle
                val appErr = it.toAppError()
                appErr.reportToSentry()
                _error.value = appErr
            }
    }
}
