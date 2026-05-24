package com.gunnys.eundunhealth.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gunnys.eundunhealth.data.preferences.ThemeMode
import com.gunnys.eundunhealth.data.preferences.ThemePreferences
import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.model.WeeklyPlan
import com.gunnys.eundunhealth.domain.model.reportToSentry
import com.gunnys.eundunhealth.domain.model.toAppError
import com.gunnys.eundunhealth.domain.repository.HealthRepository
import com.gunnys.eundunhealth.domain.repository.WorkoutRepository
import com.gunnys.eundunhealth.domain.usecase.CheckAndAwardBadgesUseCase
import com.gunnys.eundunhealth.domain.usecase.GetOrCreateWeeklyPlanUseCase
import com.gunnys.eundunhealth.domain.usecase.SyncHealthDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

sealed class HomeUiState {
    @Immutable
    data object Loading : HomeUiState()
    @Immutable
    data class Success(
        val plan: WeeklyPlan,
        val hasHealthPermission: Boolean = false,
        val completedCount: Int = 0,
        val totalWorkoutDays: Int = 0
    ) : HomeUiState() {
        val completionRate: Float get() = if (totalWorkoutDays > 0) completedCount.toFloat() / totalWorkoutDays else 0f
    }
    @Immutable
    data object Empty : HomeUiState()  // 로드 실패 → 화면은 _error로 메시지 표시
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getOrCreateWeeklyPlan: GetOrCreateWeeklyPlanUseCase,
    private val syncHealth: SyncHealthDataUseCase,
    private val checkBadges: CheckAndAwardBadgesUseCase,
    private val healthRepo: HealthRepository,
    private val workoutRepo: WorkoutRepository,
    private val themePreferences: ThemePreferences
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = themePreferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    fun cycleTheme() = viewModelScope.launch {
        val next = when (themeMode.value) {
            ThemeMode.SYSTEM -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.SYSTEM
        }
        themePreferences.setThemeMode(next)
    }

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _error = MutableStateFlow<AppError?>(null)
    val error: StateFlow<AppError?> = _error.asStateFlow()

    fun clearError() { _error.value = null }

    init {
        loadPlan()
    }

    private fun successWithStats(plan: WeeklyPlan, hasPerm: Boolean) = HomeUiState.Success(
        plan = plan,
        hasHealthPermission = hasPerm,
        completedCount = plan.days.count { !it.isRestDay && it.isCompleted },
        totalWorkoutDays = plan.days.count { !it.isRestDay }
    )

    fun loadPlan() = viewModelScope.launch {
        _uiState.value = HomeUiState.Loading
        getOrCreateWeeklyPlan()
            .onSuccess { plan ->
                val synced = syncHealth(plan).getOrElse { plan }
                checkBadges(synced)
                val hasPerm = healthRepo.hasPermissions()

                // Sync HC-detected completions to server
                synced.days.zip(plan.days).forEach { (syncedDay, originalDay) ->
                    if (syncedDay.isCompleted && !originalDay.isCompleted) {
                        workoutRepo.updateDayCompletion(synced.id, syncedDay.date, true)
                    }
                }

                _uiState.value = successWithStats(synced, hasPerm)
            }
            .onFailure {
                val appErr = it.toAppError()
                appErr.reportToSentry()
                _error.value = appErr
                _uiState.value = HomeUiState.Empty
            }
    }

    fun toggleDayCompletion(date: LocalDate) = viewModelScope.launch {
        val current = _uiState.value
        if (current !is HomeUiState.Success) return@launch

        val day = current.plan.days.find { it.date == date } ?: return@launch
        val newCompleted = !day.isCompleted

        // Optimistic update
        val updatedDays = current.plan.days.map {
            if (it.date == date) it.copy(isCompleted = newCompleted) else it
        }
        val updatedPlan = current.plan.copy(days = updatedDays)
        _uiState.value = successWithStats(updatedPlan, current.hasHealthPermission)

        // Server sync
        workoutRepo.updateDayCompletion(current.plan.id, date, newCompleted)
            .onFailure {
                // Revert on failure
                _uiState.value = current
                val appErr = it.toAppError()
                appErr.reportToSentry()
                _error.value = appErr
            }
    }
}
