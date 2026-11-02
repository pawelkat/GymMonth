package com.gymmonth.app.domain

import java.time.Instant

/**
 * Merge Health Connect visits with app-local manual visits and gym place tags, then
 * re-run overlap + visit counting so [MonthlyGymSummary.visitCount] includes manuals.
 *
 * - HC visits: place comes only from [placeTags] (cleared when the tag is removed).
 * - Manual visits: keep their own place; a tag for the same id overrides it.
 * - Only manuals whose start falls in [MonthlyGymSummary.month] are included.
 * - Any manual visits already present in [hcSummary] are dropped and re-added (idempotent).
 */
fun mergeManualVisits(
    hcSummary: MonthlyGymSummary,
    manualVisits: List<ManualVisit>,
    placeTags: Map<String, SessionGymPlace>,
    visitMode: VisitCountingMode,
    now: Instant = hcSummary.lastSyncedAt,
): MonthlyGymSummary {
    val month = hcSummary.month
    val hcVisits = hcSummary.visits
        .filterNot { it.isManual }
        .map { v ->
            val tag = placeTags[v.id]
            v.copy(gymPlaceId = tag?.gymPlaceId, gymName = tag?.gymName)
        }
    val manual = manualVisits
        .filter { month.containsSessionStart(it.start) }
        .map { m ->
            val v = m.toGymVisit()
            val tag = placeTags[v.id]
            if (tag != null) v.copy(gymPlaceId = tag.gymPlaceId, gymName = tag.gymName) else v
        }

    val reset = (hcVisits + manual).map { it.copy(overlapSuppressed = false, countsAsVisit = true) }
    val visits = applyVisitCountingPolicy(
        visits = applyOverlapPolicy(reset),
        mode = visitMode,
        zoneId = month.zoneId,
    ).sortedByDescending { it.start }

    val status = when {
        hcSummary.sourceStatus == SourceStatus.NO_DATA && visits.isNotEmpty() -> SourceStatus.OK
        hcSummary.sourceStatus == SourceStatus.USER_REPORTED_MISSING_BRIDGE && hcVisits.isNotEmpty() ->
            SourceStatus.OK
        hcSummary.sourceStatus == SourceStatus.OK && visits.isEmpty() -> SourceStatus.NO_DATA
        else -> hcSummary.sourceStatus
    }

    return hcSummary.copy(
        visitCount = countVisits(visits),
        visits = visits,
        lastSyncedAt = now,
        sourceStatus = status,
    )
}
