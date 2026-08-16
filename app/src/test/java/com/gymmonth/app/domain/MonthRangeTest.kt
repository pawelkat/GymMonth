package com.gymmonth.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class MonthRangeTest {

    private val paris = ZoneId.of("Europe/Paris")
    private val newYork = ZoneId.of("America/New_York")
    private val utc = ZoneId.of("UTC")

    @Test
    fun august_containsFirstThroughLastDay() {
        val month = MonthRange.of(2026, 8, paris)

        val aug1 = ZonedDateTime.of(2026, 8, 1, 0, 0, 0, 0, paris).toInstant()
        val sep1 = ZonedDateTime.of(2026, 9, 1, 0, 0, 0, 0, paris).toInstant()

        assertEquals(aug1, month.start)
        assertEquals(sep1, month.endExclusive)
        assertEquals(2026, month.year)
        assertEquals(8, month.month)
        assertTrue(month.containsSessionStart(aug1))
        assertTrue(
            month.containsSessionStart(
                ZonedDateTime.of(2026, 8, 31, 23, 59, 0, 0, paris).toInstant(),
            ),
        )
        assertFalse(month.containsSessionStart(sep1))
        assertFalse(
            month.containsSessionStart(
                ZonedDateTime.of(2026, 7, 31, 23, 59, 0, 0, paris).toInstant(),
            ),
        )
    }

    @Test
    fun identityKey_stableForSameMonth() {
        val a = MonthRange.of(LocalDate.of(2026, 8, 5), paris)
        val b = MonthRange.of(LocalDate.of(2026, 8, 28), paris)
        assertEquals("2026-08", a.identityKey())
        assertEquals(a.identityKey(), b.identityKey())
        assertEquals(a.start, b.start)
    }

    @Test
    fun identityKey_differsAcrossMonths() {
        val a = MonthRange.of(2026, 7, utc)
        val b = MonthRange.of(2026, 8, utc)
        assertEquals("2026-07", a.identityKey())
        assertEquals("2026-08", b.identityKey())
    }

    @Test
    fun identityKey_sameCalendarMonthDifferentZones_sameKeyDifferentInstants() {
        val a = MonthRange.of(2026, 8, paris)
        val b = MonthRange.of(2026, 8, newYork)
        assertEquals(a.identityKey(), b.identityKey())
        // Local midnight differs by zone offset
        assertTrue(a.start != b.start)
    }

    @Test
    fun sessionCrossingMonthBoundary_attributedToStartMonth() {
        // Session starts Aug 31 23:30, ends Sep 1 00:20 → August
        val sessionStart = ZonedDateTime.of(2026, 8, 31, 23, 30, 0, 0, paris).toInstant()
        val monthOfStart = MonthRange.forSessionStart(sessionStart, paris)

        val september = MonthRange.of(2026, 9, paris)
        assertFalse(september.containsSessionStart(sessionStart))
        assertTrue(monthOfStart.containsSessionStart(sessionStart))
        assertEquals("2026-08", monthOfStart.identityKey())
    }

    @Test
    fun current_usesClockInstant() {
        val fixed = ZonedDateTime.of(2026, 8, 12, 15, 0, 0, 0, paris).toInstant()
        val clock = Clock.fixed(fixed, paris)
        val month = MonthRange.current(paris, clock)
        assertEquals(
            ZonedDateTime.of(2026, 8, 1, 0, 0, 0, 0, paris).toInstant(),
            month.start,
        )
        assertEquals("2026-08", month.identityKey())
    }

    @Test
    fun previous_and_next_chain() {
        val aug = MonthRange.of(2026, 8, utc)
        val jul = aug.previous()
        val sep = aug.next()
        assertEquals("2026-07", jul.identityKey())
        assertEquals("2026-09", sep.identityKey())
        assertEquals(aug.start, jul.endExclusive)
        assertEquals(sep.start, aug.endExclusive)
    }

    @Test
    fun februaryLeapYear() {
        val feb = MonthRange.of(2024, 2, utc)
        assertEquals(
            ZonedDateTime.of(2024, 2, 1, 0, 0, 0, 0, utc).toInstant(),
            feb.start,
        )
        assertEquals(
            ZonedDateTime.of(2024, 3, 1, 0, 0, 0, 0, utc).toInstant(),
            feb.endExclusive,
        )
        assertTrue(
            feb.containsSessionStart(
                ZonedDateTime.of(2024, 2, 29, 12, 0, 0, 0, utc).toInstant(),
            ),
        )
    }

    @Test
    fun dstSpringForward_europeParis_monthContainingTransition() {
        // DST starts 2026-03-29; March still has correct local midnights.
        val march = MonthRange.of(2026, 3, paris)
        assertEquals(
            ZonedDateTime.of(2026, 3, 1, 0, 0, 0, 0, paris).toInstant(),
            march.start,
        )
        assertEquals(
            ZonedDateTime.of(2026, 4, 1, 0, 0, 0, 0, paris).toInstant(),
            march.endExclusive,
        )
        // March has 31 days; spring-forward shortens one day → 31*24 - 1 hours
        val hours = java.time.Duration.between(march.start, march.endExclusive).toHours()
        assertEquals(31L * 24 - 1, hours)
    }

    @Test
    fun dstFallBack_europeParis_monthContainingTransition() {
        // DST ends 2026-10-25; October has 31 days + 1 hour
        val oct = MonthRange.of(2026, 10, paris)
        val hours = java.time.Duration.between(oct.start, oct.endExclusive).toHours()
        assertEquals(31L * 24 + 1, hours)
    }

    @Test
    fun yearBoundary_decemberToJanuary() {
        val dec = MonthRange.of(2026, 12, utc)
        val jan = dec.next()
        assertEquals("2027-01", jan.identityKey())
        assertEquals(dec.endExclusive, jan.start)
    }
}
