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
}
