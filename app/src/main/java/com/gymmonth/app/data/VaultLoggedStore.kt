package com.gymmonth.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.vaultLoggedDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "vault_logged",
)

/**
 * Visit ids already written to the vault, so auto-log after sync does not re-add a bullet the
 * user removed from the Daily note. The Daily file's own dedupe key is the primary guard.
 */
@Singleton
class VaultLoggedStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.vaultLoggedDataStore

    suspend fun ids(): Set<String> = dataStore.data.map { it[Keys.IDS] ?: emptySet() }.first()

    suspend fun markLogged(ids: Collection<String>) {
        if (ids.isEmpty()) return
        dataStore.edit { prefs ->
            val merged = (prefs[Keys.IDS] ?: emptySet()) + ids
            prefs[Keys.IDS] = if (merged.size > MAX_IDS) merged.toList().takeLast(MAX_IDS).toSet() else merged
        }
    }

    private object Keys {
        val IDS = stringSetPreferencesKey("logged_ids")
    }

    private companion object {
        const val MAX_IDS = 1000
    }
}
