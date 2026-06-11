package com.gunnys.eundunhealth.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class UserProfileTest {
    @Test
    fun `bmi above 30 returns BEGINNER`() {
        val profile = UserProfile("u1", 170f, 90f, 35f, 30f)
        assertEquals(FitnessLevel.BEGINNER, profile.fitnessLevel)
    }

    @Test
    fun `normal body fat returns ADVANCED`() {
        val profile = UserProfile("u1", 175f, 70f, 15f, 35f)
        assertEquals(FitnessLevel.ADVANCED, profile.fitnessLevel)
    }

    @Test
    fun `intermediate body fat returns INTERMEDIATE`() {
        val profile = UserProfile("u1", 175f, 75f, 25f, 30f)
        assertEquals(FitnessLevel.INTERMEDIATE, profile.fitnessLevel)
    }

    @Test
    fun `bmi calculated correctly`() {
        val profile = UserProfile("u1", 170f, 70f, 20f, 30f)
        val expected = 70f / (1.7f * 1.7f)
        assertEquals(expected, profile.bmi, 0.1f)
    }

    // --- nullable bodyFatPercent tests ---

    private fun profile(bodyFat: Float?, weight: Float = 70f, height: Float = 175f) = UserProfile("u", height, weight, bodyFat, null)

    @Test
    fun `bodyFat null이면 BMI 기준으로 판정 — 정상 BMI는 ADVANCED`() {
        // 175cm/70kg → BMI 22.9 (≤25). bodyFat null → BMI 단독.
        assertEquals(FitnessLevel.ADVANCED, profile(bodyFat = null).fitnessLevel)
    }

    @Test
    fun `bodyFat null이어도 비만 BMI면 BEGINNER`() {
        // 175cm/95kg → BMI 31 (>30)
        assertEquals(FitnessLevel.BEGINNER, profile(bodyFat = null, weight = 95f).fitnessLevel)
    }

    @Test
    fun `bodyFat 높으면 BEGINNER`() {
        assertEquals(FitnessLevel.BEGINNER, profile(bodyFat = 35f).fitnessLevel)
    }
}
