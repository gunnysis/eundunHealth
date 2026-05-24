package com.gunnys.eundunhealth.ui.onboarding

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

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userRepo: UserRepository,
    private val authRepo: AuthRepository,
) : ViewModel() {

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<AppError?>(null)
    val error: StateFlow<AppError?> = _error.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    fun saveProfile(heightCm: Float, weightKg: Float, bodyFatPct: Float, muscleMassKg: Float) = viewModelScope.launch {
        _isLoading.value = true
        _error.value = null
        val userId = authRepo.getCurrentUserId()
        if (userId == null) {
            _error.value = AppError.Auth("로그인이 필요합니다")
            _isLoading.value = false
            return@launch
        }
        runCatching {
            userRepo.saveProfile(
                UserProfile(userId, heightCm, weightKg, bodyFatPct, muscleMassKg),
            ).getOrThrow()
        }
            .onSuccess { _saved.value = true }
            .onFailure {
                val appErr = it.toAppError()
                appErr.reportToSentry()
                _error.value = appErr
            }
        _isLoading.value = false
    }
}
