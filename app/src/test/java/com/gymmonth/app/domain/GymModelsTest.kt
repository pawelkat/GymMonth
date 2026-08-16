package com.gymmonth.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class GymModelsTest {

    private val zone = ZoneId.of("UTC")

    @Test
    fun emptySummary_defaults() {
        val month = MonthRange.of(2026, 8, zone)
        val at = Instant.parse("2026-08-12T12:00:00Z")
        val empty = MonthlyGymSummary.empty(month, at)
        assertEquals(0, empty.visitCount)
        assertTrue(empty.visits.isEmpty())
        assertEquals(SourceStatus.NO_DATA, empty.sourceStatus)
        assertEquals(false, empty.userReportedMissingData)
    }

    @Test
    fun summary_visitCountMatchesPolicy() {
        val month = MonthRange.of(2026, 8, zone)
        val v1 = GymVisit(
            id = "1",
            start = Instant.parse("2026-08-01T10:00:00Z"),
            end = Instant.parse("2026-08-01T11:00:00Z"),
            exerciseType = GymExerciseKind.STRENGTH_TRAINING,
            dataOriginPackage = "com.sec.android.app.shealth",
            countsAsVisit = true,
        )
        val v2 = v1.copy(id = "2", countsAsVisit = false, overlapSuppressed = true)
        val summary = MonthlyGymSummary(
            month = month,
            visitCount = 1,
            visits = listOf(v1, v2),
            lastSyncedAt = Instant.parse("2026-08-12T12:00:00Z"),
            sourceStatus = SourceStatus.OK,
        )
        assertEquals(1, summary.visitCount)
        assertEquals(2, summary.visits.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun visit_endBeforeStart_rejected() {
        GymVisit(
            id = "bad",
            start = Instant.parse("2026-08-01T12:00:00Z"),
            end = Instant.parse("2026-08-01T11:00:00Z"),
            exerciseType = GymExerciseKind.WEIGHTLIFTING,
            dataOriginPackage = "test",
        )
    }
}
