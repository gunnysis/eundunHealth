package com.gunnys.eundunhealth.domain.model

object BadgeCatalog {
    data class BadgeTemplate(val key: String, val name: String, val description: String)

    val all = listOf(
        // v0.1
        BadgeTemplate(BadgeKeys.WEEK_1_COMPLETE, "1주 완료", "첫 번째 주간 목표를 달성했습니다"),
        BadgeTemplate(BadgeKeys.WEEK_2_COMPLETE, "2주 연속", "2주 연속 목표를 달성했습니다"),
        BadgeTemplate(BadgeKeys.STREAK_3_WEEKS, "3주 연속", "3주 연속 목표를 달성했습니다"),
        // v0.3 마일스톤
        BadgeTemplate(BadgeKeys.FIRST_WORKOUT, "첫 운동", "처음 운동을 완료했습니다"),
        BadgeTemplate(BadgeKeys.WORKOUTS_10, "10회 운동", "총 10일치 운동을 완료했습니다"),
        BadgeTemplate(BadgeKeys.WORKOUTS_50, "50회 운동", "총 50일치 운동을 완료했습니다"),
        BadgeTemplate(BadgeKeys.STREAK_8_WEEKS, "8주 연속", "8주 연속 목표를 달성했습니다"),
        // v0.3 목표 달성
        BadgeTemplate(BadgeKeys.GOAL_WEIGHT_ACHIEVED, "체중 목표 달성", "설정한 체중 목표에 도달했습니다"),
        BadgeTemplate(BadgeKeys.GOAL_BODY_FAT_ACHIEVED, "체지방 목표 달성", "설정한 체지방률 목표에 도달했습니다"),
    )

    fun getInfo(key: String): Pair<String, String> {
        val template = all.find { it.key == key }
        return (template?.name ?: key) to (template?.description ?: "")
    }
}
