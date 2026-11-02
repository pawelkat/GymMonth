package com.gymmonth.app.health

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.gymmonth.app.domain.GymExerciseKind

/**
 * Maps Health Connect exercise type constants to [GymExerciseKind].
 *
 * Samsung Health often exports Watch workouts that have no exact HC type
 * (Push-ups, Weight machine, Circuits, generic “Workout”) as
 * [ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT]. Those are counted.
 */
object GymExerciseTypeMapper {

    /**
     * Types counted out of the box.
     * [GymExerciseKind.OTHER] = HC OTHER_WORKOUT / unknown — needed for Watch 7.
     */
    val defaultIncludedKinds: Set<GymExerciseKind> = setOf(
        GymExerciseKind.STRENGTH_TRAINING,
        GymExerciseKind.WEIGHTLIFTING,
        GymExerciseKind.CALISTHENICS,
        GymExerciseKind.HIIT,
        GymExerciseKind.OTHER,
    )

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
            ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT ->
                GymExerciseKind.OTHER
            ExerciseSessionRecord.EXERCISE_TYPE_BOOT_CAMP ->
                GymExerciseKind.OTHER
            else -> null
        }

    /**
     * Title/notes look gym-like (used when HC type is not already mapped).
     */
    fun titleLooksLikeGym(title: String?, notes: String? = null): Boolean {
        val blob = listOfNotNull(title, notes).joinToString(" ").lowercase()
        if (blob.isBlank()) return false
        return GYM_TITLE_KEYWORDS.any { it in blob }
    }

    private val GYM_TITLE_KEYWORDS = listOf(
        "gym",
        "fitness",
        "kraft",
        "workout",
        "weight",
        "strength",
        "push-up",
        "pushup",
        "push up",
        "press-up",
        "machine",
        "circuit",
        "bench",
        "dumbbell",
        "barbell",
        "calisthen",
    )
}
