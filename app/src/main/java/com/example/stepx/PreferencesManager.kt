package com.example.stepx

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val DATASTORE_NAME = "stepx_prefs"

val Context.dataStore by preferencesDataStore(name = DATASTORE_NAME)

class PreferencesManager(private val context: Context) {
    private object Keys {
        val DAILY_GOAL: Preferences.Key<Int> = intPreferencesKey("daily_goal")
        val LIGHT_REMINDER_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("light_reminder_enabled")
        val POCKET_DETECTION_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("pocket_detection_enabled")
        val DARK_THEME_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("dark_theme_enabled")
        val BACKGROUND_TRACKING_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("background_tracking_enabled")
        val TOTAL_STEPS: Preferences.Key<Int> = intPreferencesKey("total_steps")
    }

    val dailyGoal: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.DAILY_GOAL] ?: 10000
    }

    val lightReminderEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.LIGHT_REMINDER_ENABLED] ?: true
    }

    val pocketDetectionEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.POCKET_DETECTION_ENABLED] ?: true
    }

    val darkThemeEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.DARK_THEME_ENABLED] ?: false
    }

    val backgroundTrackingEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.BACKGROUND_TRACKING_ENABLED] ?: false
    }

    // Total steps (not tied to date)
    val totalSteps: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.TOTAL_STEPS] ?: 0
    }

    suspend fun setDailyGoal(goal: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DAILY_GOAL] = goal
        }
    }

    suspend fun setLightReminderEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LIGHT_REMINDER_ENABLED] = enabled
        }
    }

    suspend fun setPocketDetectionEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.POCKET_DETECTION_ENABLED] = enabled
        }
    }

    suspend fun setDarkThemeEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DARK_THEME_ENABLED] = enabled
        }
    }

    suspend fun setBackgroundTrackingEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BACKGROUND_TRACKING_ENABLED] = enabled
        }
    }

    suspend fun setTotalSteps(steps: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TOTAL_STEPS] = steps.coerceAtLeast(0)
        }
    }

    suspend fun incrementTotalSteps(delta: Int = 1) {
        if (delta == 0) return
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.TOTAL_STEPS] ?: 0
            prefs[Keys.TOTAL_STEPS] = (current + delta).coerceAtLeast(0)
        }
    }

    // --- Daily steps history ---
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    private fun stepsKeyFor(date: LocalDate): Preferences.Key<Int> {
        val name = "steps_" + date.format(dateFormatter)
        return intPreferencesKey(name)
    }

    fun stepsForDate(date: LocalDate): Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[stepsKeyFor(date)] ?: 0
    }

    suspend fun setStepsForDate(date: LocalDate, steps: Int) {
        context.dataStore.edit { prefs ->
            prefs[stepsKeyFor(date)] = steps.coerceAtLeast(0)
        }
    }

    suspend fun incrementStepsForDate(date: LocalDate, delta: Int = 1) {
        context.dataStore.edit { prefs ->
            val key = stepsKeyFor(date)
            val current = prefs[key] ?: 0
            prefs[key] = (current + delta).coerceAtLeast(0)
        }
    }

    fun lastNDaysSteps(n: Int): Flow<List<Int>> = context.dataStore.data.map { prefs ->
        val today = LocalDate.now()
        (n - 1 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            prefs[stepsKeyFor(date)] ?: 0
        }
    }
}


