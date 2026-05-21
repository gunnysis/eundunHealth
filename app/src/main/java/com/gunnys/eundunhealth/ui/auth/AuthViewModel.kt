package com.gunnys.eundunhealth.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gunnys.eundunhealth.domain.repository.AuthRepository
import com.gunnys.eundunhealth.domain.repository.UserRepository
import androidx.compose.runtime.Immutable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthState {
    @Immutable
    data object Loading : AuthState()
    @Immutable
    data class Authenticated(val userId: String, val needsOnboarding: Boolean = false) : AuthState()
    @Immutable
    data object Unauthenticated : AuthState()
    @Immutable
    data class Error(val message: String) : AuthState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val userRepo: UserRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        checkSession()
    }

    private fun checkSession() = viewModelScope.launch {
        try {
            val userId = authRepo.restoreSession()
            if (userId != null) {
                val hasProfile = userRepo.getProfile().getOrNull() != null
                _authState.value = AuthState.Authenticated(userId, needsOnboarding = !hasProfile)
            } else {
                _authState.value = AuthState.Unauthenticated
            }
        } catch (e: Exception) {
            _authState.value = AuthState.Unauthenticated
        }
    }

    fun login(email: String, password: String) = viewModelScope.launch {
        _authState.value = AuthState.Loading
        authRepo.signIn(email, password)
            .onSuccess { userId ->
                val hasProfile = userRepo.getProfile().getOrNull() != null
                _authState.value = AuthState.Authenticated(userId, needsOnboarding = !hasProfile)
            }
            .onFailure {
                _authState.value = AuthState.Error(it.message ?: "로그인에 실패했습니다")
            }
    }

    fun signup(email: String, password: String) = viewModelScope.launch {
        _authState.value = AuthState.Loading
        authRepo.signUp(email, password)
            .onSuccess { userId ->
                _authState.value = AuthState.Authenticated(userId, needsOnboarding = true)
            }
            .onFailure {
                _authState.value = AuthState.Error(it.message ?: "회원가입에 실패했습니다")
            }
    }

    fun logout() = viewModelScope.launch {
        authRepo.signOut()
        _authState.value = AuthState.Unauthenticated
    }

    fun clearError() {
        if (_authState.value is AuthState.Error) {
            _authState.value = AuthState.Unauthenticated
        }
    }
}
