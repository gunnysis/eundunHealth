package com.gunnys.eundunhealth.ui.navigation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gunnys.eundunhealth.domain.model.CurrentWeekPlanRead
import com.gunnys.eundunhealth.domain.model.GoalType
import com.gunnys.eundunhealth.domain.repository.GoalRepository
import com.gunnys.eundunhealth.domain.repository.UserRepository
import com.gunnys.eundunhealth.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

@Immutable
data class ProgressHubUiState(
    val thisWeekValue: String = "—",
    val thisWeekSubLabel: String = "이번 주 완료",
    val recentFourWeeksValue: String = "—",
    val recentFourWeeksSubLabel: String = "최근 4주 달성률",
    val goalDeltaValue: String = "—",
    val goalDeltaSubLabel: String = "목표 체중까지",
    val isLoading: Boolean = true,
)

@HiltViewModel
class ProgressHubViewModel @Inject constructor(
    private val workoutRepo: WorkoutRepository,
    private val goalRepo: GoalRepository,
    private val userRepo: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProgressHubUiState())
    val uiState: StateFlow<ProgressHubUiState> = _uiState.asStateFlow()

    init {
        loadSummary()
    }

    fun loadSummary() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true)

        val currentWeekDeferred = async { runCatching { workoutRepo.loadCurrentWeekPlan() }.getOrNull() }
        val statsDeferred = async { workoutRepo.getStatistics(weeks = 4).getOrNull() }
        val goalsDeferred = async { goalRepo.getGoals().getOrNull() }
        val profileDeferred = async { userRepo.getProfile().getOrNull() }

        val currentRead = currentWeekDeferred.await()
        val stats = statsDeferred.await()
        val goals = goalsDeferred.await()
        val profile = profileDeferred.await()

        val thisWeekText = if (currentRead is CurrentWeekPlanRead.Found) {
            val workoutDays = currentRead.plan.days.count { !it.isRestDay }
            val completedDays = currentRead.plan.days.count { !it.isRestDay && it.isCompleted }
            if (workoutDays > 0) "$completedDays/${workoutDays}일" else "—"
        } else {
            "—"
        }

        val fourWeekText = if (stats != null && stats.weeklyRates.isNotEmpty()) {
            val avg = stats.weeklyRates.map { it.completionRate }.average()
            "${(avg * 100).toInt()}%"
        } else {
            "—"
        }

        val weightGoal = goals?.firstOrNull { it.type == GoalType.WEIGHT }
        val (goalText, goalSub) = if (weightGoal != null && profile != null) {
            val diff = weightGoal.targetValue - profile.weightKg
            val formatted = if (abs(diff) < 0.05f) {
                "달성!"
            } else if (diff > 0f) {
                "+${"%.1f".format(diff)}kg"
            } else {
                "${"%.1f".format(diff)}kg"
            }
            formatted to "목표 ${"%.1f".format(weightGoal.targetValue)}kg"
        } else if (weightGoal != null) {
            "${"%.1f".format(weightGoal.targetValue)}kg" to "목표 체중"
        } else {
            "—" to "목표 미설정"
        }

        _uiState.value = ProgressHubUiState(
            thisWeekValue = thisWeekText,
            thisWeekSubLabel = "이번 주 완료",
            recentFourWeeksValue = fourWeekText,
            recentFourWeeksSubLabel = "최근 4주 달성률",
            goalDeltaValue = goalText,
            goalDeltaSubLabel = goalSub,
            isLoading = false,
        )
    }
}
