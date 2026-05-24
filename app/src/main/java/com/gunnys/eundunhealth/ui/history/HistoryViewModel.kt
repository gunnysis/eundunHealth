package com.gunnys.eundunhealth.ui.history

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.model.WeeklyPlan
import com.gunnys.eundunhealth.domain.model.reportToSentry
import com.gunnys.eundunhealth.domain.model.toAppError
import com.gunnys.eundunhealth.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class HistoryUiState(
    val plans: List<WeeklyPlan> = emptyList(),
    val isLoading: Boolean = false,
    val hasMore: Boolean = true,
    val page: Int = 0,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val workoutRepo: WorkoutRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val _error = MutableStateFlow<AppError?>(null)
    val error: StateFlow<AppError?> = _error.asStateFlow()

    fun clearError() { _error.value = null }

    private val pageSize = 10

    init {
        loadNextPage()
    }

    fun loadNextPage() {
        val current = _uiState.value
        if (current.isLoading || !current.hasMore) return

        viewModelScope.launch {
            _uiState.value = current.copy(isLoading = true)
            workoutRepo.getHistory(current.page, pageSize)
                .onSuccess { (plans, totalCount) ->
                    _uiState.value = current.copy(
                        plans = current.plans + plans,
                        isLoading = false,
                        page = current.page + 1,
                        hasMore = current.plans.size + plans.size < totalCount
                    )
                }
                .onFailure {
                    _uiState.value = current.copy(isLoading = false)
                    val appErr = it.toAppError()
                    appErr.reportToSentry()
                    _error.value = appErr
                }
        }
    }
}
