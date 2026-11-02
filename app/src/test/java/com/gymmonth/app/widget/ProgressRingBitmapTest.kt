package com.gymmonth.app.widget

import org.junit.Assert.assertNull
import org.junit.Test

class ProgressRingBitmapTest {

    @Test
    fun noTarget_returnsNull() {
        assertNull(ProgressRingBitmap.create(sizePx = 96, visitCount = 7, targetVisits = 0))
        assertNull(ProgressRingBitmap.create(sizePx = 96, visitCount = 7, targetVisits = -1))
        assertNull(ProgressRingBitmap.create(sizePx = 0, visitCount = 7, targetVisits = 12))
    }
}
