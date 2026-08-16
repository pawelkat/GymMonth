package com.gymmonth.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class HealthConnectSdkStatus {
    AVAILABLE,
    UPDATE_REQUIRED,
    UNAVAILABLE,
}

/**
 * Wraps [HealthConnectClient.getSdkStatus] for UI / onboarding decisions.
 */
@Singleton
class HealthConnectAvailability @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun status(): HealthConnectSdkStatus {
        return when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectSdkStatus.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                HealthConnectSdkStatus.UPDATE_REQUIRED
            else -> HealthConnectSdkStatus.UNAVAILABLE
        }
    }

    fun isAvailable(): Boolean = status() == HealthConnectSdkStatus.AVAILABLE

    /** Play Store package for the Health Connect provider app. */
    fun providerPackageName(): String = PROVIDER_PACKAGE

    companion object {
        const val PROVIDER_PACKAGE: String = "com.google.android.apps.healthdata"
    }
}
