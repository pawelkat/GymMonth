package com.gymmonth.app.domain

/**
 * Monthly visit target progress (0f..1f).
 * [targetVisits] ≤ 0 means no target (ring hidden / empty).
 */
object TargetProgress {
    fun fraction(visitCount: Int, targetVisits: Int): Float {
        if (targetVisits <= 0) return 0f
        if (visitCount <= 0) return 0f
        val raw = visitCount.toDouble() / targetVisits.toDouble()
        return raw.coerceIn(0.0, 1.0).toFloat()
    }

    fun hasTarget(targetVisits: Int): Boolean = targetVisits > 0

    fun isComplete(visitCount: Int, targetVisits: Int): Boolean =
        hasTarget(targetVisits) && visitCount >= targetVisits
}
