package com.gunnys.eundunhealth.ui.mealplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gunnys.eundunhealth.domain.model.WeeklyMealPlan
import com.gunnys.eundunhealth.domain.repository.MealPlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface MealPlanUiState {
    object Loading : MealPlanUiState
    object Empty : MealPlanUiState
    data class Success(val plan: WeeklyMealPlan) : MealPlanUiState
    data class Error(val message: String) : MealPlanUiState
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
                onFailure = {
                    _uiState.value = MealPlanUiState.Error(it.message ?: "식단을 불러오는데 실패했습니다.")
                },
            )
        }
    }

    fun generatePlan() {
        _uiState.value = MealPlanUiState.Loading
        viewModelScope.launch {
            repository.generatePlan().fold(
                onSuccess = { plan ->
                    _uiState.value = MealPlanUiState.Success(plan)
                },
                onFailure = {
                    _uiState.value = MealPlanUiState.Error(it.message ?: "식단 생성에 실패했습니다.")
                },
            )
        }
    }
}
