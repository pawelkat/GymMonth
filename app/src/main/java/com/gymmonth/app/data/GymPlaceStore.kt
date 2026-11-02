package com.gymmonth.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gymmonth.app.domain.SessionGymPlace
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.gymPlaceDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "session_gym_places",
)

/**
 * Maps session ids (Health Connect or manual) to a locally chosen gym.
 * On-device only (mirrors SwimWeek SessionPlaceStore).
 */
@Singleton
class GymPlaceStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.gymPlaceDataStore

    val placesFlow: Flow<Map<String, SessionGymPlace>> =
        dataStore.data.map { GymJsonCodecs.decodePlaces(it[Keys.PLACES_BLOB]) }

    suspend fun getMap(): Map<String, SessionGymPlace> = placesFlow.first()

    suspend fun setPlace(sessionId: String, gymPlaceId: String, gymName: String) {
        dataStore.edit { prefs ->
            val current = GymJsonCodecs.decodePlaces(prefs[Keys.PLACES_BLOB]).toMutableMap()
            current[sessionId] = SessionGymPlace(
                sessionId = sessionId,
                gymPlaceId = gymPlaceId,
                gymName = gymName,
                updatedAtEpochMs = System.currentTimeMillis(),
            )
            prefs[Keys.PLACES_BLOB] = GymJsonCodecs.encodePlaces(current)
        }
    }

    suspend fun clearPlace(sessionId: String) {
        dataStore.edit { prefs ->
            val current = GymJsonCodecs.decodePlaces(prefs[Keys.PLACES_BLOB]).toMutableMap()
            if (current.remove(sessionId) != null) {
                prefs[Keys.PLACES_BLOB] = GymJsonCodecs.encodePlaces(current)
            }
        }
    }

    private object Keys {
        val PLACES_BLOB = stringPreferencesKey("places_blob")
    }
}
