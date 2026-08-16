package com.gymmonth.app.domain

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Inclusive start, exclusive end, interpreted in [zoneId].
 *
 * Month attribution rule (v1): a gym session belongs entirely to the calendar
 * month that contains its start time — see [containsSessionStart].
 *
 * Boundaries are local midnights of the first day of the month and the first
 * day of the next month (device timezone).
 */
data class MonthRange(
    val year: Int,
    val month: Int,
    val start: Instant,
    val endExclusive: Instant,
    val zoneId: ZoneId,
) {
    init {
        require(month in 1..12) { "month must be 1..12, was $month" }
        require(start < endExclusive) {
            "MonthRange start ($start) must be before endExclusive ($endExclusive)"
        }
    }

    /** Stable identity for cache / month-rollover comparison, e.g. `"2026-08"`. */
    fun identityKey(): String = "%04d-%02d".format(year, month)

    /**
     * True when a session with this [sessionStart] is attributed to this month
     * (`startTime ∈ [month.start, month.endExclusive)`).
     */
    fun containsSessionStart(sessionStart: Instant): Boolean =
        !sessionStart.isBefore(start) && sessionStart.isBefore(endExclusive)

    /** Local calendar date of the month start (inclusive). */
    fun startLocalDate(): LocalDate = LocalDate.of(year, month, 1)

    /** [YearMonth] for this range. */
    fun yearMonth(): YearMonth = YearMonth.of(year, month)

    /** Previous calendar month with the same zone. */
    fun previous(): MonthRange {
        val prev = yearMonth().minusMonths(1)
        return of(prev.year, prev.monthValue, zoneId)
    }

    /** Next calendar month with the same zone. */
    fun next(): MonthRange {
        val n = yearMonth().plusMonths(1)
        return of(n.year, n.monthValue, zoneId)
    }

    companion object {
        /**
         * Calendar month containing [clock]'s instant in [zoneId], starting at
         * local midnight on the 1st.
         */
        fun current(
            zoneId: ZoneId = ZoneId.systemDefault(),
            clock: Clock = Clock.system(zoneId),
        ): MonthRange {
            val today = LocalDate.now(clock.withZone(zoneId))
            return of(today.year, today.monthValue, zoneId)
        }

        /**
         * Calendar month that contains [date] (local date in [zoneId]).
         */
        fun of(
            date: LocalDate,
            zoneId: ZoneId = ZoneId.systemDefault(),
        ): MonthRange = of(date.year, date.monthValue, zoneId)

        /**
         * Calendar month for [year]/[month] (1–12) in [zoneId].
         */
        fun of(
            year: Int,
            month: Int,
            zoneId: ZoneId = ZoneId.systemDefault(),
        ): MonthRange {
            val ym = YearMonth.of(year, month)
            val startDate = ym.atDay(1)
            val endDate = ym.plusMonths(1).atDay(1)
            val start = startDate.atStartOfDay(zoneId).toInstant()
            val endExclusive = endDate.atStartOfDay(zoneId).toInstant()
            return MonthRange(
                year = year,
                month = month,
                start = start,
                endExclusive = endExclusive,
                zoneId = zoneId,
            )
        }

        /**
         * Month that should own a session starting at [sessionStart]
         * (month containing startTime — never split across months).
         */
        fun forSessionStart(
            sessionStart: Instant,
            zoneId: ZoneId = ZoneId.systemDefault(),
        ): MonthRange {
            val localDate = sessionStart.atZone(zoneId).toLocalDate()
            return of(localDate, zoneId)
        }
    }
}
