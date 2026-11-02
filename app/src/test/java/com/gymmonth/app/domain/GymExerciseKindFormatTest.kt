package com.gymmonth.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GymExerciseKindFormatTest {

    @Test
    fun displayNamesAreHumanReadable() {
        assertEquals("Strength training", GymExerciseKind.STRENGTH_TRAINING.displayName())
        assertEquals("HIIT", GymExerciseKind.HIIT.displayName())
        assertFalse(GymExerciseKind.WEIGHTLIFTING.displayName().contains('_'))
    }
}
