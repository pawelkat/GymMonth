package com.gymmonth.app.health

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenExpiryDetectorTest {

    private class ChangesTokenExpiredException(msg: String) : Exception(msg)

    @Test
    fun detectsByClassName() {
        assertTrue(TokenExpiryDetector.isExpired(ChangesTokenExpiredException("gone")))
    }

    @Test
    fun detectsByMessage() {
        assertTrue(TokenExpiryDetector.isExpired(IllegalStateException("Changes token expired")))
    }

    @Test
    fun ignoresUnrelated() {
        assertFalse(TokenExpiryDetector.isExpired(IllegalStateException("network down")))
    }
}
