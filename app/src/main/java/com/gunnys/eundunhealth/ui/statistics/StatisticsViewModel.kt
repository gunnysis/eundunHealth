package com.gunnys.eundunhealth.ui.statistics

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.model.Statistics
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
sealed class StatisticsUiState {
    @Immutable data object Loading : StatisticsUiState()
    @Immutable data class Loaded(val data: Statistics) : StatisticsUiState()
    @Immutable data object Empty : StatisticsUiState()
}

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val workoutRepo: WorkoutRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<StatisticsUiState>(StatisticsUiState.Loading)
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    private val _error = MutableStateFlow<AppError?>(null)
    val error: StateFlow<AppError?> = _error.asStateFlow()

    fun clearError() { _error.value = null }

    init {
        load()
    }

    fun load(weeks: Int = 12) = viewModelScope.launch {
        _uiState.value = StatisticsUiState.Loading
        workoutRepo.getStatistics(weeks)
            .onSuccess { stats ->
                _uiState.value = if (stats.weeklyRates.isEmpty()) StatisticsUiState.Empty
                                 else StatisticsUiState.Loaded(stats)
            }
            .onFailure {
                val appErr = it.toAppError()
                appErr.reportToSentry()
                _error.value = appErr
                _uiState.value = StatisticsUiState.Empty
            }
    }
}
