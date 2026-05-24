package com.gunnys.eundunhealth.ui.goal

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.model.Goal
import com.gunnys.eundunhealth.domain.model.GoalType
import com.gunnys.eundunhealth.domain.model.ProfileHistoryPoint
import com.gunnys.eundunhealth.domain.model.reportToSentry
import com.gunnys.eundunhealth.domain.model.toAppError
import com.gunnys.eundunhealth.domain.repository.GoalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class GoalUiState(
    val goals: List<Goal> = emptyList(),
    val history: List<ProfileHistoryPoint> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
)

@HiltViewModel
class GoalViewModel @Inject constructor(
    private val goalRepo: GoalRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GoalUiState())
    val uiState: StateFlow<GoalUiState> = _uiState.asStateFlow()

    private val _error = MutableStateFlow<AppError?>(null)
    val error: StateFlow<AppError?> = _error.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    init {
        load()
    }

    fun load() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true)
        val goalsResult = goalRepo.getGoals()
        val historyResult = goalRepo.getProfileHistory()

        val goals = goalsResult.getOrElse {
            handleError(it)
            emptyList()
        }
        val history = historyResult.getOrElse {
            handleError(it)
            emptyList()
        }

        _uiState.value = GoalUiState(goals = goals, history = history, isLoading = false)
    }

    fun saveGoal(type: GoalType, value: Float) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isSaving = true)
        goalRepo.upsertGoal(type, value)
            .onSuccess { saved ->
                val merged = _uiState.value.goals.filterNot { it.type == saved.type } + saved
                _uiState.value = _uiState.value.copy(goals = merged, isSaving = false)
            }
            .onFailure {
                handleError(it)
                _uiState.value = _uiState.value.copy(isSaving = false)
            }
    }

    private fun handleError(t: Throwable) {
        val appErr = t.toAppError()
        appErr.reportToSentry()
        _error.value = appErr
    }
}
