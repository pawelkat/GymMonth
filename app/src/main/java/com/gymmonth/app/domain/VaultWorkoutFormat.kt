package com.gymmonth.app.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * How a gym visit is written to the Obsidian Daily note (pure; unit-tested).
 *
 * `- Gym · Strength · PureGym · 18:00–19:00 · #gymmonth <!-- gymmonth:id=manual-… -->`
 *
 * The trailing HTML comment is invisible in Obsidian preview and is the dedupe key, so the
 * same visit (HC or manual) is never logged twice.
 */
object VaultWorkoutFormat {
    const val HEADING = "## Workouts"
    const val TAG = "#gymmonth"

    /** Daily notes and timestamps follow the vault's home zone. */
    val VAULT_ZONE: ZoneId = ZoneId.of("Europe/Zurich")

    private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun dedupeKey(visitId: String): String = "gymmonth:id=$visitId"

    fun dailyDate(visit: GymVisit, zone: ZoneId = VAULT_ZONE): LocalDate =
        visit.start.atZone(zone).toLocalDate()

    fun shortKind(kind: GymExerciseKind): String = when (kind) {
        GymExerciseKind.STRENGTH_TRAINING -> "Strength"
        GymExerciseKind.WEIGHTLIFTING -> "Weightlifting"
        GymExerciseKind.CALISTHENICS -> "Calisthenics"
        GymExerciseKind.HIIT -> "HIIT"
        GymExerciseKind.OTHER -> "Workout"
    }

    fun bullet(visit: GymVisit, zone: ZoneId = VAULT_ZONE): String {
        val start = visit.start.atZone(zone)
        val end = visit.end.atZone(zone)
        val place = visit.gymName?.replace('\n', ' ')?.trim()?.takeIf { it.isNotEmpty() }
        val parts = listOfNotNull(
            "Gym",
            shortKind(visit.exerciseType),
            place,
            "${HHMM.format(start)}–${HHMM.format(end)}",
            TAG,
        )
        return "- ${parts.joinToString(" · ")} <!-- ${dedupeKey(visit.id)} -->"
    }

    /** Visits worth logging: not zeroed by overlap policy. */
    fun shouldLog(visit: GymVisit): Boolean = !visit.overlapSuppressed
}
