package com.gymmonth.app.sync

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MidnightRolloverTest {

    private val zone = ZoneId.of("Europe/Paris")

    @Test
    fun evening_schedulesTonightPlusFiveMinutes() {
        val now = ZonedDateTime.of(2026, 8, 16, 21, 0, 0, 0, zone)
        val delay = MidnightRollover.delayUntilNext(now)
        // Next midnight 2026-08-17 00:05
        assertEquals(3 * 60 + 5, delay.toMinutes())
    }

    @Test
    fun justAfterRollover_schedulesFollowingNight() {
        val now = ZonedDateTime.of(2026, 8, 17, 0, 10, 0, 0, zone)
        val delay = MidnightRollover.delayUntilNext(now)
        // Next is 2026-08-18 00:05 → 23h55m
        assertEquals(23 * 60 + 55, delay.toMinutes())
    }

    @Test
    fun delayIsAlwaysPositive() {
        val now = ZonedDateTime.of(2026, 8, 1, 0, 5, 0, 0, zone)
        val delay = MidnightRollover.delayUntilNext(now)
        assertTrue(delay.toMinutes() >= 1)
    }
}
