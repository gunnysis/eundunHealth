package com.gunnys.eundunhealth.ui.mealplan

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.model.WeeklyMealPlan
import com.gunnys.eundunhealth.domain.model.toReportedAppError
import com.gunnys.eundunhealth.domain.repository.MealPlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
sealed interface MealPlanUiState {
    @Immutable
    data object Loading : MealPlanUiState

    @Immutable
    data object Empty : MealPlanUiState

    @Immutable
    data class Generating(
        val previousPlan: WeeklyMealPlan? = null,
    ) : MealPlanUiState

    @Immutable
    data class Success(
        val plan: WeeklyMealPlan,
        val regenerateError: AppError? = null,
    ) : MealPlanUiState

    @Immutable
    data class Error(
        val error: AppError,
    ) : MealPlanUiState
}

@HiltViewModel
class MealPlanViewModel @Inject constructor(
    private val repository: MealPlanRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<MealPlanUiState>(MealPlanUiState.Loading)
    val uiState: StateFlow<MealPlanUiState> = _uiState.asStateFlow()

    init {
        loadCurrentPlan()
    }

    fun loadCurrentPlan() {
        _uiState.value = MealPlanUiState.Loading
        viewModelScope.launch {
            repository.getCurrentPlan().fold(
                onSuccess = { plan ->
                    if (plan != null) {
                        _uiState.value = MealPlanUiState.Success(plan)
                    } else {
                        _uiState.value = MealPlanUiState.Empty
                    }
                },
                onFailure = { err ->
                    _uiState.value = MealPlanUiState.Error(err.toReportedAppError())
                },
            )
        }
    }

    fun generatePlan() {
        val current = _uiState.value
        if (current is MealPlanUiState.Generating) return
        val existingPlan = (current as? MealPlanUiState.Success)?.plan
        _uiState.value = MealPlanUiState.Generating(previousPlan = existingPlan)
        viewModelScope.launch {
            repository.generatePlan().fold(
                onSuccess = { plan ->
                    _uiState.value = MealPlanUiState.Success(plan = plan, regenerateError = null)
                },
                onFailure = { err ->
                    val appErr = err.toReportedAppError()
                    if (existingPlan != null) {
                        // 기존 식단이 있는 상태에서 재생성에 실패하면 기존 식단을 보존하고 인라인 배너로 알린다 (룰 8)
                        _uiState.value = MealPlanUiState.Success(
                            plan = existingPlan,
                            regenerateError = appErr,
                        )
                    } else {
                        _uiState.value = MealPlanUiState.Error(appErr)
                    }
                },
            )
        }
    }
}
