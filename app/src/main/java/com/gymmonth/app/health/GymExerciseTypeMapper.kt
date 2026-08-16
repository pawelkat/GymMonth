package com.gymmonth.app.health

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.gymmonth.app.domain.GymExerciseKind

/**
 * Maps Health Connect exercise type constants to [GymExerciseKind].
 * Default included kinds for v1 (Settings multi-select lands later).
 */
object GymExerciseTypeMapper {

    /** Strength / gym-like types counted out of the box. */
    val defaultIncludedKinds: Set<GymExerciseKind> = setOf(
        GymExerciseKind.STRENGTH_TRAINING,
        GymExerciseKind.WEIGHTLIFTING,
        GymExerciseKind.CALISTHENICS,
        GymExerciseKind.HIIT,
    )

    /**
     * @return mapped kind, or null if not a known gym type (and not title-matched).
     */
    fun toKind(exerciseType: Int): GymExerciseKind? =
        when (exerciseType) {
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING ->
                GymExerciseKind.STRENGTH_TRAINING
            ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING ->
                GymExerciseKind.WEIGHTLIFTING
            ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS ->
                GymExerciseKind.CALISTHENICS
            ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING ->
                GymExerciseKind.HIIT
            else -> null
        }

    /**
     * Optional title/notes keyword match (off by default in aggregator).
     * Keywords: gym, fitness, kraft, workout (case-insensitive).
     */
    fun titleLooksLikeGym(title: String?): Boolean {
        if (title.isNullOrBlank()) return false
        val t = title.lowercase()
        return GYM_TITLE_KEYWORDS.any { it in t }
    }

    private val GYM_TITLE_KEYWORDS = listOf(
        "gym",
        "fitness",
        "kraft",
        "workout",
        "weight",
        "strength",
    )
}
