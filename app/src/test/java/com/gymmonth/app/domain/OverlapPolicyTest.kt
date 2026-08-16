package com.gymmonth.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class OverlapPolicyTest {

    private fun visit(
        id: String,
        startEpoch: Long,
        endEpoch: Long,
    ): GymVisit =
        GymVisit(
            id = id,
            start = Instant.ofEpochSecond(startEpoch),
            end = Instant.ofEpochSecond(endEpoch),
            exerciseType = GymExerciseKind.STRENGTH_TRAINING,
            dataOriginPackage = "com.sec.android.app.shealth",
        )

    @Test
    fun nonOverlapping_unchanged() {
        val a = visit("a", 0, 100)
        val b = visit("b", 100, 200)
        val out = applyOverlapPolicy(listOf(a, b))
        assertFalse(out.any { it.overlapSuppressed })
        assertTrue(out.all { it.countsAsVisit })
        assertEquals(2, out.size)
    }

    @Test
    fun overlapping_creditsLongerSessionOnly() {
        val short = visit("short", 0, 50)
        val long = visit("long", 25, 200)
        val out = applyOverlapPolicy(listOf(short, long))
        val kept = out.single { it.id == "long" }
        val suppressed = out.single { it.id == "short" }
        assertTrue(kept.countsAsVisit)
        assertFalse(kept.overlapSuppressed)
        assertTrue(suppressed.overlapSuppressed)
        assertFalse(suppressed.countsAsVisit)
    }

    @Test
    fun overlapping_sameDuration_earlierStartWins() {
        val first = visit("first", 0, 100)
        val second = visit("second", 50, 150)
        // durations both 100s — earlier start (first) keeps visit
        val out = applyOverlapPolicy(listOf(second, first))
        assertTrue(out.single { it.id == "first" }.countsAsVisit)
        assertFalse(out.single { it.id == "second" }.countsAsVisit)
        assertTrue(out.single { it.id == "second" }.overlapSuppressed)
    }

    @Test
    fun emptyAndSingle_passthrough() {
        assertTrue(applyOverlapPolicy(emptyList()).isEmpty())
        val one = visit("only", 0, 10)
        assertEquals(listOf(one), applyOverlapPolicy(listOf(one)))
    }

    @Test
    fun touchingAtBoundary_notOverlap() {
        // [0,100) and [100,200) do not overlap
        val a = visit("a", 0, 100)
        val b = visit("b", 100, 200)
        val out = applyOverlapPolicy(listOf(a, b))
        assertFalse(out.any { it.overlapSuppressed })
    }
}
