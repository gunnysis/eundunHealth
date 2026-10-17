package com.gunnys.eundunhealth.ui.profile

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gunnys.eundunhealth.data.preferences.ThemeMode
import com.gunnys.eundunhealth.data.preferences.ThemePreferences
import com.gunnys.eundunhealth.domain.repository.HealthRepository
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
)

@HiltViewModel
class ProfileHubViewModel @Inject constructor(
    private val healthRepository: HealthRepository,
    private val themePreferences: ThemePreferences,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileHubUiState())
    val uiState: StateFlow<ProfileHubUiState> = _uiState.asStateFlow()

    init {
        refreshHealthConnectStatus()
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
