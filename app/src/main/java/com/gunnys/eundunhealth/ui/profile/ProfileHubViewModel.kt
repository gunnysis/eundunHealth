package com.gunnys.eundunhealth.ui.profile

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gunnys.eundunhealth.data.preferences.ThemeMode
import com.gunnys.eundunhealth.data.preferences.ThemePreferences
import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.model.WorkoutPlanMode
import com.gunnys.eundunhealth.domain.model.toReportedAppError
import com.gunnys.eundunhealth.domain.repository.HealthRepository
import com.gunnys.eundunhealth.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class ProfileHubUiState(
    val isHealthConnectLoading: Boolean = true,
    val isHealthConnectAvailable: Boolean = false,
    val hasHealthPermission: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultWorkoutPlanMode: WorkoutPlanMode? = null,
    val isUpdatingDefaultPlanMode: Boolean = false,
    val defaultPlanModeError: AppError? = null,
)

@HiltViewModel
class ProfileHubViewModel @Inject constructor(
    private val healthRepository: HealthRepository,
    private val themePreferences: ThemePreferences,
    private val userRepository: UserRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileHubUiState())
    val uiState: StateFlow<ProfileHubUiState> = _uiState.asStateFlow()

    init {
        refreshHealthConnectStatus()
        refreshDefaultWorkoutPlanMode()
        viewModelScope.launch { _uiState.value = _uiState.value.copy(themeMode = themePreferences.themeMode.first()) }
    }

    fun refreshHealthConnectStatus() = viewModelScope.launch {
        val available = healthRepository.isAvailable()
        _uiState.value = _uiState.value.copy(
            isHealthConnectLoading = false,
            isHealthConnectAvailable = available,
            hasHealthPermission = available && healthRepository.hasPermissions(),
        )
    }

    fun refreshDefaultWorkoutPlanMode() = viewModelScope.launch {
        userRepository.getProfile()
            .onSuccess { profile ->
                _uiState.value = _uiState.value.copy(
                    defaultWorkoutPlanMode = profile?.defaultWorkoutPlanMode,
                )
            }
            .onFailure {
                it.toReportedAppError()
            }
    }

    fun updateDefaultWorkoutPlanMode(mode: WorkoutPlanMode?) = viewModelScope.launch {
        if (_uiState.value.isUpdatingDefaultPlanMode) return@launch
        val previous = _uiState.value.defaultWorkoutPlanMode
        _uiState.value = _uiState.value.copy(
            defaultWorkoutPlanMode = mode,
            isUpdatingDefaultPlanMode = true,
            defaultPlanModeError = null,
        )
        userRepository.updateDefaultWorkoutPlanMode(mode)
            .onSuccess { updatedProfile ->
                _uiState.value = _uiState.value.copy(
                    defaultWorkoutPlanMode = updatedProfile.defaultWorkoutPlanMode,
                    isUpdatingDefaultPlanMode = false,
                    defaultPlanModeError = null,
                )
            }
            .onFailure { err ->
                val appErr = err.toReportedAppError()
                _uiState.value = _uiState.value.copy(
                    defaultWorkoutPlanMode = previous,
                    isUpdatingDefaultPlanMode = false,
                    defaultPlanModeError = appErr,
                )
            }
    }

    fun cycleTheme() = viewModelScope.launch {
        val next = when (_uiState.value.themeMode) {
            ThemeMode.SYSTEM -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.SYSTEM
        }
        themePreferences.setThemeMode(next)
        _uiState.value = _uiState.value.copy(themeMode = next)
    }
}
