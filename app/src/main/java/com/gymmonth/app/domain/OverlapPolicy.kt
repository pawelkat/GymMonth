package com.gymmonth.app.domain

/**
 * When two gym sessions overlap in time, credit as a visit once:
 * keep the longer session (tie → earlier startTime; then lower id);
 * mark the other [GymVisit.overlapSuppressed] and [GymVisit.countsAsVisit] = false.
 *
 * Pure domain helper — used by the Health Connect aggregator (PR 3).
 * Mirrors SwimWeek distance overlap policy, adapted for visit counting.
 */
fun applyOverlapPolicy(visits: List<GymVisit>): List<GymVisit> {
    if (visits.size <= 1) return visits

    val sorted = visits.sortedWith(compareBy({ it.start }, { it.id }))
    val result = sorted.map { it }.toMutableList()
    val suppressed = BooleanArray(result.size)

    for (i in result.indices) {
        if (suppressed[i]) continue
        for (j in (i + 1) until result.size) {
            if (suppressed[j]) continue
            if (!overlaps(result[i], result[j])) continue

            val a = result[i]
            val b = result[j]
            val aDuration = a.end.toEpochMilli() - a.start.toEpochMilli()
            val bDuration = b.end.toEpochMilli() - b.start.toEpochMilli()

            val suppressIndex = when {
                aDuration > bDuration -> j
                bDuration > aDuration -> i
                // tie: earlier start keeps visit; if same start, lower id keeps it
                a.start.isBefore(b.start) -> j
                b.start.isBefore(a.start) -> i
                a.id <= b.id -> j
                else -> i
            }

            result[suppressIndex] = result[suppressIndex].copy(
                overlapSuppressed = true,
                countsAsVisit = false,
            )
            suppressed[suppressIndex] = true
            if (suppressIndex == i) break
        }
    }

    // Preserve original encounter order is less important than stable id lookup;
    // return in start/id order (same as SwimWeek).
    return result
}

private fun overlaps(a: GymVisit, b: GymVisit): Boolean {
    // [start, end) intervals
    return a.start.isBefore(b.end) && b.start.isBefore(a.end)
}
