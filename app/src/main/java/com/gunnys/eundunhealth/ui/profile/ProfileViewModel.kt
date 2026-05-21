package com.gunnys.eundunhealth.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gunnys.eundunhealth.domain.model.UserProfile
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
    data class Error(val message: String) : ProfileUiState()
}

sealed class SaveState {
    data object Idle : SaveState()
    data object Success : SaveState()
    data class Error(val message: String) : SaveState()
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepo: UserRepository,
    private val authRepo: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() = viewModelScope.launch {
        _uiState.value = ProfileUiState.Loading
        userRepo.getProfile()
            .onSuccess { profile ->
                if (profile != null) {
                    _uiState.value = ProfileUiState.Loaded(profile)
                } else {
                    _uiState.value = ProfileUiState.Error("프로필 정보를 찾을 수 없습니다")
                }
            }
            .onFailure {
                _uiState.value = ProfileUiState.Error("프로필을 불러올 수 없습니다")
            }
    }

    fun saveProfile(heightCm: Float, weightKg: Float, bodyFatPct: Float, muscleMassKg: Float) =
        viewModelScope.launch {
            _isSaving.value = true
            _saveState.value = SaveState.Idle
            try {
                val userId = authRepo.getCurrentUserId()
                    ?: throw IllegalStateException("로그인이 필요합니다")
                userRepo.saveProfile(
                    UserProfile(userId, heightCm, weightKg, bodyFatPct, muscleMassKg)
                ).getOrThrow()
                _saveState.value = SaveState.Success
            } catch (e: Exception) {
                _saveState.value = SaveState.Error(e.message ?: "저장에 실패했습니다")
            } finally {
                _isSaving.value = false
            }
        }

    fun clearSaveState() {
        _saveState.value = SaveState.Idle
    }
}
