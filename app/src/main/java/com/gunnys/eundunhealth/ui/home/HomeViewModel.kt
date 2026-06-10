package com.gunnys.eundunhealth.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gunnys.eundunhealth.data.preferences.ThemeMode
import com.gunnys.eundunhealth.data.preferences.ThemePreferences
import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.model.DailyActivity
import com.gunnys.eundunhealth.domain.model.WeeklyPlan
import com.gunnys.eundunhealth.domain.model.reportToSentry
import com.gunnys.eundunhealth.domain.model.toAppError
import com.gunnys.eundunhealth.domain.repository.WorkoutRepository
import com.gunnys.eundunhealth.domain.usecase.CheckAndAwardBadgesUseCase
import com.gunnys.eundunhealth.domain.usecase.GetOrCreateWeeklyPlanUseCase
import com.gunnys.eundunhealth.domain.usecase.GetTodayActivityUseCase
import com.gunnys.eundunhealth.domain.usecase.HealthSyncResult
import com.gunnys.eundunhealth.domain.usecase.SyncHealthDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@Immutable
sealed class HomeUiState {
    @Immutable
    data object Loading : HomeUiState()

    @Immutable
    data class Success(
        val plan: WeeklyPlan,
        val isHealthConnectAvailable: Boolean = true,
        val hasHealthPermission: Boolean = false,
        val completedCount: Int = 0,
        val totalWorkoutDays: Int = 0,
        val todayActivity: DailyActivity? = null,
        val hasActivityPermission: Boolean = false,
    ) : HomeUiState() {
        val completionRate: Float get() = if (totalWorkoutDays > 0) completedCount.toFloat() / totalWorkoutDays else 0f
    }

    @Immutable
    data class Error(val error: AppError) : HomeUiState()
}

@Immutable
sealed class HomeSideEffect {
    @Immutable
    data class ShowSnackbar(val message: String) : HomeSideEffect()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getOrCreateWeeklyPlan: GetOrCreateWeeklyPlanUseCase,
    private val syncHealth: SyncHealthDataUseCase,
    private val checkBadges: CheckAndAwardBadgesUseCase,
    private val workoutRepo: WorkoutRepository,
    private val themePreferences: ThemePreferences,
    private val getTodayActivity: GetTodayActivityUseCase,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = themePreferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    fun cycleTheme() = viewModelScope.launch {
        val next = when (themeMode.value) {
            ThemeMode.SYSTEM -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.SYSTEM
        }
        themePreferences.setThemeMode(next)
    }

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _sideEffect = Channel<HomeSideEffect>(Channel.BUFFERED)
    val sideEffect = _sideEffect.receiveAsFlow()

    init {
        loadPlan()
    }

    private fun successWithStats(plan: WeeklyPlan, isAvailable: Boolean, hasPermission: Boolean) = HomeUiState.Success(
        plan = plan,
        isHealthConnectAvailable = isAvailable,
        hasHealthPermission = hasPermission,
        completedCount = plan.days.count { !it.isRestDay && it.isCompleted },
        totalWorkoutDays = plan.days.count { !it.isRestDay },
    )

    fun loadPlan() = viewModelScope.launch {
        _uiState.value = HomeUiState.Loading
        getOrCreateWeeklyPlan()
            .onSuccess { plan ->
                val sync = syncHealth(plan).getOrElse {
                    HealthSyncResult(plan, isAvailable = false, hasPermission = false, newlyCompletedDates = emptyList())
                }

                // 렌더 먼저 — 서버 푸시/배지 적립을 기다리지 않고 즉시 화면을 그린다.
                _uiState.value = successWithStats(sync.plan, sync.isAvailable, sync.hasPermission)
                loadTodayActivity()

                // Health Connect 가 새로 감지한 완료만 서버에 반영 (백그라운드).
                // 미반영 날은 다음 sync 시 HC 재감지로 재시도되지만, silent drop 방지를 위해
                // 실패는 Sentry 로 보고해 관측 가능하게 둔다.
                sync.newlyCompletedDates.forEach { date ->
                    workoutRepo.updateDayCompletion(sync.plan.id, date, true)
                        .onFailure { it.toAppError().reportToSentry() }
                }
                checkBadges(sync.plan)
            }
            .onFailure {
                val appErr = it.toAppError()
                appErr.reportToSentry()
                _uiState.value = HomeUiState.Error(appErr)
            }
    }

    private fun loadTodayActivity() = viewModelScope.launch {
        val result = getTodayActivity().getOrNull() ?: return@launch
        val current = _uiState.value
        if (current is HomeUiState.Success) {
            _uiState.value = current.copy(
                todayActivity = result.activity,
                hasActivityPermission = result.hasPermission,
            )
        }
    }

    fun refreshActivity() = loadTodayActivity()

    // detekt UnreachableCode 는 `viewModelScope.launch { }` + `?: return@launch` 조합의 제어흐름을
    // 오판해 본문 전체를 unreachable 로 잘못 표시하는 알려진 false positive. 기존엔 baseline 에 문장별로
    // 박제했으나 baseline signature 가 코드 내용을 포함해 본문 수정 시 drift(재노출)된다 → 함수 단위
    // suppress 로 고정해 drift 를 차단한다. (Kotlin 컴파일러는 unreachable 로 보지 않음 — 정상 동작 코드)
    @Suppress("UnreachableCode")
    fun toggleDayCompletion(date: LocalDate) = viewModelScope.launch {
        val current = _uiState.value
        if (current !is HomeUiState.Success) return@launch

        val day = current.plan.days.find { it.date == date } ?: return@launch
        val newCompleted = !day.isCompleted

        // Optimistic update
        val updatedDays = current.plan.days.map {
            if (it.date == date) it.copy(isCompleted = newCompleted) else it
        }
        val updatedPlan = current.plan.copy(days = updatedDays)
        // current.copy 로 todayActivity·hasActivityPermission 등 기존 Success 필드 보존
        // (successWithStats 는 활동 필드를 기본값으로 리셋하므로 토글 시 활동 카드가 사라짐)
        _uiState.value = current.copy(
            plan = updatedPlan,
            completedCount = updatedPlan.days.count { !it.isRestDay && it.isCompleted },
            totalWorkoutDays = updatedPlan.days.count { !it.isRestDay },
        )

        // Server sync
        workoutRepo.updateDayCompletion(current.plan.id, date, newCompleted)
            .onFailure {
                // 실패 시 토글만 되돌린다. current(토글 직전 스냅샷) 전체로 덮으면 그 사이
                // loadTodayActivity 가 채운 todayActivity 가 사라지므로, 활동 필드는 live 상태에서
                // 보존하고 plan/완료 카운트만 revert.
                val live = _uiState.value
                if (live is HomeUiState.Success) {
                    _uiState.value = live.copy(
                        plan = current.plan,
                        completedCount = current.completedCount,
                        totalWorkoutDays = current.totalWorkoutDays,
                    )
                }
                val appErr = it.toAppError()
                appErr.reportToSentry()
                _sideEffect.trySend(HomeSideEffect.ShowSnackbar(appErr.userMessage))
            }
    }
}
