package com.gymmonth.app.health

import com.gymmonth.app.domain.GymExerciseKind
import com.gymmonth.app.domain.GymVisit
import com.gymmonth.app.domain.MonthRange
import com.gymmonth.app.domain.MonthlyGymSummary
import com.gymmonth.app.domain.SourceStatus
import com.gymmonth.app.domain.VisitCountingMode
import com.gymmonth.app.domain.applyOverlapPolicy
import com.gymmonth.app.domain.applyVisitCountingPolicy
import com.gymmonth.app.domain.countVisits
import com.gymmonth.app.util.ClockProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production monthly gym visit aggregation (PR 3).
 *
 * Filter gym types → month attribution by start → overlap credit-once → visit policy.
 */
@Singleton
class GymVisitAggregator @Inject constructor(
    private val dataSource: HealthConnectDataSource,
    private val clock: ClockProvider,
) {
    /**
     * @param includedKinds exercise kinds that count (default: strength family + HIIT)
     * @param matchTitleKeywords when true, also include unmapped types whose title/notes look gym-like
     * @param visitMode [VisitCountingMode.PER_SESSION] (default) or one-per-day
     */
    suspend fun aggregateMonth(
        month: MonthRange,
        includedKinds: Set<GymExerciseKind> = GymExerciseTypeMapper.defaultIncludedKinds,
        matchTitleKeywords: Boolean = true,
        visitMode: VisitCountingMode = VisitCountingMode.PER_SESSION,
    ): MonthlyGymSummary {
        val rawRecords = dataSource.listExerciseSessions(month.start, month.endExclusive)
        val rawVisits = mutableListOf<GymVisit>()

        for (rec in rawRecords) {
            if (!month.containsSessionStart(rec.start)) continue

            val kind = resolveKind(rec, includedKinds, matchTitleKeywords) ?: continue

            rawVisits += GymVisit(
                id = rec.id,
                start = rec.start,
                end = rec.end,
                exerciseType = kind,
                dataOriginPackage = rec.dataOriginPackage,
                title = rec.title,
            )
        }

        val afterOverlap = applyOverlapPolicy(rawVisits)
        val visits = applyVisitCountingPolicy(
            visits = afterOverlap,
            mode = visitMode,
            zoneId = month.zoneId,
        ).sortedByDescending { it.start }

        return MonthlyGymSummary(
            month = month,
            visitCount = countVisits(visits),
            visits = visits,
            lastSyncedAt = clock.now(),
            sourceStatus = if (visits.isEmpty()) SourceStatus.NO_DATA else SourceStatus.OK,
        )
    }

    private fun resolveKind(
        rec: RawExerciseSession,
        includedKinds: Set<GymExerciseKind>,
        matchTitleKeywords: Boolean,
    ): GymExerciseKind? {
        val mapped = GymExerciseTypeMapper.toKind(rec.exerciseType)
        if (mapped != null && mapped in includedKinds) return mapped
        if (matchTitleKeywords &&
            GymExerciseTypeMapper.titleLooksLikeGym(rec.title, rec.notes)
        ) {
            return GymExerciseKind.OTHER
        }
        return null
    }
}
