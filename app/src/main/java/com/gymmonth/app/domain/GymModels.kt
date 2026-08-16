package com.gymmonth.app.domain

import java.time.Instant

/**
 * Strength / gym-like exercise kinds recognized by GymMonth.
 * Maps from Health Connect [ExerciseSessionRecord] types in the aggregator (PR 3).
 */
enum class GymExerciseKind {
    /** Health Connect EXERCISE_TYPE_STRENGTH_TRAINING */
    STRENGTH_TRAINING,

    /** Health Connect EXERCISE_TYPE_WEIGHTLIFTING */
    WEIGHTLIFTING,

    /** Health Connect EXERCISE_TYPE_CALISTHENICS */
    CALISTHENICS,

    /** Health Connect EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING (optional in Settings) */
    HIIT,

    /**
     * Other types the user enabled, generic "Workout", or title-keyword matches.
     * Kept so diagnostics can still show the session.
     */
    OTHER,
}

/**
 * Sync / data availability status for a monthly summary.
 * Semantics match SwimWeek's SourceStatus for onboarding UX parity.
 */
enum class SourceStatus {
    OK,
    HEALTH_CONNECT_UNAVAILABLE,
    PERMISSIONS_MISSING,
    NO_DATA,

    /**
     * User manually reported missing data from Diagnostics while HC returned
     * no gym sessions. Not set by automatic heuristics in v1.
     */
    USER_REPORTED_MISSING_BRIDGE,

    ERROR,
}

/**
 * One gym-like exercise session after domain resolution (no Health Connect types).
 */
data class GymVisit(
    val id: String,
    val start: Instant,
    val end: Instant,
    val exerciseType: GymExerciseKind,
    val dataOriginPackage: String,
    val title: String? = null,
    /** App-local gym place id (from GymPlaceStore), if tagged. */
    val gymPlaceId: String? = null,
    /** App-local gym display name, if tagged. */
    val gymName: String? = null,
    /** True when this visit was zeroed by [applyOverlapPolicy]. */
    val overlapSuppressed: Boolean = false,
    /**
     * Whether this session contributes to [MonthlyGymSummary.visitCount]
     * after overlap + [VisitCountingMode] policy.
     */
    val countsAsVisit: Boolean = true,
) {
    init {
        require(!end.isBefore(start)) { "end must be >= start" }
    }
}

/**
 * Derived monthly total shown on widget / home. On-device only.
 */
data class MonthlyGymSummary(
    val month: MonthRange,
    /** Unique visits after overlap + visit counting policy. */
    val visitCount: Int,
    val visits: List<GymVisit>,
    val lastSyncedAt: Instant,
    val sourceStatus: SourceStatus,
    val userReportedMissingData: Boolean = false,
) {
    init {
        require(visitCount >= 0)
        require(visitCount <= visits.size) {
            "visitCount ($visitCount) cannot exceed visits.size (${visits.size})"
        }
    }

    companion object {
        fun empty(
            month: MonthRange,
            lastSyncedAt: Instant,
            sourceStatus: SourceStatus = SourceStatus.NO_DATA,
        ): MonthlyGymSummary =
            MonthlyGymSummary(
                month = month,
                visitCount = 0,
                visits = emptyList(),
                lastSyncedAt = lastSyncedAt,
                sourceStatus = sourceStatus,
            )
    }
}
