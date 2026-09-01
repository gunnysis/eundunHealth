package com.gunnys.eundunhealth.ui.statistics

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.model.Statistics
import com.gunnys.eundunhealth.domain.model.toReportedAppError
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

    @Immutable data class Error(val error: AppError) : StatisticsUiState()
}

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val workoutRepo: WorkoutRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<StatisticsUiState>(StatisticsUiState.Loading)
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load(weeks: Int = 12) = viewModelScope.launch {
        _uiState.value = StatisticsUiState.Loading
        workoutRepo.getStatistics(weeks)
            .onSuccess { stats ->
                _uiState.value = if (stats.weeklyRates.isEmpty()) {
                    StatisticsUiState.Empty
                } else {
                    StatisticsUiState.Loaded(stats)
                }
            }
            .onFailure {
                _uiState.value = StatisticsUiState.Error(it.toReportedAppError())
            }
    }
}
