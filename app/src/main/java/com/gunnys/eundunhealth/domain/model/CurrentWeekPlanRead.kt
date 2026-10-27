package com.gunnys.eundunhealth.domain.model

enum class PlanSource { REMOTE, CACHE }

sealed interface CurrentWeekPlanRead {
    data class Found(val plan: WeeklyPlan) : CurrentWeekPlanRead

    data object Missing : CurrentWeekPlanRead

    data class Unreadable(val source: PlanSource, val error: Throwable? = null) : CurrentWeekPlanRead

    data class Failed(val error: Throwable) : CurrentWeekPlanRead
}

class WeeklyPlanAlreadyExistsException : IllegalStateException("이미 이번 주 운동 계획이 있습니다")
