package com.gymmonth.app.health

/**
 * Detects Health Connect Changes-token expiry from exception class/message.
 * Isolated so unit tests do not need a real HC client.
 */
object TokenExpiryDetector {
    fun isExpired(e: Exception): Boolean {
        val name = e::class.java.simpleName
        val message = e.message.orEmpty()
        return name.contains("ChangesTokenExpired", ignoreCase = true) ||
            name.contains("TokenExpired", ignoreCase = true) ||
            (message.contains("token", ignoreCase = true) &&
                message.contains("expir", ignoreCase = true))
    }
}
