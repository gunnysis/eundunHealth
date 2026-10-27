package com.gunnys.eundunhealth.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gunnys.eundunhealth.data.preferences.ThemePreferences
import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.model.CurrentWeekPlanRead
import com.gunnys.eundunhealth.domain.model.PlanSource
import com.gunnys.eundunhealth.domain.model.WeeklyPlan
import com.gunnys.eundunhealth.domain.model.WorkoutPlanMode
import com.gunnys.eundunhealth.domain.model.toReportedAppError
import com.gunnys.eundunhealth.domain.repository.WorkoutRepository
import com.gunnys.eundunhealth.domain.usecase.CheckAndAwardBadgesUseCase
import com.gunnys.eundunhealth.domain.usecase.CreateWeeklyPlanUseCase
import com.gunnys.eundunhealth.domain.usecase.HealthSyncResult
import com.gunnys.eundunhealth.domain.usecase.LoadCurrentWeekPlanUseCase
import com.gunnys.eundunhealth.domain.usecase.LoadDefaultWorkoutPlanModeUseCase
import com.gunnys.eundunhealth.domain.usecase.SyncHealthDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

private val KST_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

@Immutable
sealed class HomeUiState {
    @Immutable
    data object Loading : HomeUiState()

    @Immutable
    data class NoPlan(
        val selectedMode: WorkoutPlanMode? = null,
        val createError: AppError? = null,
    ) : HomeUiState()

    @Immutable
    data class Creating(
        val selectedMode: WorkoutPlanMode,
    ) : HomeUiState()

    @Immutable
    data class AutoCreating(
        val mode: WorkoutPlanMode,
    ) : HomeUiState()

    @Immutable
    data class AutoCreateError(
        val mode: WorkoutPlanMode,
        val error: AppError,
    ) : HomeUiState()

    @Immutable
    data class PlanDataError(
        val source: PlanSource,
        val resetError: AppError? = null,
        val isResetting: Boolean = false,
    ) : HomeUiState()

    @Immutable
    data class Success(
        val plan: WeeklyPlan,
        val isHealthConnectAvailable: Boolean = true,
        val hasHealthPermission: Boolean = false,
        val completedCount: Int = 0,
        val totalWorkoutDays: Int = 0,
        val today: LocalDate = LocalDate.now(KST_ZONE),
        val toggleError: AppError? = null,
    ) : HomeUiState() {
        val completionRate: Float get() = if (totalWorkoutDays > 0) completedCount.toFloat() / totalWorkoutDays else 0f
    }

    @Immutable
    data class Error(val error: AppError) : HomeUiState()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val loadCurrentWeekPlan: LoadCurrentWeekPlanUseCase,
    private val loadDefaultWorkoutPlanMode: LoadDefaultWorkoutPlanModeUseCase,
    private val createWeeklyPlan: CreateWeeklyPlanUseCase,
    private val themePreferences: ThemePreferences,
    private val syncHealth: SyncHealthDataUseCase,
    private val checkBadges: CheckAndAwardBadgesUseCase,
    private val workoutRepo: WorkoutRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // 같은 날짜의 토글 전송을 직렬화 — 빠른 체크/해제 시 이전 전송을 취소해 최신 의도만 서버에 반영.
    private val toggleJobs = mutableMapOf<LocalDate, Job>()

    init {
        loadPlan()
    }

    private fun currentWeekStart(): LocalDate = LocalDate.now(KST_ZONE).with(DayOfWeek.MONDAY)

    private fun successWithStats(plan: WeeklyPlan, isAvailable: Boolean, hasPermission: Boolean) = HomeUiState.Success(
        plan = plan,
        isHealthConnectAvailable = isAvailable,
        hasHealthPermission = hasPermission,
        completedCount = plan.days.count { !it.isRestDay && it.isCompleted },
        totalWorkoutDays = plan.days.count { !it.isRestDay },
        today = LocalDate.now(KST_ZONE),
    )

    private suspend fun onPlanReady(plan: WeeklyPlan) {
        themePreferences.clearAutoCreateAttempt()
        val sync = syncHealth(plan).getOrElse {
            HealthSyncResult(plan, isAvailable = false, hasPermission = false, newlyCompletedDates = emptyList())
        }

        // 렌더 먼저 — 서버 푸시/배지 적립을 기다리지 않고 즉시 화면을 그린다.
        _uiState.value = successWithStats(sync.plan, sync.isAvailable, sync.hasPermission)

        // Health Connect 가 새로 감지한 완료만 서버에 반영 (백그라운드).
        sync.newlyCompletedDates.forEach { date ->
            // manual=false: HC 자동완료는 manuallySet 을 남기지 않아 이후 사용자 수동 해제가 가능.
            workoutRepo.updateDayCompletion(sync.plan.id, date, true, manual = false)
                .onFailure { it.toReportedAppError() }
        }
        checkBadges(sync.plan)
    }

    fun loadPlan() {
        val current = _uiState.value
        if (current is HomeUiState.Creating || current is HomeUiState.AutoCreating) return
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            when (val read = loadCurrentWeekPlan()) {
                is CurrentWeekPlanRead.Found -> {
                    onPlanReady(read.plan)
                }
                CurrentWeekPlanRead.Missing -> {
                    handleMissingPlan()
                }
                is CurrentWeekPlanRead.Unreadable -> {
                    Sentry.addBreadcrumb(
                        Breadcrumb().apply {
                            category = "plan_read_unreadable"
                            level = SentryLevel.WARNING
                            setData("source", read.source.name)
                        },
                    )
                    read.error?.toReportedAppError()
                    _uiState.value = HomeUiState.PlanDataError(source = read.source)
                }
                is CurrentWeekPlanRead.Failed -> {
                    val appErr = read.error.toReportedAppError()
                    _uiState.value = HomeUiState.Error(appErr)
                }
            }
        }
    }

    private suspend fun handleMissingPlan() {
        val defaultMode = loadDefaultWorkoutPlanMode()
        if (defaultMode == null) {
            _uiState.value = HomeUiState.NoPlan()
            return
        }
        val weekStart = currentWeekStart()
        if (themePreferences.hasAutoCreateAttempted(weekStart)) {
            Sentry.addBreadcrumb(
                Breadcrumb().apply {
                    category = "plan_auto_attempt_suppressed"
                    level = SentryLevel.INFO
                    setData("weekStart", weekStart.toString())
                    setData("mode", defaultMode.name)
                },
            )
            _uiState.value = HomeUiState.AutoCreateError(
                mode = defaultMode,
                error = AppError.Server(
                    code = 0,
                    userMessage = "이전 자동 생성 시도가 완료되지 않았습니다. 아래 버튼을 눌러 다시 시도해주세요",
                ),
            )
            return
        }
        executeAutoCreate(defaultMode, weekStart)
    }

    private suspend fun executeAutoCreate(mode: WorkoutPlanMode, weekStart: LocalDate) {
        _uiState.value = HomeUiState.AutoCreating(mode)
        themePreferences.markAutoCreateAttempted(weekStart)
        createWeeklyPlan(mode)
            .onSuccess { plan ->
                onPlanReady(plan)
            }
            .onFailure { err ->
                val appErr = err.toReportedAppError()
                _uiState.value = HomeUiState.AutoCreateError(mode = mode, error = appErr)
            }
    }

    fun selectMode(mode: WorkoutPlanMode) {
        val current = _uiState.value
        if (current is HomeUiState.NoPlan) {
            _uiState.value = current.copy(selectedMode = mode, createError = null)
        }
    }

    fun createSelectedPlan() {
        val current = _uiState.value
        val mode = (current as? HomeUiState.NoPlan)?.selectedMode ?: return
        _uiState.value = HomeUiState.Creating(selectedMode = mode)
        viewModelScope.launch {
            createWeeklyPlan(mode)
                .onSuccess { plan ->
                    onPlanReady(plan)
                }
                .onFailure { err ->
                    val appErr = err.toReportedAppError()
                    _uiState.value = HomeUiState.NoPlan(selectedMode = mode, createError = appErr)
                }
        }
    }

    fun retryAutoCreate() {
        val current = _uiState.value
        if (current !is HomeUiState.AutoCreateError) return
        viewModelScope.launch {
            val mode = loadDefaultWorkoutPlanMode() ?: current.mode
            executeAutoCreate(mode, currentWeekStart())
        }
    }

    fun resetCorruptedPlan() {
        val current = _uiState.value
        if (current !is HomeUiState.PlanDataError || current.isResetting) return
        _uiState.value = current.copy(isResetting = true, resetError = null)
        viewModelScope.launch {
            workoutRepo.deleteCurrentWeekPlan()
                .onSuccess {
                    themePreferences.clearAutoCreateAttempt()
                    handleMissingPlan()
                }
                .onFailure { err ->
                    val appErr = err.toReportedAppError()
                    _uiState.value = current.copy(isResetting = false, resetError = appErr)
                }
        }
    }

    // detekt UnreachableCode 는 `?: return` + 람다/`viewModelScope.launch` 조합의 제어흐름을 오판해
    // 본문을 unreachable 로 잘못 표시하는 알려진 false positive. Kotlin 컴파일러는 정상으로 보며
    // (단위테스트로 동작 검증됨) 함수 단위 suppress 로 고정한다.
    @Suppress("UnreachableCode")
    fun toggleDayCompletion(date: LocalDate) {
        val current = _uiState.value
        if (current !is HomeUiState.Success) return

        val day = current.plan.days.find { it.date == date } ?: return
        val newCompleted = !day.isCompleted

        // Optimistic update + 수동 표시(manuallySet=true) → 이후 HC 자동완료가 이 날을 덮지 않음.
        val updatedDays = current.plan.days.map {
            if (it.date == date) it.copy(isCompleted = newCompleted, manuallySet = true) else it
        }
        val updatedPlan = current.plan.copy(days = updatedDays)
        // current.copy 로 isHealthConnectAvailable·hasHealthPermission·today 등 기존 Success 필드 보존
        _uiState.value = current.copy(
            plan = updatedPlan,
            completedCount = updatedPlan.days.count { !it.isRestDay && it.isCompleted },
            totalWorkoutDays = updatedPlan.days.count { !it.isRestDay },
            toggleError = null,
        )

        // 같은 날짜의 직전 전송을 취소하고 최신 의도만 전송 → 빠른 체크/해제 시 마지막 액션이 서버에 반영.
        toggleJobs[date]?.cancel()
        toggleJobs[date] = viewModelScope.launch {
            workoutRepo.updateDayCompletion(current.plan.id, date, newCompleted)
                .onFailure {
                    // 실패 시 plan/완료 카운트만 current(토글 직전 스냅샷)로 revert.
                    val live = _uiState.value
                    val appErr = it.toReportedAppError()
                    if (live is HomeUiState.Success) {
                        _uiState.value = live.copy(
                            plan = current.plan,
                            completedCount = current.completedCount,
                            totalWorkoutDays = current.totalWorkoutDays,
                            toggleError = appErr,
                        )
                    }
                }
        }
    }
}
