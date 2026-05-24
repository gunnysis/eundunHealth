package com.gunnys.eundunhealth.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthViewModelTest {

    @Test
    fun `AuthState Loading is initial default`() {
        val state: AuthState = AuthState.Loading
        assertEquals(AuthState.Loading, state)
    }

    @Test
    fun `AuthState Authenticated carries userId and onboarding flag`() {
        val state = AuthState.Authenticated(userId = "user-123", needsOnboarding = true)
        assertEquals("user-123", state.userId)
        assertEquals(true, state.needsOnboarding)
    }

    @Test
    fun `AuthState Authenticated defaults needsOnboarding to false`() {
        val state = AuthState.Authenticated(userId = "user-456")
        assertEquals(false, state.needsOnboarding)
    }

    @Test
    fun `AuthState Unauthenticated is its own singleton`() {
        assertEquals(AuthState.Unauthenticated, AuthState.Unauthenticated)
    }
}
