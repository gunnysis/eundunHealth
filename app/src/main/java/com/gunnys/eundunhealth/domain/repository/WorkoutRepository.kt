package com.gunnys.eundunhealth.domain.repository

import com.gunnys.eundunhealth.domain.model.Exercise
import com.gunnys.eundunhealth.domain.model.Statistics
import com.gunnys.eundunhealth.domain.model.UserProfile
import com.gunnys.eundunhealth.domain.model.WeeklyPlan
import java.time.LocalDate

interface WorkoutRepository {
    suspend fun getCurrentWeekPlan(): Result<WeeklyPlan?>

    /** 현재 주 운동 1개를 id 로 조회. Room 캐시 우선(네트워크 없이 즉시), 미스 시 네트워크 폴백. */
    suspend fun getExerciseById(exerciseId: String): Result<Exercise?>

    suspend fun createWeeklyPlan(profile: UserProfile): Result<WeeklyPlan>
    suspend fun updateDayCompletion(planId: String, date: LocalDate, completed: Boolean): Result<Unit>
    suspend fun getHistory(page: Int, size: Int): Result<Pair<List<WeeklyPlan>, Int>>
    suspend fun getStatistics(weeks: Int): Result<Statistics>
}
