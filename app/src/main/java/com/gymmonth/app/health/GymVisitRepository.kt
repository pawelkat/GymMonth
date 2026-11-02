package com.gymmonth.app.health

import com.gymmonth.app.data.ChangesTokenStore
import com.gymmonth.app.data.GymPlaceStore
import com.gymmonth.app.data.ManualVisitStore
import com.gymmonth.app.data.PreferencesStore
import com.gymmonth.app.data.SummaryCacheStore
import com.gymmonth.app.domain.ManualVisit
import com.gymmonth.app.domain.MonthRange
import com.gymmonth.app.domain.MonthlyGymSummary
import com.gymmonth.app.domain.SourceStatus
import com.gymmonth.app.domain.isManualVisitId
import com.gymmonth.app.domain.mergeManualVisits
import com.gymmonth.app.sync.SyncReason
import com.gymmonth.app.sync.WidgetUpdater
import com.gymmonth.app.util.ClockProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Orchestrates monthly aggregation, Changes-token incremental sync, cache,
 * and Glance updates via [WidgetUpdater].
 */
@Singleton
class GymVisitRepository @Inject constructor(
    private val aggregator: GymVisitAggregator,
    private val summaryCache: SummaryCacheStore,
    private val changesTokenStore: ChangesTokenStore,
    private val changesSyncEngine: ChangesSyncEngine,
    private val preferencesStore: PreferencesStore,
    private val availability: HealthConnectAvailability,
    private val permissionChecker: HealthPermissionChecker,
    private val clock: ClockProvider,
    private val widgetUpdater: WidgetUpdater,
    private val manualVisitStore: ManualVisitStore,
    private val gymPlaceStore: GymPlaceStore,
) {
    private val inMemory = MutableStateFlow<MonthlyGymSummary?>(null)
    private val writeMutex = Mutex()

    /** Cache + latest in-memory refresh for UI. */
    val summaryFlow: Flow<MonthlyGymSummary?> =
        combine(summaryCache.summaryFlow, inMemory) { cached, mem -> mem ?: cached }

    /**
     * Entry used by UI and workers.
     * - MANUAL / FOREGROUND / MIDNIGHT / TOKEN_EXPIRED → full aggregate
     * - PERIODIC / CHANGES → drain first; re-aggregate only if needed
     */
    suspend fun sync(reason: SyncReason): MonthlyGymSummary {
        val forceFull = when (reason) {
            SyncReason.MANUAL,
            SyncReason.APP_FOREGROUND,
            SyncReason.MIDNIGHT,
            SyncReason.PERMISSION_GRANTED,
            SyncReason.MONTH_IDENTITY_CHANGED,
            SyncReason.TOKEN_EXPIRED,
            SyncReason.WIDGET_PIN,
            -> true
            SyncReason.PERIODIC,
            SyncReason.CHANGES,
            -> false
        }
        return refreshMonthlySummary(forceFull = forceFull, reason = reason)
    }

    suspend fun refreshMonthlySummary(
        forceFull: Boolean = false,
        @Suppress("UNUSED_PARAMETER") reason: SyncReason = SyncReason.MANUAL,
    ): MonthlyGymSummary {
        val month = MonthRange.current(
            zoneId = clock.zoneId(),
            clock = clock.clock(),
        )

        if (!availability.isAvailable()) {
            return mergeAndCache(
                MonthlyGymSummary.empty(
                    month = month,
                    lastSyncedAt = clock.now(),
                    sourceStatus = SourceStatus.HEALTH_CONNECT_UNAVAILABLE,
                ),
            )
        }

        if (!permissionChecker.hasRequiredPermissions()) {
            return mergeAndCache(
                MonthlyGymSummary.empty(
                    month = month,
                    lastSyncedAt = clock.now(),
                    sourceStatus = SourceStatus.PERMISSIONS_MISSING,
                ),
            )
        }

        val cached = summaryCache.summaryFlow.first()
        val monthChanged = cached != null &&
            cached.month.identityKey() != month.identityKey()
        val noToken = changesTokenStore.get().isNullOrBlank()
        var doFull = forceFull || monthChanged || noToken

        if (!doFull) {
            val drain = changesSyncEngine.drain()
            if (drain.tokenExpired) {
                doFull = true
            } else if (!drain.needsReaggregate) {
                val keep = cached ?: return fullAggregateAndToken(month, cached)
                if (keep.month.identityKey() == month.identityKey()) {
                    inMemory.value = keep
                    return keep
                }
                doFull = true
            } else {
                doFull = true
            }
        }

        return if (doFull) {
            fullAggregateAndToken(month, cached)
        } else {
            cached ?: fullAggregateAndToken(month, null)
        }
    }

    private suspend fun fullAggregateAndToken(
        month: MonthRange,
        cached: MonthlyGymSummary?,
    ): MonthlyGymSummary {
        return try {
            val prefs = preferencesStore.get()
            val previousUserFlag = cached
                ?.takeIf { it.month.identityKey() == month.identityKey() }
                ?.userReportedMissingData
                ?: false

            val summary = aggregator.aggregateMonth(
                month = month,
                visitMode = prefs.visitCountingMode,
            ).let { s ->
                if (s.visits.isNotEmpty()) {
                    s.copy(userReportedMissingData = false)
                } else {
                    s.copy(
                        userReportedMissingData = previousUserFlag,
                        sourceStatus = if (previousUserFlag) {
                            SourceStatus.USER_REPORTED_MISSING_BRIDGE
                        } else {
                            s.sourceStatus
                        },
                    )
                }
            }
            // Re-merge app-local manual visits + place tags after every HC aggregate.
            val saved = mergeAndCache(summary)
            changesSyncEngine.issueToken()
            saved
        } catch (_: SecurityException) {
            mergeAndCache(
                MonthlyGymSummary.empty(
                    month = month,
                    lastSyncedAt = clock.now(),
                    sourceStatus = SourceStatus.PERMISSIONS_MISSING,
                ),
            )
        } catch (_: Exception) {
            val fallback = cached?.copy(
                lastSyncedAt = clock.now(),
                sourceStatus = SourceStatus.ERROR,
            ) ?: MonthlyGymSummary.empty(
                month = month,
                lastSyncedAt = clock.now(),
                sourceStatus = SourceStatus.ERROR,
            )
            cacheAndReturn(fallback)
        }
    }

    // ---- Manual visits + gym place tagging (app-local; never written to Health Connect) ----

    suspend fun addManualVisit(visit: ManualVisit): MonthlyGymSummary {
        manualVisitStore.add(visit)
        return recomputeLocal()
    }

    /** Deletes a manual visit. HC visits cannot be deleted (returns null, no-op). */
    suspend fun deleteManualVisit(id: String): MonthlyGymSummary? {
        if (!isManualVisitId(id)) return null
        manualVisitStore.delete(id)
        gymPlaceStore.clearPlace(id)
        return recomputeLocal()
    }

    suspend fun setSessionGym(sessionId: String, gymPlaceId: String, gymName: String): MonthlyGymSummary {
        if (isManualVisitId(sessionId)) {
            manualVisitStore.updatePlace(sessionId, gymPlaceId, gymName)
            gymPlaceStore.clearPlace(sessionId)
        } else {
            gymPlaceStore.setPlace(sessionId, gymPlaceId, gymName)
        }
        return recomputeLocal()
    }

    suspend fun clearSessionGym(sessionId: String): MonthlyGymSummary {
        if (isManualVisitId(sessionId)) {
            manualVisitStore.updatePlace(sessionId, null, null)
        }
        gymPlaceStore.clearPlace(sessionId)
        return recomputeLocal()
    }

    /**
     * Re-merge manual visits + tags onto the cached HC visits without hitting Health Connect.
     */
    suspend fun recomputeLocal(): MonthlyGymSummary {
        val month = MonthRange.current(zoneId = clock.zoneId(), clock = clock.clock())
        val cached = summaryFlow.first()
            ?.takeIf { it.month.identityKey() == month.identityKey() }
        val base = cached ?: MonthlyGymSummary.empty(month = month, lastSyncedAt = clock.now())
        return mergeAndCache(base, keepSyncTime = cached != null)
    }

    private suspend fun mergeAndCache(
        hcSummary: MonthlyGymSummary,
        keepSyncTime: Boolean = false,
    ): MonthlyGymSummary = writeMutex.withLock {
        val prefs = preferencesStore.get()
        val merged = mergeManualVisits(
            hcSummary = hcSummary,
            manualVisits = manualVisitStore.getAll(),
            placeTags = gymPlaceStore.getMap(),
            visitMode = prefs.visitCountingMode,
            now = if (keepSyncTime) hcSummary.lastSyncedAt else clock.now(),
        )
        cacheAndReturn(merged)
    }

    /**
     * Clears summary + changes token + in-memory state (permission revoke path).
     */
    suspend fun clearLocalHealthState() {
        summaryCache.clear()
        changesTokenStore.clear()
        inMemory.value = null
        widgetUpdater.updateAll()
    }

    suspend fun setUserReportedMissingData(reported: Boolean) {
        val current = summaryFlow.first() ?: return
        val updated = current.copy(
            userReportedMissingData = reported,
            sourceStatus = when {
                reported && current.visits.isEmpty() -> SourceStatus.USER_REPORTED_MISSING_BRIDGE
                current.visits.isEmpty() -> SourceStatus.NO_DATA
                else -> current.sourceStatus
            },
        )
        cacheAndReturn(updated)
    }

    private suspend fun cacheAndReturn(summary: MonthlyGymSummary): MonthlyGymSummary {
        summaryCache.save(summary)
        inMemory.value = summary
        widgetUpdater.updateAll()
        return summary
    }
}
