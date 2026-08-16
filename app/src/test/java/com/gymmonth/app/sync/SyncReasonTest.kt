package com.gymmonth.app.sync

import org.junit.Assert.assertTrue
import org.junit.Test

class SyncReasonTest {
    @Test
    fun hasMonthIdentityReason() {
        assertTrue(SyncReason.entries.any { it == SyncReason.MONTH_IDENTITY_CHANGED })
        assertTrue(SyncReason.entries.any { it == SyncReason.MANUAL })
    }
}
