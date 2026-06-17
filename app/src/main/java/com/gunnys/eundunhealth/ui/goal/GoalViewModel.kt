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

        goalsResult.fold(
            onSuccess = { goals ->
                // goals(핵심 콘텐츠) 성공 → 화면 렌더. history(진행 차트)는 비핵심이라 실패해도
                // 화면을 막지 않고 snackbar 로만 알린다(목표 편집기는 계속 사용 가능). 실패를 빈
                // 데이터로 흘려보내면 "데이터 없음"으로 오표시되던 것(silent failure)을 차트 한정으로만 둔다.
                val history = historyResult.getOrElse {
                    handleError(it)
                    emptyList()
                }
                _uiState.value = GoalUiState(goals = goals, history = history, isLoading = false)
            },
            onFailure = {
                // goals 로드 실패 = 핵심 콘텐츠 실패 → 전체 에러 상태(ErrorContent + 재시도).
                val appErr = it.toAppError()
                appErr.reportToSentry()
                _uiState.value = _uiState.value.copy(isLoading = false, error = appErr)
            },
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
