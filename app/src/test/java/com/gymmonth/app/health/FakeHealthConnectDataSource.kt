package com.gymmonth.app.health

import java.time.Instant

/** In-memory Health Connect stub for JVM unit tests. */
class FakeHealthConnectDataSource(
    var sessions: List<RawExerciseSession> = emptyList(),
) : HealthConnectDataSource {

    override suspend fun listExerciseSessions(
        start: Instant,
        endExclusive: Instant,
    ): List<RawExerciseSession> {
        return sessions.filter { it.start < endExclusive && it.end > start }
    }
}
