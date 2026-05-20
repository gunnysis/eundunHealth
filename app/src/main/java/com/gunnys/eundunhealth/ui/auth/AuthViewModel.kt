package com.gunnys.eundunhealth.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gunnys.eundunhealth.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthState {
    object Loading : AuthState()
    data class Authenticated(val userId: String, val needsOnboarding: Boolean = false) : AuthState()
    object Unauthenticated : AuthState()
    data class Error(val message: String) : AuthState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val userRepo: UserRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        checkSession()
    }

    private fun checkSession() = viewModelScope.launch {
        try {
            val session = supabaseClient.auth.currentSessionOrNull()
            if (session != null) {
                val userId = session.user?.id ?: ""
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
        try {
            supabaseClient.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            val userId = supabaseClient.auth.currentUserOrNull()?.id ?: ""
            val hasProfile = userRepo.getProfile().getOrNull() != null
            _authState.value = AuthState.Authenticated(userId, needsOnboarding = !hasProfile)
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e.message ?: "로그인에 실패했습니다")
        }
    }

    fun signup(email: String, password: String) = viewModelScope.launch {
        _authState.value = AuthState.Loading
        try {
            supabaseClient.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            val userId = supabaseClient.auth.currentUserOrNull()?.id ?: ""
            _authState.value = AuthState.Authenticated(userId, needsOnboarding = true)
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e.message ?: "회원가입에 실패했습니다")
        }
    }

    fun clearError() {
        if (_authState.value is AuthState.Error) {
            _authState.value = AuthState.Unauthenticated
        }
    }
}
