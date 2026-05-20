package com.gunnys.eundunhealth.domain.model

object BadgeCatalog {
    data class BadgeTemplate(val key: String, val name: String, val description: String)

    val all = listOf(
        BadgeTemplate(BadgeKeys.WEEK_1_COMPLETE, "1주 완료", "첫 번째 주간 목표를 달성했습니다"),
        BadgeTemplate(BadgeKeys.WEEK_2_COMPLETE, "2주 연속", "2주 연속 목표를 달성했습니다"),
        BadgeTemplate(BadgeKeys.STREAK_3_WEEKS, "3주 연속", "3주 연속 목표를 달성했습니다")
    )

    fun getInfo(key: String): Pair<String, String> {
        val template = all.find { it.key == key }
        return (template?.name ?: key) to (template?.description ?: "")
    }
}
