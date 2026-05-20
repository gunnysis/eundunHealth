package com.gunnys.eundunhealth.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gunnys.eundunhealth.domain.model.UserProfile
import com.gunnys.eundunhealth.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userRepo: UserRepository,
    private val supabaseClient: SupabaseClient
) : ViewModel() {

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun saveProfile(heightCm: Float, weightKg: Float, bodyFatPct: Float, muscleMassKg: Float) =
        viewModelScope.launch {
            _isLoading.value = true
            val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return@launch
            userRepo.saveProfile(
                UserProfile(userId, heightCm, weightKg, bodyFatPct, muscleMassKg)
            )
            _isLoading.value = false
            _saved.value = true
        }
}
