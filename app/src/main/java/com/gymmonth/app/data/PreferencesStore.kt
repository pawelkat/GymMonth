package com.gymmonth.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gymmonth.app.domain.VisitCountingMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.userPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_prefs",
)

data class UserPreferences(
    val onboardingCompleted: Boolean = false,
    /** Monthly visit target; 0 = unset (no progress ring). */
    val monthlyTargetVisits: Int = 0,
    /** Default: one visit per session (plan PR1 decision). */
    val visitCountingMode: VisitCountingMode = VisitCountingMode.PER_SESSION,
    /**
     * When true, schedule gym-go reminder notifications (PR 9).
     */
    val gymReminderEnabled: Boolean = false,
    val schemaVersion: Int = 1,
)

@Singleton
class PreferencesStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.userPrefsDataStore

    val preferencesFlow: Flow<UserPreferences> = dataStore.data.map { prefs ->
        UserPreferences(
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            monthlyTargetVisits = (prefs[Keys.MONTHLY_TARGET_VISITS] ?: 0).coerceAtLeast(0),
            visitCountingMode = prefs[Keys.VISIT_COUNTING_MODE]?.let {
                runCatching { VisitCountingMode.valueOf(it) }.getOrNull()
            } ?: VisitCountingMode.PER_SESSION,
            gymReminderEnabled = prefs[Keys.GYM_REMINDER_ENABLED] ?: false,
            schemaVersion = prefs[Keys.SCHEMA_VERSION] ?: 1,
        )
    }

    suspend fun get(): UserPreferences = preferencesFlow.first()

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    /**
     * Persist monthly visit target. Pass 0 to clear/unset.
     */
    suspend fun setMonthlyTargetVisits(visits: Int) {
        val sanitized = if (visits > 0) visits else 0
        dataStore.edit { it[Keys.MONTHLY_TARGET_VISITS] = sanitized }
    }

    suspend fun setVisitCountingMode(mode: VisitCountingMode) {
        dataStore.edit { it[Keys.VISIT_COUNTING_MODE] = mode.name }
    }

    suspend fun setGymReminderEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.GYM_REMINDER_ENABLED] = enabled }
    }

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val MONTHLY_TARGET_VISITS = intPreferencesKey("monthly_target_visits")
        val VISIT_COUNTING_MODE = stringPreferencesKey("visit_counting_mode")
        val GYM_REMINDER_ENABLED = booleanPreferencesKey("gym_reminder_enabled")
        val SCHEMA_VERSION = intPreferencesKey("schema_version")
    }
}
