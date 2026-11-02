package com.gymmonth.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gymmonth.app.domain.VisitCountingMode
import com.vaultwrite.VaultPaths
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
    /** Write visits to the Obsidian vault Daily note under `## Workouts` (VaultWrite). */
    val vaultWriteEnabled: Boolean = false,
    /** Vault root on the phone (DriveSync mirror). */
    val vaultRoot: String = VaultPaths.DEFAULT_VAULT_ROOT,
    /** Also log visits that newly appear after a Health Connect sync. */
    val vaultAutoLogSynced: Boolean = false,
    /** Auto-log only visits starting at/after this instant (set when auto-log is enabled). */
    val vaultAutoLogSinceEpochMs: Long = 0L,
    /** Preselected gym for Add visit; null = first catalog gym (PureGym). */
    val defaultGymId: String? = null,
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
            vaultWriteEnabled = prefs[Keys.VAULT_WRITE_ENABLED] ?: false,
            vaultRoot = prefs[Keys.VAULT_ROOT]?.trim()?.takeIf { it.isNotEmpty() }
                ?: VaultPaths.DEFAULT_VAULT_ROOT,
            vaultAutoLogSynced = prefs[Keys.VAULT_AUTO_LOG_SYNCED] ?: false,
            vaultAutoLogSinceEpochMs = prefs[Keys.VAULT_AUTO_LOG_SINCE] ?: 0L,
            defaultGymId = prefs[Keys.DEFAULT_GYM_ID]?.takeIf { it.isNotBlank() },
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

    suspend fun setVaultWriteEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.VAULT_WRITE_ENABLED] = enabled }
    }

    /** Blank resets to [VaultPaths.DEFAULT_VAULT_ROOT]. */
    suspend fun setVaultRoot(root: String) {
        dataStore.edit { it[Keys.VAULT_ROOT] = root.trim() }
    }

    suspend fun setVaultAutoLogSynced(enabled: Boolean, nowEpochMs: Long) {
        dataStore.edit {
            val wasOn = it[Keys.VAULT_AUTO_LOG_SYNCED] ?: false
            it[Keys.VAULT_AUTO_LOG_SYNCED] = enabled
            if (enabled && !wasOn) it[Keys.VAULT_AUTO_LOG_SINCE] = nowEpochMs
        }
    }

    suspend fun setDefaultGymId(id: String?) {
        dataStore.edit { it[Keys.DEFAULT_GYM_ID] = id.orEmpty() }
    }

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val MONTHLY_TARGET_VISITS = intPreferencesKey("monthly_target_visits")
        val VISIT_COUNTING_MODE = stringPreferencesKey("visit_counting_mode")
        val GYM_REMINDER_ENABLED = booleanPreferencesKey("gym_reminder_enabled")
        val VAULT_WRITE_ENABLED = booleanPreferencesKey("vault_write_enabled")
        val VAULT_ROOT = stringPreferencesKey("vault_root")
        val VAULT_AUTO_LOG_SYNCED = booleanPreferencesKey("vault_auto_log_synced")
        val VAULT_AUTO_LOG_SINCE = longPreferencesKey("vault_auto_log_since")
        val DEFAULT_GYM_ID = stringPreferencesKey("default_gym_id")
        val SCHEMA_VERSION = intPreferencesKey("schema_version")
    }
}
