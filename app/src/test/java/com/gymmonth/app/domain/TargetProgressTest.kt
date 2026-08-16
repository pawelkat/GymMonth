package com.gymmonth.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetProgressTest {

    @Test
    fun noTarget_zeroFraction() {
        assertEquals(0f, TargetProgress.fraction(7, 0), 0f)
        assertFalse(TargetProgress.hasTarget(0))
        assertFalse(TargetProgress.hasTarget(-1))
    }

    @Test
    fun halfAndFull() {
        assertEquals(0.5f, TargetProgress.fraction(6, 12), 0.001f)
        assertEquals(1f, TargetProgress.fraction(12, 12), 0f)
        assertTrue(TargetProgress.isComplete(12, 12))
    }

    @Test
    fun overTarget_clampedToOne() {
        assertEquals(1f, TargetProgress.fraction(20, 12), 0f)
        assertTrue(TargetProgress.isComplete(20, 12))
    }

    @Test
    fun zeroVisits() {
        assertEquals(0f, TargetProgress.fraction(0, 12), 0f)
        assertFalse(TargetProgress.isComplete(0, 12))
    }
}
