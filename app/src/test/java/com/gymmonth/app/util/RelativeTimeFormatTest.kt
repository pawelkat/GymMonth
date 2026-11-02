package com.gymmonth.app.util

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelativeTimeFormatTest {

    private val now = Instant.parse("2026-08-16T12:00:00Z")

    @Test
    fun justNow() {
        assertEquals(
            "updated just now",
            RelativeTimeFormat.formatUpdatedAgo(now.minusSeconds(10), now),
        )
    }

    @Test
    fun minutes() {
        assertEquals(
            "updated 15m ago",
            RelativeTimeFormat.formatUpdatedAgo(now.minusSeconds(15 * 60), now),
        )
    }

    @Test
    fun hours() {
        assertEquals(
            "updated 2h ago",
            RelativeTimeFormat.formatUpdatedAgo(now.minusSeconds(2 * 3600), now),
        )
    }

    @Test
    fun days() {
        val s = RelativeTimeFormat.formatUpdatedAgo(now.minusSeconds(3 * 86_400), now)
        assertTrue(s.contains("3d"))
    }
}
