package com.gymmonth.app.health

import java.time.Instant

/**
 * Abstraction over Health Connect exercise reads so aggregation can be
 * unit-tested without a real [androidx.health.connect.client.HealthConnectClient].
 *
 * Distance is not required for visit counting (unlike SwimWeek).
 */
interface HealthConnectDataSource {
    /**
     * Exercise sessions that may overlap [start, endExclusive).
     * Caller post-filters by startTime and gym exercise type.
     */
    suspend fun listExerciseSessions(start: Instant, endExclusive: Instant): List<RawExerciseSession>
}

/**
 * Raw HC exercise row before gym-type filtering.
 * [exerciseType] is the Health Connect [ExerciseSessionRecord] type constant.
 */
data class RawExerciseSession(
    val id: String,
    val exerciseType: Int,
    val start: Instant,
    val end: Instant,
    val dataOriginPackage: String,
    val title: String?,
    val notes: String? = null,
)
