package com.gunnys.eundunhealth.ui.badge

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.model.BadgeCatalog
import com.gunnys.eundunhealth.domain.model.reportToSentry
import com.gunnys.eundunhealth.domain.model.toAppError
import com.gunnys.eundunhealth.domain.repository.BadgeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class BadgeDisplayItem(
    val key: String,
    val name: String,
    val description: String,
    val earned: Boolean,
    val earnedAt: String? = null
)

@HiltViewModel
class BadgeViewModel @Inject constructor(
    private val badgeRepo: BadgeRepository
) : ViewModel() {

    private val _badges = MutableStateFlow<List<BadgeDisplayItem>>(emptyList())
    val badges: StateFlow<List<BadgeDisplayItem>> = _badges.asStateFlow()

    private val _error = MutableStateFlow<AppError?>(null)
    val error: StateFlow<AppError?> = _error.asStateFlow()

    fun clearError() { _error.value = null }

    init {
        loadBadges()
    }

    fun loadBadges() = viewModelScope.launch {
        badgeRepo.getEarnedBadges()
            .onSuccess { earned ->
                _badges.value = BadgeCatalog.all.map { template ->
                    val (name, desc) = BadgeCatalog.getInfo(template.key)
                    val earnedBadge = earned.find { it.key == template.key }
                    BadgeDisplayItem(
                        key = template.key,
                        name = name,
                        description = desc,
                        earned = earnedBadge != null,
                        earnedAt = earnedBadge?.earnedAt?.let {
                            java.time.LocalDateTime.ofInstant(it, java.time.ZoneId.systemDefault())
                                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy.M.d"))
                        }
                    )
                }
            }
            .onFailure {
                val appErr = it.toAppError()
                appErr.reportToSentry()
                _error.value = appErr
            }
    }
}
