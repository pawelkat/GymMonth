package com.gymmonth.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides a [HealthConnectClient] only when the SDK is available.
 * Callers must check [HealthConnectAvailability] before reading data (PR 3+).
 */
@Singleton
class HealthConnectClientProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val availability: HealthConnectAvailability,
) {
    /**
     * @return client when SDK is available, otherwise null
     */
    fun getOrNull(): HealthConnectClient? {
        if (!availability.isAvailable()) return null
        return HealthConnectClient.getOrCreate(context)
    }

    /**
     * @throws IllegalStateException if Health Connect is not available
     */
    fun requireClient(): HealthConnectClient {
        return getOrNull()
            ?: error("Health Connect SDK is not available on this device")
    }
}
