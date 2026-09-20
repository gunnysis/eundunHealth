package com.gunnys.eundunhealth.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class UserProfileTest {
    @Test
    fun `bmi is calculated correctly`() {
        val profile = UserProfile(
            userId = "test_user",
            heightCm = 175f,
            weightKg = 70f,
            gender = Gender.MALE,
            bodyFatPercent = 15f,
            muscleMassKg = 35f,
        )
        assertEquals(22.857143f, profile.bmi)
    }

    @Test
    fun `fitnessLevel is BEGINNER if bodyFatPercent is high`() {
        val profile = UserProfile(
            userId = "test_user",
            heightCm = 175f,
            weightKg = 70f,
            gender = Gender.MALE,
            bodyFatPercent = 35f,
            muscleMassKg = 30f,
        )
        assertEquals(FitnessLevel.BEGINNER, profile.fitnessLevel)
    }

    @Test
    fun `fitnessLevel is INTERMEDIATE if bodyFatPercent is medium`() {
        val profile = UserProfile(
            userId = "test_user",
            heightCm = 175f,
            weightKg = 70f,
            gender = Gender.MALE,
            bodyFatPercent = 25f,
            muscleMassKg = 30f,
        )
        assertEquals(FitnessLevel.INTERMEDIATE, profile.fitnessLevel)
    }

    @Test
    fun `fitnessLevel is ADVANCED if bodyFatPercent is low`() {
        val profile = UserProfile(
            userId = "test_user",
            heightCm = 175f,
            weightKg = 70f,
            gender = Gender.MALE,
            bodyFatPercent = 15f,
            muscleMassKg = 35f,
        )
        assertEquals(FitnessLevel.ADVANCED, profile.fitnessLevel)
    }

    @Test
    fun `fitnessLevel uses BMI if bodyFatPercent is null`() {
        val profile = UserProfile(userId = "test_user", heightCm = 170f, weightKg = 90f, gender = Gender.MALE, bodyFatPercent = null, muscleMassKg = null)
        // BMI = 90 / (1.7 * 1.7) = 31.14 (BEGINNER)
        assertEquals(FitnessLevel.BEGINNER, profile.fitnessLevel)
    }

    // --- nullable bodyFatPercent tests ---

    private fun profile(bodyFat: Float?, weight: Float = 70f, height: Float = 175f) = UserProfile("u", height, weight, Gender.UNSPECIFIED, bodyFat, null)

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
