package com.gymmonth.app.domain

/** Human-readable label for [GymExerciseKind] (English v1). */
fun GymExerciseKind.displayName(): String =
    when (this) {
        GymExerciseKind.STRENGTH_TRAINING -> "Strength training"
        GymExerciseKind.WEIGHTLIFTING -> "Weightlifting"
        GymExerciseKind.CALISTHENICS -> "Calisthenics"
        GymExerciseKind.HIIT -> "HIIT"
        GymExerciseKind.OTHER -> "Other gym workout"
    }
