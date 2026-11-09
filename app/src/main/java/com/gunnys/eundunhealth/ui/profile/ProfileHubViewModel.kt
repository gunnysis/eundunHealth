package com.gunnys.eundunhealth.ui.profile

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gunnys.eundunhealth.data.preferences.ThemeMode
import com.gunnys.eundunhealth.data.preferences.ThemePreferences
import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.model.UserProfile
import com.gunnys.eundunhealth.domain.model.WorkoutPlanMode
import com.gunnys.eundunhealth.domain.model.runSuspendCatching
import com.gunnys.eundunhealth.domain.model.toReportedAppError
import com.gunnys.eundunhealth.domain.repository.HealthRepository
import com.gunnys.eundunhealth.domain.repository.UserRepository
import com.gunnys.eundunhealth.domain.repository.WearBiometricRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class ProfileHubUiState(
    val profile: UserProfile? = null,
    val isHealthConnectLoading: Boolean = true,
    val isHealthConnectAvailable: Boolean = false,
    val hasHealthPermission: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColorEnabled: Boolean = false,
    val defaultWorkoutPlanMode: WorkoutPlanMode? = null,
    val isUpdatingDefaultPlanMode: Boolean = false,
    val defaultPlanModeError: AppError? = null,
    val watchSummaryCount: Int = 0,
    val latestWatchSummaryDate: String? = null,
    val latestWatchRecoveryScore: Int? = null,
    val isSyncingWatch: Boolean = false,
    val watchStatusMessage: String = "샤오미 워치 5 상시 생체·마음 상태 연동 준비됨",
)

@HiltViewModel
class ProfileHubViewModel @Inject constructor(
    private val healthRepository: HealthRepository,
    private val themePreferences: ThemePreferences,
    private val userRepository: UserRepository,
    private val wearBiometricRepository: WearBiometricRepository? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileHubUiState())
    val uiState: StateFlow<ProfileHubUiState> = _uiState.asStateFlow()

    init {
        refreshHealthConnectStatus()
        refreshDefaultWorkoutPlanMode()
        observeWatchBiometrics()
        viewModelScope.launch {
            val mode = themePreferences.themeMode.first()
            val dynamicColor = runSuspendCatching { themePreferences.dynamicColorEnabled.first() }.getOrDefault(false)
            _uiState.value = _uiState.value.copy(
                themeMode = mode,
                dynamicColorEnabled = dynamicColor,
            )
        }
    }

    private fun observeWatchBiometrics() {
        val repo = wearBiometricRepository ?: return
        viewModelScope.launch {
            repo.refreshRemoteIfEmpty(days = 14)
        }
        viewModelScope.launch {
            repo.observeRecentSummaries(limitDays = 14).collect { items ->
                val latest = items.firstOrNull()
                _uiState.update { prev ->
                    prev.copy(
                        watchSummaryCount = items.size,
                        latestWatchSummaryDate = latest?.recordDate,
                        latestWatchRecoveryScore = latest?.recoveryScore,
                        watchStatusMessage =
                        if (items.isNotEmpty()) {
                            "최근 ${items.size}일 동기화됨 (최신 ${latest?.recordDate ?: "-"})"
                        } else {
                            "워치에서 측정된 일일 생체 요약 대기 중"
                        },
                    )
                }
            }
        }
    }

    fun syncWatchBiometricsNow() {
        val repo = wearBiometricRepository ?: return
        if (_uiState.value.isSyncingWatch) return
        _uiState.update { it.copy(isSyncingWatch = true, watchStatusMessage = "워치 및 서버 동기화 요청 중...") }
        viewModelScope.launch {
            repo.requestWatchSyncNow()
                .onSuccess { msg ->
                    _uiState.update { it.copy(isSyncingWatch = false, watchStatusMessage = msg) }
                }
                .onFailure { err ->
                    err.toReportedAppError()
                    _uiState.update {
                        it.copy(
                            isSyncingWatch = false,
                            watchStatusMessage = "워치 동기화 요청 실패 — 다시 시도해 주세요",
                        )
                    }
                }
        }
    }

    fun deleteAllWatchBiometrics() {
        val repo = wearBiometricRepository ?: return
        if (_uiState.value.isSyncingWatch) return
        _uiState.update { it.copy(isSyncingWatch = true, watchStatusMessage = "워치 생체 요약 기록 삭제 중...") }
        viewModelScope.launch {
            repo.deleteAllSummaries()
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isSyncingWatch = false,
                            watchSummaryCount = 0,
                            latestWatchSummaryDate = null,
                            latestWatchRecoveryScore = null,
                            watchStatusMessage = "워치 생체 요약 기록이 모두 삭제되었습니다",
                        )
                    }
                }
                .onFailure { err ->
                    err.toReportedAppError()
                    _uiState.update {
                        it.copy(
                            isSyncingWatch = false,
                            watchStatusMessage = "워치 기록 삭제 실패 — 네트워크 상태를 확인해 주세요",
                        )
                    }
                }
        }
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
                    profile = profile,
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
                    profile = updatedProfile,
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

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        themePreferences.setThemeMode(mode)
        _uiState.value = _uiState.value.copy(themeMode = mode)
    }

    fun setDynamicColorEnabled(enabled: Boolean) = viewModelScope.launch {
        themePreferences.setDynamicColorEnabled(enabled)
        _uiState.value = _uiState.value.copy(dynamicColorEnabled = enabled)
    }
}
