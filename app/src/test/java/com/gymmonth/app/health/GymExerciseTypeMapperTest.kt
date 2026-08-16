package com.gymmonth.app.health

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.gymmonth.app.domain.GymExerciseKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GymExerciseTypeMapperTest {

    @Test
    fun mapsKnownStrengthTypes() {
        assertEquals(
            GymExerciseKind.STRENGTH_TRAINING,
            GymExerciseTypeMapper.toKind(ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING),
        )
        assertEquals(
            GymExerciseKind.WEIGHTLIFTING,
            GymExerciseTypeMapper.toKind(ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING),
        )
        assertEquals(
            GymExerciseKind.CALISTHENICS,
            GymExerciseTypeMapper.toKind(ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS),
        )
        assertEquals(
            GymExerciseKind.HIIT,
            GymExerciseTypeMapper.toKind(
                ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING,
            ),
        )
    }

    @Test
    fun runningIsNotGym() {
        assertNull(GymExerciseTypeMapper.toKind(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING))
    }

    @Test
    fun titleKeywords() {
        assertTrue(GymExerciseTypeMapper.titleLooksLikeGym("Morning Gym"))
        assertTrue(GymExerciseTypeMapper.titleLooksLikeGym("KRAFT training"))
        assertFalse(GymExerciseTypeMapper.titleLooksLikeGym("Morning jog"))
        assertFalse(GymExerciseTypeMapper.titleLooksLikeGym(null))
    }

    @Test
    fun defaultIncluded_hasCoreStrength() {
        val d = GymExerciseTypeMapper.defaultIncludedKinds
        assertTrue(GymExerciseKind.STRENGTH_TRAINING in d)
        assertTrue(GymExerciseKind.WEIGHTLIFTING in d)
        assertTrue(GymExerciseKind.CALISTHENICS in d)
        assertTrue(GymExerciseKind.HIIT in d)
        assertFalse(GymExerciseKind.OTHER in d)
    }
}
