package com.gymmonth.app.health

import com.gymmonth.app.data.PreferencesStore
import com.gymmonth.app.data.SummaryCacheStore
import com.gymmonth.app.domain.MonthRange
import com.gymmonth.app.domain.MonthlyGymSummary
import com.gymmonth.app.domain.SourceStatus
import com.gymmonth.app.sync.SyncReason
import com.gymmonth.app.sync.WidgetUpdater
import com.gymmonth.app.util.ClockProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * Orchestrates monthly aggregation, cache, and future widget updates.
 * Changes-token incremental sync lands with background workers (PR 6).
 */
@Singleton
class GymVisitRepository @Inject constructor(
    private val aggregator: GymVisitAggregator,
    private val summaryCache: SummaryCacheStore,
    private val preferencesStore: PreferencesStore,
    private val availability: HealthConnectAvailability,
    private val permissionChecker: HealthPermissionChecker,
    private val clock: ClockProvider,
    private val widgetUpdater: WidgetUpdater,
) {
    private val inMemory = MutableStateFlow<MonthlyGymSummary?>(null)

    /** Cache + latest in-memory refresh for UI. */
    val summaryFlow: Flow<MonthlyGymSummary?> =
        combine(summaryCache.summaryFlow, inMemory) { cached, mem -> mem ?: cached }

    /**
     * Entry used by UI and workers.
     * v1 always full-aggregates for MANUAL / FOREGROUND; PERIODIC same until PR 6.
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
            SyncReason.PERIODIC,
            SyncReason.CHANGES,
            -> true
        }
        return refreshMonthlySummary(forceFull = forceFull, reason = reason)
    }

    suspend fun refreshMonthlySummary(
        forceFull: Boolean = true,
        @Suppress("UNUSED_PARAMETER") reason: SyncReason = SyncReason.MANUAL,
    ): MonthlyGymSummary {
        val month = MonthRange.current(
            zoneId = clock.zoneId(),
            clock = clock.clock(),
        )

        if (!availability.isAvailable()) {
            return cacheAndReturn(
                MonthlyGymSummary.empty(
                    month = month,
                    lastSyncedAt = clock.now(),
                    sourceStatus = SourceStatus.HEALTH_CONNECT_UNAVAILABLE,
                ),
            )
        }

        if (!permissionChecker.hasRequiredPermissions()) {
            return cacheAndReturn(
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
        val doFull = forceFull || monthChanged || cached == null

        // When not forcing full and month identity matches, keep cache (PR 6 will
        // drain HC Changes tokens here).
        if (!doFull) {
            val keep = cached ?: return fullAggregate(month, null)
            inMemory.value = keep
            return keep
        }

        return fullAggregate(month, cached)
    }

    private suspend fun fullAggregate(
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
            cacheAndReturn(summary)
        } catch (_: SecurityException) {
            cacheAndReturn(
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

    /**
     * Clears summary + in-memory state (permission revoke path).
     */
    suspend fun clearLocalHealthState() {
        summaryCache.clear()
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
