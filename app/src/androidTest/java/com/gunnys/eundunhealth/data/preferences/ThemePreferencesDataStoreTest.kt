package com.gunnys.eundunhealth.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class ThemePreferencesDataStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences = ThemePreferences(context)

    @After
    fun clearPreferences() = runBlocking { preferences.clearAll() }

    @Test
    fun writesRemainReadableThroughDataStore() = runBlocking {
        val weekStart = LocalDate.of(2026, 10, 26)
        preferences.clearAll()

        preferences.setThemeMode(ThemeMode.DARK)
        preferences.markAutoCreateAttempted(weekStart)

        assertEquals(ThemeMode.DARK, preferences.themeMode.first())
        assertTrue(preferences.hasAutoCreateAttempted(weekStart))
    }
}
