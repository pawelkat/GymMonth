package com.gymmonth.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gymmonth.app.domain.ManualVisit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.manualVisitDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "manual_visits",
)

/**
 * App-local manual gym visits. Never written to Health Connect.
 */
@Singleton
class ManualVisitStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.manualVisitDataStore

    val visitsFlow: Flow<List<ManualVisit>> =
        dataStore.data.map { GymJsonCodecs.decodeManualVisits(it[Keys.VISITS_BLOB]) }

    suspend fun getAll(): List<ManualVisit> = visitsFlow.first()

    suspend fun add(visit: ManualVisit) = mutate { GymJsonCodecs.addManual(it, visit) }

    suspend fun delete(id: String) = mutate { GymJsonCodecs.deleteManual(it, id) }

    suspend fun updatePlace(id: String, gymPlaceId: String?, gymName: String?) =
        mutate { GymJsonCodecs.updateManualPlace(it, id, gymPlaceId, gymName) }

    private suspend fun mutate(block: (List<ManualVisit>) -> List<ManualVisit>) {
        dataStore.edit { prefs ->
            val current = GymJsonCodecs.decodeManualVisits(prefs[Keys.VISITS_BLOB])
            prefs[Keys.VISITS_BLOB] = GymJsonCodecs.encodeManualVisits(block(current))
        }
    }

    private object Keys {
        val VISITS_BLOB = stringPreferencesKey("visits_blob")
    }
}
