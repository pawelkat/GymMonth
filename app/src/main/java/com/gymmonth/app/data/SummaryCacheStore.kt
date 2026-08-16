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
import com.gymmonth.app.domain.GymExerciseKind
import com.gymmonth.app.domain.GymVisit
import com.gymmonth.app.domain.MonthRange
import com.gymmonth.app.domain.MonthlyGymSummary
import com.gymmonth.app.domain.SourceStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.summaryDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "summary_prefs",
)

/**
 * On-device cache of derived monthly gym visits (health-sensitive; excluded from backup).
 */
@Singleton
class SummaryCacheStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.summaryDataStore

    val summaryFlow: Flow<MonthlyGymSummary?> = dataStore.data.map { prefs -> prefs.toSummary() }

    /** One-shot read for Glance / workers (DataStore is source of truth). */
    suspend fun getOnce(): MonthlyGymSummary? = summaryFlow.first()

    suspend fun save(summary: MonthlyGymSummary) {
        dataStore.edit { prefs ->
            prefs[Keys.MONTH_IDENTITY] = summary.month.identityKey()
            prefs[Keys.YEAR] = summary.month.year
            prefs[Keys.MONTH] = summary.month.month
            prefs[Keys.MONTH_START_EPOCH] = summary.month.start.toEpochMilli()
            prefs[Keys.MONTH_END_EPOCH] = summary.month.endExclusive.toEpochMilli()
            prefs[Keys.ZONE_ID] = summary.month.zoneId.id
            prefs[Keys.VISIT_COUNT] = summary.visitCount
            prefs[Keys.LAST_SYNCED] = summary.lastSyncedAt.toEpochMilli()
            prefs[Keys.SOURCE_STATUS] = summary.sourceStatus.name
            prefs[Keys.USER_REPORTED_MISSING] = summary.userReportedMissingData
            prefs[Keys.VISITS_BLOB] = encodeVisits(summary.visits)
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private fun Preferences.toSummary(): MonthlyGymSummary? {
        val identity = this[Keys.MONTH_IDENTITY] ?: return null
        val year = this[Keys.YEAR] ?: return null
        val monthNum = this[Keys.MONTH] ?: return null
        val startEpoch = this[Keys.MONTH_START_EPOCH] ?: return null
        val endEpoch = this[Keys.MONTH_END_EPOCH] ?: return null
        val zone = this[Keys.ZONE_ID] ?: return null
        val visitCount = this[Keys.VISIT_COUNT] ?: 0
        val lastSynced = this[Keys.LAST_SYNCED] ?: return null
        val statusName = this[Keys.SOURCE_STATUS] ?: SourceStatus.NO_DATA.name
        val userReported = this[Keys.USER_REPORTED_MISSING] ?: false
        val visitsBlob = this[Keys.VISITS_BLOB].orEmpty()

        val month = MonthRange(
            year = year,
            month = monthNum,
            start = Instant.ofEpochMilli(startEpoch),
            endExclusive = Instant.ofEpochMilli(endEpoch),
            zoneId = ZoneId.of(zone),
        )
        // identity stored for month-rollover comparison
        @Suppress("UNUSED_VARIABLE")
        val _identity = identity

        return MonthlyGymSummary(
            month = month,
            visitCount = visitCount,
            visits = decodeVisits(visitsBlob),
            lastSyncedAt = Instant.ofEpochMilli(lastSynced),
            sourceStatus = runCatching { SourceStatus.valueOf(statusName) }
                .getOrDefault(SourceStatus.ERROR),
            userReportedMissingData = userReported,
        )
    }

    private fun encodeVisits(visits: List<GymVisit>): String {
        // id\x1fkind\x1fstart\x1fend\x1fpackage\x1ftitle\x1foverlap\x1fcounts\x1fplaceId\x1fplaceName
        return visits.joinToString("\n") { v ->
            listOf(
                v.id,
                v.exerciseType.name,
                v.start.toEpochMilli().toString(),
                v.end.toEpochMilli().toString(),
                v.dataOriginPackage,
                v.title.orEmpty().replace('\n', ' ').replace('\u001f', ' '),
                v.overlapSuppressed.toString(),
                v.countsAsVisit.toString(),
                v.gymPlaceId.orEmpty().replace('\u001f', ' '),
                v.gymName.orEmpty().replace('\n', ' ').replace('\u001f', ' '),
            ).joinToString("\u001f")
        }
    }

    private fun decodeVisits(blob: String): List<GymVisit> {
        if (blob.isBlank()) return emptyList()
        return blob.lineSequence().mapNotNull { line ->
            val p = line.split('\u001f')
            if (p.size < 8) return@mapNotNull null
            runCatching {
                GymVisit(
                    id = p[0],
                    exerciseType = GymExerciseKind.valueOf(p[1]),
                    start = Instant.ofEpochMilli(p[2].toLong()),
                    end = Instant.ofEpochMilli(p[3].toLong()),
                    dataOriginPackage = p[4],
                    title = p[5].ifEmpty { null },
                    overlapSuppressed = p[6].toBoolean(),
                    countsAsVisit = p[7].toBoolean(),
                    gymPlaceId = p.getOrNull(8)?.ifEmpty { null },
                    gymName = p.getOrNull(9)?.ifEmpty { null },
                )
            }.getOrNull()
        }.toList()
    }

    private object Keys {
        val MONTH_IDENTITY = stringPreferencesKey("month_identity")
        val YEAR = intPreferencesKey("year")
        val MONTH = intPreferencesKey("month")
        val MONTH_START_EPOCH = longPreferencesKey("month_start_epoch")
        val MONTH_END_EPOCH = longPreferencesKey("month_end_epoch")
        val ZONE_ID = stringPreferencesKey("zone_id")
        val VISIT_COUNT = intPreferencesKey("visit_count")
        val LAST_SYNCED = longPreferencesKey("last_synced")
        val SOURCE_STATUS = stringPreferencesKey("source_status")
        val USER_REPORTED_MISSING = booleanPreferencesKey("user_reported_missing")
        val VISITS_BLOB = stringPreferencesKey("visits_blob")
    }
}
