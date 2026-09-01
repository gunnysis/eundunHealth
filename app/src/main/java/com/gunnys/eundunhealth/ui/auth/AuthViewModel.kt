package com.gunnys.eundunhealth.ui.auth

import android.app.Activity
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.repository.AuthCancelledException
import com.gunnys.eundunhealth.domain.repository.AuthRepository
import com.gunnys.eundunhealth.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
sealed class SessionState {
    data object Unknown : SessionState()
    data class Authenticated(val userId: String, val needsOnboarding: Boolean = false) : SessionState()
    data object Unauthenticated : SessionState()
}

/**
 * 인증 게이트 화면의 렌더 상태.
 *
 * 설계 §5.3 은 `Launching`(브라우저 여는 중)과 `AwaitingReturn`(브라우저 체류 중)을 나눴으나
 * **하나로 합쳤다**. MSAL 은 "Custom Tab 이 실제로 표시됐다" 는 콜백을 주지 않아 두 상태를
 * 구분할 근거가 런타임에 없고, 두 상태의 UI(스피너 + CTA 비활성)도 동일하다. 나누면 전이가
 * 일어나지 않는 죽은 상태가 하나 생긴다. ViewModel 이 살아있는 한 이 상태는 백그라운드 왕복을
 * 넘어 유지되므로 설계가 노린 "복귀 시 깜빡임 방지" 는 그대로 달성된다.
 */
@Immutable
sealed interface AuthGateUiState {
    data object Idle : AuthGateUiState
    data object Authenticating : AuthGateUiState
    data class Failed(val error: AppError) : AuthGateUiState
}

/**
 * 앱 전역 세션 관리 + 인증 게이트 ViewModel.
 *
 * 브라우저 위임 전환으로 입력 검증·재발송·비밀번호 재설정이 전부 Entra 호스팅 페이지로
 * 넘어가면서 per-screen 로직이 소멸했다. 그래서 `LoginViewModel`/`SignupViewModel`/
 * `ForgotPasswordViewModel` 3종을 폐기하고 여기로 통합했다(설계 §5.6, 룰 11 항목 5 갱신).
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val userRepo: UserRepository,
) : ViewModel() {

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Unknown)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _uiState = MutableStateFlow<AuthGateUiState>(AuthGateUiState.Idle)
    val uiState: StateFlow<AuthGateUiState> = _uiState.asStateFlow()

    init {
        checkSession()
    }

    private fun checkSession() = viewModelScope.launch {
        runCatching {
            val userId = authRepo.restoreSession()
            if (userId != null) {
                val hasProfile = userRepo.getProfile().getOrNull() != null
                SessionState.Authenticated(userId, needsOnboarding = !hasProfile)
            } else {
                SessionState.Unauthenticated
            }
        }
            .onSuccess { session -> _sessionState.value = session }
            .onFailure { _sessionState.value = SessionState.Unauthenticated }
    }

    /**
     * 브라우저를 띄워 인증한다. 성공하면 세션 상태가 전환되고 [AppNavigation] 이 이동시킨다.
     *
     * 중복 탭 차단: 이미 진행 중이면 무시한다 — MSAL 은 대화형 요청이 겹치면 예외를 던진다.
     */
    fun authenticate(activity: Activity) {
        if (_uiState.value is AuthGateUiState.Authenticating) return
        _uiState.value = AuthGateUiState.Authenticating

        viewModelScope.launch {
            authRepo.authenticate(activity)
                .onSuccess { userId ->
                    val hasProfile = userRepo.getProfile().getOrNull() != null
                    _uiState.value = AuthGateUiState.Idle
                    _sessionState.value =
                        SessionState.Authenticated(userId, needsOnboarding = !hasProfile)
                }
                .onFailure { e ->
                    // 사용자가 브라우저를 닫은 것은 실패가 아니다 — 배너 없이 조용히 복귀(설계 §5.3).
                    _uiState.value = if (e is AuthCancelledException) {
                        AuthGateUiState.Idle
                    } else {
                        AuthGateUiState.Failed(e.toAppErrorReporting())
                    }
                }
        }
    }

    fun dismissError() {
        if (_uiState.value is AuthGateUiState.Failed) {
            _uiState.value = AuthGateUiState.Idle
        }
    }

    fun logout() = viewModelScope.launch {
        authRepo.signOut()
        _uiState.value = AuthGateUiState.Idle
        _sessionState.value = SessionState.Unauthenticated
    }

    /** 계정 삭제 등 외부 경로에서 세션이 끝났을 때 게이트로 되돌린다. */
    fun onSessionEnded() {
        _uiState.value = AuthGateUiState.Idle
        _sessionState.value = SessionState.Unauthenticated
    }
}
