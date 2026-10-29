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
    private val dynamicColorKey = booleanPreferencesKey("dynamic_color_enabled")
    private val autoCreateWeekKey = stringPreferencesKey("auto_create_attempted_week_start")
    private val v2ResetCompletedKey = booleanPreferencesKey("v2_local_reset_completed")
    private val smileDismissedDateKey = stringPreferencesKey("smile_shortcut_dismissed_date")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    val dynamicColorEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[dynamicColorKey] ?: false
    }

    // DataStore Preferences.edit is suspend; detekt 1.23 type resolution misses this contract.
    @Suppress("RedundantSuspendModifier")
    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[themeKey] = mode.name
        }
    }

    // DataStore Preferences.edit is suspend; detekt 1.23 type resolution misses this contract.
    @Suppress("RedundantSuspendModifier")
    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[dynamicColorKey] = enabled
        }
    }

    suspend fun isSmileShortcutDismissed(today: LocalDate): Boolean {
        val prefs = context.dataStore.data.first()
        return prefs[smileDismissedDateKey] == today.toString()
    }

    // DataStore Preferences.edit is suspend; keep this API suspend for callers.
    @Suppress("RedundantSuspendModifier")
    suspend fun markSmileShortcutDismissed(today: LocalDate) {
        context.dataStore.edit { prefs ->
            prefs[smileDismissedDateKey] = today.toString()
        }
    }

    suspend fun hasAutoCreateAttempted(weekStart: LocalDate): Boolean {
        val prefs = context.dataStore.data.first()
        return prefs[autoCreateWeekKey] == weekStart.toString()
    }

    // DataStore Preferences.edit is suspend; keep this API suspend for callers.
    @Suppress("RedundantSuspendModifier")
    suspend fun markAutoCreateAttempted(weekStart: LocalDate) {
        context.dataStore.edit { prefs ->
            prefs[autoCreateWeekKey] = weekStart.toString()
        }
    }

    // DataStore Preferences.edit is suspend; detekt cannot resolve the extension here.
    @Suppress("RedundantSuspendModifier")
    suspend fun clearAutoCreateAttempt() {
        context.dataStore.edit { prefs ->
            prefs.remove(autoCreateWeekKey)
        }
    }

    suspend fun isV2LocalResetCompleted(): Boolean {
        val prefs = context.dataStore.data.first()
        return prefs[v2ResetCompletedKey] == true
    }

    // DataStore Preferences.edit is suspend; preserve structured-concurrency semantics.
    @Suppress("RedundantSuspendModifier")
    suspend fun markV2LocalResetCompleted() {
        context.dataStore.edit { prefs ->
            val savedTheme = prefs[themeKey]
            val savedDynamicColor = prefs[dynamicColorKey]
            prefs.clear()
            savedTheme?.let { prefs[themeKey] = it }
            savedDynamicColor?.let { prefs[dynamicColorKey] = it }
            prefs[v2ResetCompletedKey] = true
        }
    }

    // DataStore Preferences.edit is suspend; a local suppression avoids a global baseline.
    @Suppress("RedundantSuspendModifier")
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
