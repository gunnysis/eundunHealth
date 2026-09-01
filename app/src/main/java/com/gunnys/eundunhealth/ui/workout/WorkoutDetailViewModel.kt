package com.gunnys.eundunhealth.ui.workout

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.model.Exercise
import com.gunnys.eundunhealth.domain.model.toReportedAppError
import com.gunnys.eundunhealth.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
sealed class WorkoutDetailUiState {
    @Immutable data object Loading : WorkoutDetailUiState()

    @Immutable data class Loaded(val exercise: Exercise) : WorkoutDetailUiState()

    @Immutable data class Error(val error: AppError) : WorkoutDetailUiState()
}

@HiltViewModel
class WorkoutDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutRepo: WorkoutRepository,
) : ViewModel() {

    private val exerciseId: String = savedStateHandle["exerciseId"] ?: ""

    private val _uiState = MutableStateFlow<WorkoutDetailUiState>(WorkoutDetailUiState.Loading)
    val uiState: StateFlow<WorkoutDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() = viewModelScope.launch {
        _uiState.value = WorkoutDetailUiState.Loading
        workoutRepo.getExerciseById(exerciseId)
            .onSuccess { exercise ->
                _uiState.value = if (exercise != null) {
                    WorkoutDetailUiState.Loaded(exercise)
                } else {
                    // 예상된 not-found 는 NotFound 로(Sentry 노이즈 제외). 진짜 실패만 onFailure 가 보고.
                    WorkoutDetailUiState.Error(AppError.NotFound("운동을 찾을 수 없습니다"))
                }
            }
            .onFailure {
                _uiState.value = WorkoutDetailUiState.Error(it.toReportedAppError())
            }
    }
}
