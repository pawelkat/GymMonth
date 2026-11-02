package com.gymmonth.app.domain

import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * A gym visit entered by hand in GymMonth. App-local only — never written to Health Connect.
 */
data class ManualVisit(
    val id: String,
    val start: Instant,
    val end: Instant,
    val gymPlaceId: String? = null,
    val gymName: String? = null,
    val createdAtEpochMs: Long = 0L,
) {
    init {
        require(id.startsWith(MANUAL_VISIT_ID_PREFIX)) { "manual visit id must start with $MANUAL_VISIT_ID_PREFIX" }
        require(!end.isBefore(start)) { "end must be >= start" }
    }

    fun toGymVisit(): GymVisit = GymVisit(
        id = id,
        start = start,
        end = end,
        exerciseType = GymExerciseKind.STRENGTH_TRAINING,
        dataOriginPackage = MANUAL_VISIT_ORIGIN_PACKAGE,
        title = null,
        gymPlaceId = gymPlaceId,
        gymName = gymName,
    )

    companion object {
        val DEFAULT_DURATION: Duration = Duration.ofMinutes(60)

        fun create(
            start: Instant,
            duration: Duration = DEFAULT_DURATION,
            gymPlaceId: String? = null,
            gymName: String? = null,
            nowEpochMs: Long = System.currentTimeMillis(),
        ): ManualVisit = ManualVisit(
            id = MANUAL_VISIT_ID_PREFIX + UUID.randomUUID().toString(),
            start = start,
            end = start.plus(if (duration.isNegative || duration.isZero) DEFAULT_DURATION else duration),
            gymPlaceId = gymPlaceId,
            gymName = gymName?.trim()?.ifEmpty { null },
            createdAtEpochMs = nowEpochMs,
        )
    }
}

const val MANUAL_VISIT_ID_PREFIX = "manual-"
const val MANUAL_VISIT_ORIGIN_PACKAGE = "com.gymmonth.app.manual"

fun isManualVisitId(id: String): Boolean = id.startsWith(MANUAL_VISIT_ID_PREFIX)

val GymVisit.isManual: Boolean get() = isManualVisitId(id)
