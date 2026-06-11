package com.gunnys.eundunhealth.ui.auth

import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 이메일 확인 재발송 + 60초 쿨다운. Login/SignupViewModel 이 합성으로 공유.
 * scope 는 viewModelScope 주입(독립 테스트 시 TestScope).
 */
class ResendConfirmationController(
    private val authRepo: AuthRepository,
    private val scope: CoroutineScope,
) {
    private val _cooldownSec = MutableStateFlow(0)
    val cooldownSec: StateFlow<Int> = _cooldownSec.asStateFlow()

    private val _error = MutableStateFlow<AppError?>(null)
    val error: StateFlow<AppError?> = _error.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    fun resend(email: String) {
        scope.launch {
            if (_cooldownSec.value > 0) return@launch
            authRepo.resendConfirmation(email)
                .onSuccess {
                    _cooldownSec.value = 60
                    while (_cooldownSec.value > 0) {
                        delay(1_000)
                        _cooldownSec.value = (_cooldownSec.value - 1).coerceAtLeast(0)
                    }
                }
                .onFailure { _error.value = it.toAppErrorReporting() }
        }
    }
}
