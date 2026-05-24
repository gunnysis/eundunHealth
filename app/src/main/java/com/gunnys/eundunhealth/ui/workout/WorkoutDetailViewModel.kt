package com.gunnys.eundunhealth.ui.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.model.Exercise
import com.gunnys.eundunhealth.domain.model.reportToSentry
import com.gunnys.eundunhealth.domain.model.toAppError
import com.gunnys.eundunhealth.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WorkoutDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutRepo: WorkoutRepository,
) : ViewModel() {

    private val exerciseId: String = savedStateHandle["exerciseId"] ?: ""

    private val _exercise = MutableStateFlow<Exercise?>(null)
    val exercise: StateFlow<Exercise?> = _exercise.asStateFlow()

    private val _error = MutableStateFlow<AppError?>(null)
    val error: StateFlow<AppError?> = _error.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    init {
        loadExercise()
    }

    private fun loadExercise() = viewModelScope.launch {
        workoutRepo.getCurrentWeekPlan()
            .onSuccess { plan ->
                _exercise.value = plan?.days
                    ?.flatMap { it.exercises }
                    ?.find { it.id == exerciseId }
            }
            .onFailure {
                val appErr = it.toAppError()
                appErr.reportToSentry()
                _error.value = appErr
            }
    }
}
