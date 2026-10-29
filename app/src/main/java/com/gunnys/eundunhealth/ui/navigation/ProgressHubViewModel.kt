package com.gunnys.eundunhealth.ui.navigation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gunnys.eundunhealth.domain.model.Badge
import com.gunnys.eundunhealth.domain.model.BadgeCatalog
import com.gunnys.eundunhealth.domain.model.CurrentWeekPlanRead
import com.gunnys.eundunhealth.domain.model.Goal
import com.gunnys.eundunhealth.domain.model.GoalType
import com.gunnys.eundunhealth.domain.model.SmileSummaryStats
import com.gunnys.eundunhealth.domain.model.Statistics
import com.gunnys.eundunhealth.domain.model.UserProfile
import com.gunnys.eundunhealth.domain.model.WorkoutPlanMode
import com.gunnys.eundunhealth.domain.repository.BadgeRepository
import com.gunnys.eundunhealth.domain.repository.GoalRepository
import com.gunnys.eundunhealth.domain.repository.SmileTrainingRepository
import com.gunnys.eundunhealth.domain.repository.UserRepository
import com.gunnys.eundunhealth.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import kotlin.math.abs

@Immutable
data class BadgeMiniPreview(
    val key: String,
    val name: String,
    val earned: Boolean,
)

@Immutable
data class ProgressHubUiState(
    val thisWeekValue: String = "—",
    val thisWeekSubLabel: String = "이번 주 완료",
    val thisWeekCompletedDays: Int = 0,
    val thisWeekWorkoutDays: Int = 0,
    val thisWeekProgress: Float = 0f,
    val thisWeekDayStamps: List<Boolean?> = emptyList(),
    val thisWeekMode: WorkoutPlanMode? = null,
    val recentFourWeeksValue: String = "—",
    val recentFourWeeksSubLabel: String = "최근 4주 달성률",
    val recentFourWeeksRate: Float = 0f,
    val recentWeeklyRates: List<Float> = emptyList(),
    val currentStreakWeeks: Int = 0,
    val longestStreakWeeks: Int = 0,
    val goalDeltaValue: String = "—",
    val goalDeltaSubLabel: String = "목표 체중까지",
    val currentWeightKg: Float? = null,
    val targetWeightKg: Float? = null,
    val currentBodyFatPct: Float? = null,
    val targetBodyFatPct: Float? = null,
    val isWeightGoalAchieved: Boolean = false,
    val earnedBadgeCount: Int = 0,
    val totalBadgeCount: Int = BadgeCatalog.all.size,
    val latestBadgeName: String? = null,
    val badgePreviews: List<BadgeMiniPreview> = emptyList(),
    val smileStats: SmileSummaryStats = SmileSummaryStats(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class ProgressHubViewModel @Inject constructor(
    private val workoutRepo: WorkoutRepository,
    private val goalRepo: GoalRepository,
    private val userRepo: UserRepository,
    private val smileTrainingRepo: SmileTrainingRepository,
    private val badgeRepo: BadgeRepository? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProgressHubUiState())
    val uiState: StateFlow<ProgressHubUiState> = _uiState.asStateFlow()

    init {
        loadSummary()
        observeSmileTrainingStats()
    }

    private fun observeSmileTrainingStats() {
        viewModelScope.launch {
            smileTrainingRepo.observeSummaryStats().collect { stats ->
                _uiState.update { it.copy(smileStats = stats) }
            }
        }
    }

    fun loadSummary() = viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true) }

        val currentWeekDeferred = async { runCatching { workoutRepo.loadCurrentWeekPlan() }.getOrNull() }
        val statsDeferred = async { workoutRepo.getStatistics(weeks = 4).getOrNull() }
        val goalsDeferred = async { goalRepo.getGoals().getOrNull() }
        val profileDeferred = async { userRepo.getProfile().getOrNull() }
        val badgesDeferred = async { badgeRepo?.getEarnedBadges()?.getOrNull() }

        val currentRead = currentWeekDeferred.await()
        val stats = statsDeferred.await()
        val goals = goalsDeferred.await()
        val profile = profileDeferred.await()
        val earnedBadges = badgesDeferred.await()

        _uiState.update { current ->
            buildUpdatedSummaryState(
                current = current,
                currentRead = currentRead,
                stats = stats,
                goals = goals,
                profile = profile,
                earnedBadges = earnedBadges,
            )
        }
    }

    private fun buildUpdatedSummaryState(
        current: ProgressHubUiState,
        currentRead: CurrentWeekPlanRead?,
        stats: Statistics?,
        goals: List<Goal>?,
        profile: UserProfile?,
        earnedBadges: List<Badge>?,
    ): ProgressHubUiState {
        val foundPlan = (currentRead as? CurrentWeekPlanRead.Found)?.plan
        val workoutDays = foundPlan?.days?.count { !it.isRestDay } ?: 0
        val completedDays = foundPlan?.days?.count { !it.isRestDay && it.isCompleted } ?: 0
        val thisWeekProgress = if (workoutDays > 0) {
            (completedDays.toFloat() / workoutDays).coerceIn(0f, 1f)
        } else {
            0f
        }
        val dayStamps = foundPlan?.days?.map { day ->
            if (day.isRestDay) null else day.isCompleted
        }.orEmpty()
        val thisWeekText = if (workoutDays > 0) "$completedDays/${workoutDays}일" else "—"

        val weeklyRates = stats?.weeklyRates?.map { it.completionRate.coerceIn(0f, 1f) }.orEmpty()
        val avgFourWeekRate = if (weeklyRates.isNotEmpty()) {
            weeklyRates.average().toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }
        val fourWeekText = if (weeklyRates.isNotEmpty()) "${(avgFourWeekRate * 100).toInt()}%" else "—"

        val weightGoal = goals?.firstOrNull { it.type == GoalType.WEIGHT }
        val bodyFatGoal = goals?.firstOrNull { it.type == GoalType.BODY_FAT }
        val (goalText, goalSub, isWeightAchieved) = formatWeightGoalDelta(weightGoal, profile)

        val earnedKeys = earnedBadges?.map { it.key }?.toSet().orEmpty()
        val badgePreviews = BadgeCatalog.all.map { template ->
            BadgeMiniPreview(
                key = template.key,
                name = template.name,
                earned = template.key in earnedKeys,
            )
        }
        val latestBadge = earnedBadges
            ?.maxByOrNull { it.earnedAt ?: Instant.EPOCH }
            ?.let { BadgeCatalog.getInfo(it.key).first }

        return current.copy(
            thisWeekValue = thisWeekText,
            thisWeekSubLabel = "이번 주 완료",
            thisWeekCompletedDays = completedDays,
            thisWeekWorkoutDays = workoutDays,
            thisWeekProgress = thisWeekProgress,
            thisWeekDayStamps = dayStamps,
            thisWeekMode = foundPlan?.mode,
            recentFourWeeksValue = fourWeekText,
            recentFourWeeksSubLabel = "최근 4주 달성률",
            recentFourWeeksRate = avgFourWeekRate,
            recentWeeklyRates = weeklyRates,
            currentStreakWeeks = stats?.currentStreak ?: 0,
            longestStreakWeeks = stats?.longestStreak ?: 0,
            goalDeltaValue = goalText,
            goalDeltaSubLabel = goalSub,
            currentWeightKg = profile?.weightKg,
            targetWeightKg = weightGoal?.targetValue,
            currentBodyFatPct = profile?.bodyFatPercent,
            targetBodyFatPct = bodyFatGoal?.targetValue,
            isWeightGoalAchieved = isWeightAchieved,
            earnedBadgeCount = earnedBadges?.size ?: 0,
            totalBadgeCount = BadgeCatalog.all.size,
            latestBadgeName = latestBadge,
            badgePreviews = badgePreviews,
            isLoading = false,
        )
    }

    private fun formatWeightGoalDelta(
        weightGoal: Goal?,
        profile: UserProfile?,
    ): Triple<String, String, Boolean> {
        if (weightGoal == null) {
            return Triple("—", "목표 미설정", false)
        }
        if (profile == null) {
            return Triple("${"%.1f".format(weightGoal.targetValue)}kg", "목표 체중", false)
        }
        val diff = weightGoal.targetValue - profile.weightKg
        val isAchieved = abs(diff) < 0.05f
        val formatted = when {
            isAchieved -> "달성!"
            diff > 0f -> "+${"%.1f".format(diff)}kg"
            else -> "${"%.1f".format(diff)}kg"
        }
        return Triple(formatted, "목표 ${"%.1f".format(weightGoal.targetValue)}kg", isAchieved)
    }
}
