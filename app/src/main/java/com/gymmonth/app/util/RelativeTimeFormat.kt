package com.gymmonth.app.util

import java.time.Duration
import java.time.Instant

/**
 * Formats absolute [lastSyncedAt] as a relative label at render time.
 * Do not schedule widget ticks solely to refresh this string.
 */
object RelativeTimeFormat {

    fun formatUpdatedAgo(lastSyncedAt: Instant, now: Instant = Instant.now()): String {
        val seconds = Duration.between(lastSyncedAt, now).seconds.coerceAtLeast(0)
        return when {
            seconds < 60 -> "updated just now"
            seconds < 3600 -> {
                val m = seconds / 60
                "updated ${m}m ago"
            }
            seconds < 86_400 -> {
                val h = seconds / 3600
                "updated ${h}h ago"
            }
            else -> {
                val d = seconds / 86_400
                "updated ${d}d ago"
            }
        }
    }
}
