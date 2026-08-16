package com.gymmonth.app.domain

import java.time.LocalDate
import java.time.ZoneId

/**
 * How multiple sessions map to visit counts.
 *
 * **Default (v1):** [PER_SESSION] — each non-suppressed session counts as one visit.
 * Settings may switch to [AT_MOST_ONE_PER_DAY] so same-day multi-session = 1 visit.
 */
enum class VisitCountingMode {
    /** One visit per qualifying session (after overlap policy). */
    PER_SESSION,

    /**
     * At most one visit per local calendar day (device timezone of the month).
     * When multiple sessions fall on the same day, the earliest start keeps
     * [GymVisit.countsAsVisit]; others are marked false (still listed in diagnostics).
     */
    AT_MOST_ONE_PER_DAY,
}

/**
 * Apply visit counting mode to a list of visits (typically already overlap-resolved).
 *
 * - Visits with [GymVisit.overlapSuppressed] or already `countsAsVisit == false` stay false.
 * - Remaining visits are re-evaluated under [mode].
 * - Returns the same order as input (stable for UI lists).
 */
fun applyVisitCountingPolicy(
    visits: List<GymVisit>,
    mode: VisitCountingMode,
    zoneId: ZoneId,
): List<GymVisit> {
    if (visits.isEmpty()) return visits

    return when (mode) {
        VisitCountingMode.PER_SESSION ->
            visits.map { v ->
                if (v.overlapSuppressed) {
                    v.copy(countsAsVisit = false)
                } else {
                    v.copy(countsAsVisit = true)
                }
            }

        VisitCountingMode.AT_MOST_ONE_PER_DAY -> {
            // Group by local date; earliest start (then lower id) keeps the visit credit.
            val eligible = visits
                .filter { !it.overlapSuppressed }
                .sortedWith(compareBy({ it.start }, { it.id }))
            val keeperIds = mutableSetOf<String>()
            val seenDays = mutableSetOf<LocalDate>()
            for (v in eligible) {
                val day = v.start.atZone(zoneId).toLocalDate()
                if (day !in seenDays) {
                    seenDays.add(day)
                    keeperIds.add(v.id)
                }
            }
            visits.map { v ->
                val counts = !v.overlapSuppressed && v.id in keeperIds
                v.copy(countsAsVisit = counts)
            }
        }
    }
}

/** Count of visits that contribute to the monthly total. */
fun countVisits(visits: List<GymVisit>): Int = visits.count { it.countsAsVisit }
