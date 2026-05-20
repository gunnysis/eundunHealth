package com.gunnys.eundunhealth.ui.badge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gunnys.eundunhealth.domain.model.Badge
import com.gunnys.eundunhealth.domain.model.BadgeKeys
import com.gunnys.eundunhealth.domain.repository.BadgeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

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

    private val allBadgeTemplates = listOf(
        Triple(BadgeKeys.WEEK_1_COMPLETE, "1주 완료", "첫 번째 주간 목표를 달성했습니다"),
        Triple(BadgeKeys.WEEK_2_COMPLETE, "2주 연속", "2주 연속 목표를 달성했습니다"),
        Triple(BadgeKeys.STREAK_3_WEEKS, "3주 연속", "3주 연속 목표를 달성했습니다")
    )

    init {
        loadBadges()
    }

    private fun loadBadges() = viewModelScope.launch {
        val earned = badgeRepo.getEarnedBadges().getOrElse { emptyList() }
        _badges.value = allBadgeTemplates.map { (key, name, desc) ->
            BadgeDisplayItem(key, name, desc, earned = earned.any { it.key == key })
        }
    }
}
