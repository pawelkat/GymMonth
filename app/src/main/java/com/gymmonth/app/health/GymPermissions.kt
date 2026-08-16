package com.gymmonth.app.health

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord

/**
 * Health Connect permission contract for GymMonth v1.
 *
 * Required: exercise sessions (for monthly gym visit counts).
 * Recommended: background read (widget refresh via WorkManager — later PRs).
 * Not requested: DISTANCE (visits, not meters), HISTORY (current month fits
 * default 30-day window for partial months; full-month reads may need care),
 * write types.
 */
object GymPermissions {

    val required: Set<String> = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
    )

    val recommendedBackground: Set<String> = setOf(
        HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND,
    )

    /** Required + optional background — used by the permission request launcher. */
    val allRequested: Set<String> = required + recommendedBackground
}
