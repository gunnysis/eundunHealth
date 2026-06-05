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
    val earnedAt: String? = null,
)

@Immutable
sealed class BadgeUiState {
    @Immutable data object Loading : BadgeUiState()

    @Immutable data class Loaded(val badges: List<BadgeDisplayItem>) : BadgeUiState()

    @Immutable data object Empty : BadgeUiState()

    @Immutable data class Error(val error: AppError) : BadgeUiState()
}

@HiltViewModel
class BadgeViewModel @Inject constructor(
    private val badgeRepo: BadgeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<BadgeUiState>(BadgeUiState.Loading)
    val uiState: StateFlow<BadgeUiState> = _uiState.asStateFlow()

    init {
        loadBadges()
    }

    fun loadBadges() = viewModelScope.launch {
        _uiState.value = BadgeUiState.Loading
        badgeRepo.getEarnedBadges()
            .onSuccess { earned ->
                val items = BadgeCatalog.all.map { template ->
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
                        },
                    )
                }
                _uiState.value = if (items.isEmpty()) {
                    BadgeUiState.Empty
                } else {
                    BadgeUiState.Loaded(items)
                }
            }
            .onFailure {
                val appErr = it.toAppError()
                appErr.reportToSentry()
                _uiState.value = BadgeUiState.Error(appErr)
            }
    }
}
