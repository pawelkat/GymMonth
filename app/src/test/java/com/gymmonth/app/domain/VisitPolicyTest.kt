package com.gymmonth.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class VisitPolicyTest {

    private val zone = ZoneId.of("Europe/Paris")

    private fun visit(
        id: String,
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 10,
        durationHours: Long = 1,
    ): GymVisit {
        val start = ZonedDateTime.of(year, month, day, hour, 0, 0, 0, zone).toInstant()
        val end = start.plusSeconds(durationHours * 3600)
        return GymVisit(
            id = id,
            start = start,
            end = end,
            exerciseType = GymExerciseKind.STRENGTH_TRAINING,
            dataOriginPackage = "com.sec.android.app.shealth",
        )
    }

    @Test
    fun perSession_countsEachNonSuppressed() {
        val a = visit("a", 2026, 8, 1)
        val b = visit("b", 2026, 8, 1, hour = 18)
        val c = visit("c", 2026, 8, 2)
        val out = applyVisitCountingPolicy(
            listOf(a, b, c),
            VisitCountingMode.PER_SESSION,
            zone,
        )
        assertEquals(3, countVisits(out))
        assertTrue(out.all { it.countsAsVisit })
    }

    @Test
    fun perSession_excludesOverlapSuppressed() {
        val kept = visit("kept", 2026, 8, 1)
        val suppressed = visit("sup", 2026, 8, 1, hour = 10).copy(
            start = Instant.parse("2026-08-01T08:00:00Z"),
            end = Instant.parse("2026-08-01T09:00:00Z"),
            overlapSuppressed = true,
            countsAsVisit = false,
        )
        val out = applyVisitCountingPolicy(
            listOf(kept, suppressed),
            VisitCountingMode.PER_SESSION,
            zone,
        )
        assertEquals(1, countVisits(out))
        assertFalse(out.single { it.id == "sup" }.countsAsVisit)
    }

    @Test
    fun atMostOnePerDay_sameDay_countsOnce() {
        val morning = visit("m", 2026, 8, 12, hour = 9)
        val evening = visit("e", 2026, 8, 12, hour = 18)
        val nextDay = visit("n", 2026, 8, 13, hour = 10)
        val out = applyVisitCountingPolicy(
            listOf(evening, morning, nextDay),
            VisitCountingMode.AT_MOST_ONE_PER_DAY,
            zone,
        )
        assertEquals(2, countVisits(out))
        // Earliest on Aug 12 keeps credit
        assertTrue(out.single { it.id == "m" }.countsAsVisit)
        assertFalse(out.single { it.id == "e" }.countsAsVisit)
        assertTrue(out.single { it.id == "n" }.countsAsVisit)
    }

    @Test
    fun atMostOnePerDay_stillListsAllSessions() {
        val a = visit("a", 2026, 8, 5, hour = 8)
        val b = visit("b", 2026, 8, 5, hour = 20)
        val out = applyVisitCountingPolicy(
            listOf(a, b),
            VisitCountingMode.AT_MOST_ONE_PER_DAY,
            zone,
        )
        assertEquals(2, out.size)
        assertEquals(1, countVisits(out))
    }

    @Test
    fun atMostOnePerDay_skipsOverlapSuppressedWhenPickingKeeper() {
        // Overlapping double-export on day 1: only non-suppressed should be keeper candidate
        val primary = visit("primary", 2026, 8, 1, hour = 10)
        val duplicate = visit("dup", 2026, 8, 1, hour = 10).copy(
            overlapSuppressed = true,
            countsAsVisit = false,
        )
        val evening = visit("eve", 2026, 8, 1, hour = 19)
        val out = applyVisitCountingPolicy(
            listOf(duplicate, evening, primary),
            VisitCountingMode.AT_MOST_ONE_PER_DAY,
            zone,
        )
        assertEquals(1, countVisits(out))
        assertTrue(out.single { it.id == "primary" }.countsAsVisit)
        assertFalse(out.single { it.id == "dup" }.countsAsVisit)
        assertFalse(out.single { it.id == "eve" }.countsAsVisit)
    }

    @Test
    fun empty_passthrough() {
        assertTrue(
            applyVisitCountingPolicy(
                emptyList(),
                VisitCountingMode.AT_MOST_ONE_PER_DAY,
                zone,
            ).isEmpty(),
        )
    }
}
