package com.gymmonth.app.domain

/**
 * One month for home statistics (target vs result bar chart).
 */
data class MonthStatPoint(
    val month: MonthRange,
    /** Short label, e.g. "Aug" or "2026-08". */
    val label: String,
    val resultVisits: Int,
    val targetVisits: Int,
    val isCurrentMonth: Boolean = false,
) {
    val hasTarget: Boolean get() = targetVisits > 0
}
