package com.gymmonth.app.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GymPermissionsTest {

    @Test
    fun required_hasExerciseOnly() {
        assertEquals(1, GymPermissions.required.size)
        assertTrue(GymPermissions.required.any { it.isNotEmpty() })
        // Background is separate
        assertTrue(GymPermissions.required.none { it in GymPermissions.recommendedBackground })
    }

    @Test
    fun required_doesNotIncludeDistance() {
        val joined = GymPermissions.required.joinToString()
        assertFalse(joined.contains("Distance", ignoreCase = true))
        assertFalse(joined.contains("DISTANCE", ignoreCase = true))
    }

    @Test
    fun allRequested_includesBackground() {
        assertTrue(GymPermissions.allRequested.containsAll(GymPermissions.required))
        assertTrue(GymPermissions.allRequested.containsAll(GymPermissions.recommendedBackground))
        assertEquals(
            GymPermissions.required.size + GymPermissions.recommendedBackground.size,
            GymPermissions.allRequested.size,
        )
    }

    @Test
    fun historyNotRequested() {
        val joined = GymPermissions.allRequested.joinToString()
        assertFalse(joined.contains("HISTORY", ignoreCase = true))
    }
}
