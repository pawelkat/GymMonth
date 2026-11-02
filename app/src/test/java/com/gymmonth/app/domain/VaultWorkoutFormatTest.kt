package com.gymmonth.app.domain

import com.vaultwrite.DailyNoteAppender
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultWorkoutFormatTest {
    // 2026-11-02 17:00Z = 18:00 Europe/Zurich (CET, UTC+1)
    private val start = Instant.parse("2026-11-02T17:00:00Z")
    private val end = Instant.parse("2026-11-02T18:00:00Z")

    private fun visit(
        id: String = "manual-abc",
        gym: String? = "PureGym",
        kind: GymExerciseKind = GymExerciseKind.STRENGTH_TRAINING,
        s: Instant = start,
        e: Instant = end,
    ) = GymVisit(id, s, e, kind, "pkg", gymName = gym)

    @Test
    fun bulletMatchesAgreedFormat() {
        assertEquals(
            "- Gym · Strength · PureGym · 18:00–19:00 · #gymmonth <!-- gymmonth:id=manual-abc -->",
            VaultWorkoutFormat.bullet(visit()),
        )
    }

    @Test
    fun bulletWithoutPlaceOmitsIt() {
        assertEquals(
            "- Gym · HIIT · 18:00–19:00 · #gymmonth <!-- gymmonth:id=hc-1 -->",
            VaultWorkoutFormat.bullet(visit(id = "hc-1", gym = "  ", kind = GymExerciseKind.HIIT)),
        )
    }

    @Test
    fun summerTimeUsesZurichOffset() {
        val v = visit(s = Instant.parse("2026-07-01T16:30:00Z"), e = Instant.parse("2026-07-01T17:15:00Z"))
        assertTrue(VaultWorkoutFormat.bullet(v).contains("18:30–19:15"))
    }

    @Test
    fun dailyDateIsZurichLocalDate() {
        // 23:30Z on Nov 2 = 00:30 Nov 3 in Zurich
        val v = visit(s = Instant.parse("2026-11-02T23:30:00Z"), e = Instant.parse("2026-11-03T00:30:00Z"))
        assertEquals(LocalDate.of(2026, 11, 3), VaultWorkoutFormat.dailyDate(v))
    }

    @Test
    fun overlapSuppressedNotLogged() {
        assertTrue(VaultWorkoutFormat.shouldLog(visit()))
        assertFalse(VaultWorkoutFormat.shouldLog(visit().copy(overlapSuppressed = true)))
    }

    @Test
    fun appendsUnderWorkoutsAndDedupesById() {
        val v = visit()
        val daily = "# 2026-11-02\n\n## Notes\n\n- n\n\n## Links\n"
        val once = DailyNoteAppender.append(
            daily, LocalDate.of(2026, 11, 2), VaultWorkoutFormat.HEADING,
            VaultWorkoutFormat.bullet(v), VaultWorkoutFormat.dedupeKey(v.id),
        )
        assertTrue(once.markdown.endsWith("## Workouts\n\n${VaultWorkoutFormat.bullet(v)}\n"))
        // Retagging the place changes the bullet text, but the id key still dedupes.
        val again = DailyNoteAppender.append(
            once.markdown, LocalDate.of(2026, 11, 2), VaultWorkoutFormat.HEADING,
            VaultWorkoutFormat.bullet(v.copy(gymName = "Other")), VaultWorkoutFormat.dedupeKey(v.id),
        )
        assertTrue(again.duplicate)
    }
}
