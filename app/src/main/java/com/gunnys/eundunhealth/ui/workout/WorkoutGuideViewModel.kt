package com.gunnys.eundunhealth.ui.workout

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.model.CurrentWeekPlanRead
import com.gunnys.eundunhealth.domain.model.DayPlan
import com.gunnys.eundunhealth.domain.model.WorkoutPlanMode
import com.gunnys.eundunhealth.domain.model.toReportedAppError
import com.gunnys.eundunhealth.domain.repository.WorkoutRepository
import com.gunnys.eundunhealth.domain.usecase.CheckAndAwardBadgesUseCase
import com.gunnys.eundunhealth.domain.usecase.LoadCurrentWeekPlanUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

private val KST_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

@Immutable
sealed class WorkoutGuideSideEffect {
    @Immutable
    data object ExitGuide : WorkoutGuideSideEffect()
}

@Immutable
sealed class WorkoutGuideUiState {
    @Immutable
    data object Loading : WorkoutGuideUiState()

    @Immutable
    data class InProgress(
        val planId: String,
        val dayPlan: DayPlan,
        val mode: WorkoutPlanMode,
        val currentIndex: Int = 0,
        val isCompleting: Boolean = false,
        val completionError: AppError? = null,
    ) : WorkoutGuideUiState() {
        val totalExercises: Int get() = dayPlan.exercises.size
        val isLastExercise: Boolean get() = currentIndex >= totalExercises - 1
    }

    @Immutable
    data class CompletedSummary(
        val dayPlan: DayPlan,
        val mode: WorkoutPlanMode,
    ) : WorkoutGuideUiState()

    @Immutable
    data class Error(val error: AppError) : WorkoutGuideUiState()
}

@HiltViewModel
class WorkoutGuideViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val loadCurrentWeekPlan: LoadCurrentWeekPlanUseCase,
    private val workoutRepo: WorkoutRepository,
    private val checkBadges: CheckAndAwardBadgesUseCase,
) : ViewModel() {

    private val dateIso: String = savedStateHandle["dateIso"] ?: ""
    private val targetDate: LocalDate = runCatching { LocalDate.parse(dateIso) }
        .getOrElse { LocalDate.now(KST_ZONE) }

    private val _uiState = MutableStateFlow<WorkoutGuideUiState>(WorkoutGuideUiState.Loading)
    val uiState: StateFlow<WorkoutGuideUiState> = _uiState.asStateFlow()

    private val _sideEffect = Channel<WorkoutGuideSideEffect>(Channel.BUFFERED)
    val sideEffect = _sideEffect.receiveAsFlow()

    init {
        load()
    }

    fun load() = viewModelScope.launch {
        _uiState.value = WorkoutGuideUiState.Loading
        when (val read = loadCurrentWeekPlan()) {
            is CurrentWeekPlanRead.Found -> {
                val plan = read.plan
                val day = plan.days.find { it.date == targetDate }
                    ?: plan.days.firstOrNull { !it.isRestDay && it.exercises.isNotEmpty() }
                if (day == null || day.exercises.isEmpty()) {
                    _uiState.value = WorkoutGuideUiState.Error(
                        AppError.NotFound("오늘 진행할 운동 종목이 없습니다"),
                    )
                } else {
                    _uiState.value = WorkoutGuideUiState.InProgress(
                        planId = plan.id,
                        dayPlan = day,
                        mode = plan.mode,
                        currentIndex = 0,
                    )
                }
            }
            CurrentWeekPlanRead.Missing -> {
                _uiState.value = WorkoutGuideUiState.Error(
                    AppError.NotFound("현재 주의 운동 계획이 없습니다"),
                )
            }
            is CurrentWeekPlanRead.Unreadable -> {
                val err = read.error?.toReportedAppError()
                    ?: AppError.Unknown(IllegalStateException("계획 데이터를 읽을 수 없습니다"))
                _uiState.value = WorkoutGuideUiState.Error(err)
            }
            is CurrentWeekPlanRead.Failed -> {
                _uiState.value = WorkoutGuideUiState.Error(read.error.toReportedAppError())
            }
        }
    }

    fun setCurrentIndex(index: Int) {
        val current = _uiState.value
        if (current is WorkoutGuideUiState.InProgress) {
            val coerced = index.coerceIn(0, (current.totalExercises - 1).coerceAtLeast(0))
            _uiState.value = current.copy(currentIndex = coerced, completionError = null)
        }
    }

    fun nextExerciseOrComplete() {
        val current = _uiState.value
        if (current !is WorkoutGuideUiState.InProgress) return
        if (!current.isLastExercise) {
            _uiState.value = current.copy(
                currentIndex = current.currentIndex + 1,
                completionError = null,
            )
        } else {
            completeWorkout(exitImmediately = false)
        }
    }

    fun previousExercise() {
        val current = _uiState.value
        if (current is WorkoutGuideUiState.InProgress && current.currentIndex > 0) {
            _uiState.value = current.copy(
                currentIndex = current.currentIndex - 1,
                completionError = null,
            )
        }
    }

    fun completeWorkout(exitImmediately: Boolean = false) {
        val current = _uiState.value
        if (current !is WorkoutGuideUiState.InProgress || current.isCompleting) return
        _uiState.value = current.copy(isCompleting = true, completionError = null)
        viewModelScope.launch {
            workoutRepo.updateDayCompletion(
                planId = current.planId,
                date = current.dayPlan.date,
                completed = true,
                manual = true,
            ).onSuccess {
                // 배지 적립 확인 (백그라운드 안전 호출)
                val read = loadCurrentWeekPlan()
                if (read is CurrentWeekPlanRead.Found) {
                    checkBadges(read.plan)
                }
                if (exitImmediately) {
                    _sideEffect.send(WorkoutGuideSideEffect.ExitGuide)
                } else {
                    _uiState.value = WorkoutGuideUiState.CompletedSummary(
                        dayPlan = current.dayPlan.copy(isCompleted = true, manuallySet = true),
                        mode = current.mode,
                    )
                }
            }.onFailure { err ->
                val appErr = err.toReportedAppError()
                _uiState.value = current.copy(isCompleting = false, completionError = appErr)
            }
        }
    }
}
