package com.gunnys.eundunhealth.ui.onboarding

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.model.DEFAULT_REST_DAY
import com.gunnys.eundunhealth.domain.model.Gender
import com.gunnys.eundunhealth.domain.model.UserProfile
import com.gunnys.eundunhealth.domain.model.WorkoutPlanMode
import com.gunnys.eundunhealth.domain.model.normalizeRestDays
import com.gunnys.eundunhealth.domain.model.toReportedAppError
import com.gunnys.eundunhealth.domain.repository.AuthRepository
import com.gunnys.eundunhealth.domain.repository.UserRepository
import com.gunnys.eundunhealth.domain.usecase.CreateWeeklyPlanUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class OnboardingUiState(
    val isLoading: Boolean = false,
    val error: AppError? = null,
)

@Immutable
sealed class OnboardingSideEffect {
    data object NavigateToHome : OnboardingSideEffect()
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userRepo: UserRepository,
    private val authRepo: AuthRepository,
    private val createWeeklyPlan: CreateWeeklyPlanUseCase? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _sideEffect = Channel<OnboardingSideEffect>(Channel.BUFFERED)
    val sideEffect = _sideEffect.receiveAsFlow()

    @Suppress("LongParameterList")
    fun saveProfile(
        heightCm: Float,
        weightKg: Float,
        gender: Gender,
        bodyFatPct: Float? = null,
        muscleMassKg: Float? = null,
        restDays: List<Int> = listOf(DEFAULT_REST_DAY),
        selectedMode: WorkoutPlanMode = WorkoutPlanMode.HOME,
        saveAsDefaultMode: Boolean = true,
    ) = viewModelScope.launch {
        _uiState.value = OnboardingUiState(isLoading = true, error = null)
        val userId = authRepo.getCurrentUserId()
        if (userId == null) {
            _uiState.value = OnboardingUiState(isLoading = false, error = AppError.Auth("로그인이 필요합니다"))
            return@launch
        }
        val defaultMode = if (saveAsDefaultMode) selectedMode else null
        runCatching {
            userRepo.saveProfile(
                UserProfile(
                    userId = userId,
                    heightCm = heightCm,
                    weightKg = weightKg,
                    gender = gender,
                    bodyFatPercent = bodyFatPct,
                    muscleMassKg = muscleMassKg,
                    restDays = normalizeRestDays(restDays),
                    defaultWorkoutPlanMode = defaultMode,
                ),
            ).getOrThrow()
        }
            .onSuccess {
                // 프로필 저장 성공 시 첫 주 계획도 즉시 생성을 시도한다.
                // 계획 생성이 일시적 네트워크 오류로 실패해도 홈 화면(NoPlan/AutoCreate)에서 안전하게 이어받는다.
                if (createWeeklyPlan != null) {
                    createWeeklyPlan.invoke(selectedMode)
                        .onFailure { it.toReportedAppError() }
                }
                _sideEffect.send(OnboardingSideEffect.NavigateToHome)
            }
            .onFailure {
                _uiState.value = OnboardingUiState(isLoading = false, error = it.toReportedAppError())
                return@launch
            }
        _uiState.value = OnboardingUiState(isLoading = false)
    }
}
