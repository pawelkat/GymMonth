package com.gymmonth.app.health

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.gymmonth.app.domain.GymExerciseKind
import com.gymmonth.app.domain.MonthRange
import com.gymmonth.app.domain.SourceStatus
import com.gymmonth.app.domain.VisitCountingMode
import com.gymmonth.app.util.FixedClockProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GymVisitAggregatorTest {

    private val zone = ZoneId.of("Europe/Paris")
    private val month = MonthRange.of(LocalDate.of(2026, 8, 12), zone)
    private val clock = FixedClockProvider(
        instant = ZonedDateTime.of(2026, 8, 12, 12, 0, 0, 0, zone).toInstant(),
        zoneId = zone,
    )

    private fun instant(day: Int, hour: Int, minute: Int = 0): Instant =
        ZonedDateTime.of(2026, 8, day, hour, minute, 0, 0, zone).toInstant()

    private fun session(
        id: String,
        day: Int,
        hour: Int,
        type: Int = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
        durationHours: Long = 1,
        title: String? = null,
        packageName: String = "com.sec.android.app.shealth",
    ): RawExerciseSession {
        val start = instant(day, hour)
        return RawExerciseSession(
            id = id,
            exerciseType = type,
            start = start,
            end = start.plusSeconds(durationHours * 3600),
            dataOriginPackage = packageName,
            title = title,
        )
    }

    @Test
    fun countsStrengthSessionsAsVisits() = runBlocking {
        val fake = FakeHealthConnectDataSource(
            sessions = listOf(
                session("a", day = 5, hour = 10),
                session("b", day = 8, hour = 18),
            ),
        )
        val summary = GymVisitAggregator(fake, clock).aggregateMonth(month)
        assertEquals(2, summary.visitCount)
        assertEquals(2, summary.visits.size)
        assertEquals(SourceStatus.OK, summary.sourceStatus)
        assertTrue(summary.visits.all { it.exerciseType == GymExerciseKind.STRENGTH_TRAINING })
    }

    @Test
    fun ignoresNonGymExerciseTypes() = runBlocking {
        val fake = FakeHealthConnectDataSource(
            sessions = listOf(
                session(
                    id = "run",
                    day = 5,
                    hour = 10,
                    type = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
                ),
            ),
        )
        val summary = GymVisitAggregator(fake, clock).aggregateMonth(month)
        assertEquals(0, summary.visitCount)
        assertEquals(SourceStatus.NO_DATA, summary.sourceStatus)
    }

    @Test
    fun includesHiitByDefault() = runBlocking {
        val fake = FakeHealthConnectDataSource(
            sessions = listOf(
                session(
                    id = "hiit",
                    day = 6,
                    hour = 7,
                    type = ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING,
                ),
            ),
        )
        val summary = GymVisitAggregator(fake, clock).aggregateMonth(month)
        assertEquals(1, summary.visitCount)
        assertEquals(GymExerciseKind.HIIT, summary.visits.single().exerciseType)
    }

    @Test
    fun titleKeywordMatch_offByDefault() = runBlocking {
        val fake = FakeHealthConnectDataSource(
            sessions = listOf(
                session(
                    id = "generic",
                    day = 7,
                    hour = 12,
                    type = ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT,
                    title = "Gym session",
                ),
            ),
        )
        val off = GymVisitAggregator(fake, clock).aggregateMonth(month, matchTitleKeywords = false)
        assertEquals(0, off.visitCount)

        val on = GymVisitAggregator(fake, clock).aggregateMonth(month, matchTitleKeywords = true)
        assertEquals(1, on.visitCount)
        assertEquals(GymExerciseKind.OTHER, on.visits.single().exerciseType)
    }

    @Test
    fun previousMonthSession_excluded() = runBlocking {
        val july31 = ZonedDateTime.of(2026, 7, 31, 23, 30, 0, 0, zone).toInstant()
        val fake = FakeHealthConnectDataSource(
            sessions = listOf(
                RawExerciseSession(
                    id = "prev",
                    exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
                    start = july31,
                    end = july31.plusSeconds(3600),
                    dataOriginPackage = "com.sec.android.app.shealth",
                    title = null,
                ),
            ),
        )
        val summary = GymVisitAggregator(fake, clock).aggregateMonth(month)
        assertEquals(0, summary.visitCount)
    }

    @Test
    fun overlappingSessions_creditVisitOnce() = runBlocking {
        val aStart = instant(10, 10)
        val aEnd = instant(10, 11)
        val bStart = instant(10, 10, 30)
        val bEnd = instant(10, 12)
        val fake = FakeHealthConnectDataSource(
            sessions = listOf(
                RawExerciseSession(
                    id = "short",
                    exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
                    start = aStart,
                    end = aEnd,
                    dataOriginPackage = "com.sec.android.app.shealth",
                    title = null,
                ),
                RawExerciseSession(
                    id = "long",
                    exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
                    start = bStart,
                    end = bEnd,
                    dataOriginPackage = "com.sec.android.app.shealth",
                    title = null,
                ),
            ),
        )
        val summary = GymVisitAggregator(fake, clock).aggregateMonth(month)
        assertEquals(2, summary.visits.size)
        assertEquals(1, summary.visitCount)
        assertTrue(summary.visits.any { it.overlapSuppressed })
        assertTrue(summary.visits.single { it.id == "long" }.countsAsVisit)
        assertFalse(summary.visits.single { it.id == "short" }.countsAsVisit)
    }

    @Test
    fun onePerDay_mode_collapsesSameDay() = runBlocking {
        val fake = FakeHealthConnectDataSource(
            sessions = listOf(
                session("m", day = 12, hour = 9),
                session("e", day = 12, hour = 18),
                session("n", day = 13, hour = 10),
            ),
        )
        val summary = GymVisitAggregator(fake, clock).aggregateMonth(
            month = month,
            visitMode = VisitCountingMode.AT_MOST_ONE_PER_DAY,
        )
        assertEquals(3, summary.visits.size)
        assertEquals(2, summary.visitCount)
    }

    @Test
    fun perSession_countsSameDayTwice() = runBlocking {
        val fake = FakeHealthConnectDataSource(
            sessions = listOf(
                session("m", day = 12, hour = 9),
                session("e", day = 12, hour = 18),
            ),
        )
        val summary = GymVisitAggregator(fake, clock).aggregateMonth(
            month = month,
            visitMode = VisitCountingMode.PER_SESSION,
        )
        assertEquals(2, summary.visitCount)
    }
}
