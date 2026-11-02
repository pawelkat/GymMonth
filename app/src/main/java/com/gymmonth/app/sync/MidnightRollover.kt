package com.gymmonth.app.sync

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Next flex midnight (+5 min) delay for month-identity rollover.
 * Pure so unit tests can pin [now].
 */
object MidnightRollover {
    fun delayUntilNext(now: ZonedDateTime): Duration {
        var nextMidnight = LocalDate.from(now).plusDays(1)
            .atTime(LocalTime.MIDNIGHT)
            .atZone(now.zone)
            .plusMinutes(5)
        if (!nextMidnight.isAfter(now)) {
            nextMidnight = nextMidnight.plusDays(1)
        }
        return Duration.between(now, nextMidnight)
    }
}
