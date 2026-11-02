package com.gymmonth.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisitMergeTest {

    private val zone = ZoneId.of("Europe/Zurich")
    private val month = MonthRange.of(LocalDate.of(2026, 11, 2), zone)
    private val now = ZonedDateTime.of(2026, 11, 2, 12, 0, 0, 0, zone).toInstant()

    private fun at(day: Int, hour: Int, monthNum: Int = 11): Instant =
        ZonedDateTime.of(2026, monthNum, day, hour, 0, 0, 0, zone).toInstant()

    private fun hc(id: String, day: Int, hour: Int) = GymVisit(
        id = id,
        start = at(day, hour),
        end = at(day, hour + 1),
        exerciseType = GymExerciseKind.STRENGTH_TRAINING,
        dataOriginPackage = "com.sec.android.app.shealth",
    )

    private fun manual(id: String, day: Int, hour: Int, monthNum: Int = 11) = ManualVisit(
        id = "manual-$id",
        start = at(day, hour, monthNum),
        end = at(day, hour + 1, monthNum),
        gymPlaceId = "puregym",
        gymName = "PureGym",
    )

    private fun hcSummary(visits: List<GymVisit>) = MonthlyGymSummary(
        month = month,
        visitCount = visits.size,
        visits = visits,
        lastSyncedAt = now,
        sourceStatus = if (visits.isEmpty()) SourceStatus.NO_DATA else SourceStatus.OK,
    )

    @Test
    fun manualVisitsAddToVisitCount() {
        val merged = mergeManualVisits(
            hcSummary = hcSummary(listOf(hc("a", 1, 10))),
            manualVisits = listOf(manual("x", 2, 18)),
            placeTags = emptyMap(),
            visitMode = VisitCountingMode.PER_SESSION,
        )
        assertEquals(2, merged.visitCount)
        assertEquals(2, merged.visits.size)
        assertEquals("manual-x", merged.visits.first().id) // newest first
        assertEquals("PureGym", merged.visits.first().gymName)
    }

    @Test
    fun manualOnlyMonthBecomesOk() {
        val merged = mergeManualVisits(
            hcSummary = hcSummary(emptyList()),
            manualVisits = listOf(manual("x", 2, 8)),
            placeTags = emptyMap(),
            visitMode = VisitCountingMode.PER_SESSION,
        )
        assertEquals(1, merged.visitCount)
        assertEquals(SourceStatus.OK, merged.sourceStatus)
    }

    @Test
    fun manualOutsideMonthIgnored() {
        val merged = mergeManualVisits(
            hcSummary = hcSummary(emptyList()),
            manualVisits = listOf(manual("oct", 30, 10, monthNum = 10)),
            placeTags = emptyMap(),
            visitMode = VisitCountingMode.PER_SESSION,
        )
        assertEquals(0, merged.visitCount)
        assertEquals(SourceStatus.NO_DATA, merged.sourceStatus)
    }

    @Test
    fun overlappingManualAndHcCreditedOnce() {
        val merged = mergeManualVisits(
            hcSummary = hcSummary(listOf(hc("a", 2, 18))),
            manualVisits = listOf(manual("x", 2, 18)),
            placeTags = emptyMap(),
            visitMode = VisitCountingMode.PER_SESSION,
        )
        assertEquals(1, merged.visitCount)
        assertEquals(1, merged.visits.count { it.overlapSuppressed })
    }

    @Test
    fun onePerDayModeAppliesToManuals() {
        val merged = mergeManualVisits(
            hcSummary = hcSummary(listOf(hc("a", 2, 7))),
            manualVisits = listOf(manual("x", 2, 18)),
            placeTags = emptyMap(),
            visitMode = VisitCountingMode.AT_MOST_ONE_PER_DAY,
        )
        assertEquals(1, merged.visitCount)
    }

    @Test
    fun remergeIsIdempotentAndSurvivesHcRefresh() {
        val manuals = listOf(manual("x", 3, 18))
        val first = mergeManualVisits(hcSummary(listOf(hc("a", 1, 10))), manuals, emptyMap(), VisitCountingMode.PER_SESSION)
        // Re-merge from the already-merged summary (local recompute path)
        val second = mergeManualVisits(first, manuals, emptyMap(), VisitCountingMode.PER_SESSION)
        assertEquals(first.visits.map { it.id }, second.visits.map { it.id })
        assertEquals(2, second.visitCount)
        // Fresh HC aggregate with a new session: manual is re-added
        val third = mergeManualVisits(
            hcSummary(listOf(hc("a", 1, 10), hc("b", 4, 10))),
            manuals,
            emptyMap(),
            VisitCountingMode.PER_SESSION,
        )
        assertEquals(3, third.visitCount)
        assertTrue(third.visits.any { it.id == "manual-x" })
    }

    @Test
    fun deletedManualDisappearsOnRemerge() {
        val first = mergeManualVisits(hcSummary(listOf(hc("a", 1, 10))), listOf(manual("x", 3, 18)), emptyMap(), VisitCountingMode.PER_SESSION)
        val after = mergeManualVisits(first, emptyList(), emptyMap(), VisitCountingMode.PER_SESSION)
        assertEquals(1, after.visitCount)
        assertFalse(after.visits.any { it.isManual })
    }

    @Test
    fun placeTagsApplyToHcVisitsAndClear() {
        val tags = mapOf("a" to SessionGymPlace("a", "puregym", "PureGym"))
        val tagged = mergeManualVisits(hcSummary(listOf(hc("a", 1, 10))), emptyList(), tags, VisitCountingMode.PER_SESSION)
        assertEquals("puregym", tagged.visits.single().gymPlaceId)
        assertEquals("PureGym", tagged.visits.single().gymName)
        val cleared = mergeManualVisits(tagged, emptyList(), emptyMap(), VisitCountingMode.PER_SESSION)
        assertNull(cleared.visits.single().gymName)
    }

    @Test
    fun permissionsMissingStatusKeptButManualsCounted() {
        val base = MonthlyGymSummary.empty(month, now, SourceStatus.PERMISSIONS_MISSING)
        val merged = mergeManualVisits(base, listOf(manual("x", 2, 9)), emptyMap(), VisitCountingMode.PER_SESSION)
        assertEquals(SourceStatus.PERMISSIONS_MISSING, merged.sourceStatus)
        assertEquals(1, merged.visitCount)
    }
}
