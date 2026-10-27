package com.gunnys.eundunhealth.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Singleton
class ThemePreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val themeKey = stringPreferencesKey("theme_mode")
    private val autoCreateWeekKey = stringPreferencesKey("auto_create_attempted_week_start")
    private val v2ResetCompletedKey = booleanPreferencesKey("v2_local_reset_completed")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[themeKey] = mode.name
        }
    }

    suspend fun hasAutoCreateAttempted(weekStart: LocalDate): Boolean {
        val prefs = context.dataStore.data.first()
        return prefs[autoCreateWeekKey] == weekStart.toString()
    }

    suspend fun markAutoCreateAttempted(weekStart: LocalDate) {
        context.dataStore.edit { prefs ->
            prefs[autoCreateWeekKey] = weekStart.toString()
        }
    }

    suspend fun clearAutoCreateAttempt() {
        context.dataStore.edit { prefs ->
            prefs.remove(autoCreateWeekKey)
        }
    }

    suspend fun isV2LocalResetCompleted(): Boolean {
        val prefs = context.dataStore.data.first()
        return prefs[v2ResetCompletedKey] == true
    }

    suspend fun markV2LocalResetCompleted() {
        context.dataStore.edit { prefs ->
            prefs.clear()
            prefs[v2ResetCompletedKey] = true
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { prefs ->
            val wasReset = prefs[v2ResetCompletedKey] == true
            prefs.clear()
            if (wasReset) {
                prefs[v2ResetCompletedKey] = true
            }
        }
    }
}
