package com.gymmonth.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.changesTokenDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "hc_changes_token",
)

/**
 * Opaque Health Connect changes token.
 * Cleared with health state on permission revoke.
 */
@Singleton
class ChangesTokenStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.changesTokenDataStore

    val tokenFlow: Flow<String?> = dataStore.data.map { it[Keys.TOKEN] }

    suspend fun get(): String? = tokenFlow.first()

    suspend fun save(token: String) {
        dataStore.edit { it[Keys.TOKEN] = token }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(Keys.TOKEN) }
    }

    private object Keys {
        val TOKEN = stringPreferencesKey("token")
    }
}
