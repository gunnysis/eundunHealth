package com.gunnys.eundunhealth.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gunnys.eundunhealth.domain.model.WeeklyPlan
import com.gunnys.eundunhealth.domain.repository.HealthRepository
import com.gunnys.eundunhealth.domain.usecase.CheckAndAwardBadgesUseCase
import com.gunnys.eundunhealth.domain.usecase.GetOrCreateWeeklyPlanUseCase
import com.gunnys.eundunhealth.domain.usecase.SyncHealthDataUseCase
import androidx.compose.runtime.Immutable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class HomeUiState {
    @Immutable
    data object Loading : HomeUiState()
    @Immutable
    data class Success(val plan: WeeklyPlan, val hasHealthPermission: Boolean = false) : HomeUiState()
    @Immutable
    data class Error(val message: String) : HomeUiState()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getOrCreateWeeklyPlan: GetOrCreateWeeklyPlanUseCase,
    private val syncHealth: SyncHealthDataUseCase,
    private val checkBadges: CheckAndAwardBadgesUseCase,
    private val healthRepo: HealthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadPlan()
    }

    fun loadPlan() = viewModelScope.launch {
        _uiState.value = HomeUiState.Loading
        getOrCreateWeeklyPlan()
            .onSuccess { plan ->
                val synced = syncHealth(plan).getOrElse { plan }
                checkBadges(synced)
                val hasPerm = healthRepo.hasPermissions()
                _uiState.value = HomeUiState.Success(synced, hasPerm)
            }
            .onFailure {
                _uiState.value = HomeUiState.Error(it.message ?: "운동 계획을 불러올 수 없습니다")
            }
    }
}
