package com.gymmonth.app.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncReasonTest {

    @Test
    fun periodicAndChangesAreIncremental() {
        val forceFullReasons = setOf(
            SyncReason.MANUAL,
            SyncReason.APP_FOREGROUND,
            SyncReason.MIDNIGHT,
            SyncReason.PERMISSION_GRANTED,
            SyncReason.MONTH_IDENTITY_CHANGED,
            SyncReason.TOKEN_EXPIRED,
            SyncReason.WIDGET_PIN,
        )
        assertTrue(SyncReason.PERIODIC !in forceFullReasons)
        assertTrue(SyncReason.CHANGES !in forceFullReasons)
        assertTrue(SyncReason.MONTH_IDENTITY_CHANGED in forceFullReasons)
    }

    @Test
    fun allReasonsHaveStableNames() {
        SyncReason.entries.forEach { reason ->
            assertEquals(reason, SyncReason.valueOf(reason.name))
        }
    }
}
