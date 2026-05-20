package com.gunnys.eundunhealth.ui.badge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.Immutable
import com.gunnys.eundunhealth.domain.model.Badge
import com.gunnys.eundunhealth.domain.model.BadgeCatalog
import com.gunnys.eundunhealth.domain.model.BadgeKeys
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
    val earned: Boolean
)

@HiltViewModel
class BadgeViewModel @Inject constructor(
    private val badgeRepo: BadgeRepository
) : ViewModel() {

    private val _badges = MutableStateFlow<List<BadgeDisplayItem>>(emptyList())
    val badges: StateFlow<List<BadgeDisplayItem>> = _badges.asStateFlow()

    init {
        loadBadges()
    }

    private fun loadBadges() = viewModelScope.launch {
        val earned = badgeRepo.getEarnedBadges().getOrElse { emptyList() }
        _badges.value = BadgeCatalog.all.map { template ->
            val (name, desc) = BadgeCatalog.getInfo(template.key)
            BadgeDisplayItem(template.key, name, desc, earned = earned.any { it.key == template.key })
        }
    }
}
