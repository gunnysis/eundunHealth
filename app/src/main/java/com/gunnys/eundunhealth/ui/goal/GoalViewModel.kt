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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
sealed class GoalSideEffect {
    data class ShowSnackbar(val message: String) : GoalSideEffect()
}

@Immutable
data class GoalUiState(
    val goals: List<Goal> = emptyList(),
    val history: List<ProfileHistoryPoint> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: AppError? = null,
)

@HiltViewModel
class GoalViewModel @Inject constructor(
    private val goalRepo: GoalRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GoalUiState())
    val uiState: StateFlow<GoalUiState> = _uiState.asStateFlow()

    private val _sideEffect = Channel<GoalSideEffect>(Channel.BUFFERED)
    val sideEffect = _sideEffect.receiveAsFlow()

    init {
        load()
    }

    fun load() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        val goalsResult = goalRepo.getGoals()
        val historyResult = goalRepo.getProfileHistory()

        // 로드 실패를 빈 데이터로 흘려보내면 "데이터 없음"으로 오표시된다(silent failure).
        // 첫 실패를 error 로 노출해 ErrorContent + 재시도를 띄운다.
        val firstError = goalsResult.exceptionOrNull() ?: historyResult.exceptionOrNull()
        if (firstError != null) {
            val appErr = firstError.toAppError()
            appErr.reportToSentry()
            _uiState.value = _uiState.value.copy(isLoading = false, error = appErr)
            return@launch
        }

        _uiState.value = GoalUiState(
            goals = goalsResult.getOrDefault(emptyList()),
            history = historyResult.getOrDefault(emptyList()),
            isLoading = false,
        )
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
        _sideEffect.trySend(GoalSideEffect.ShowSnackbar(appErr.userMessage))
    }
}
