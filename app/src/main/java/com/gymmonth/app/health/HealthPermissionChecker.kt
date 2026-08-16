package com.gymmonth.app.health

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads currently granted Health Connect permissions (suspend).
 * No record reads — permissions only (PR 2).
 */
@Singleton
class HealthPermissionChecker @Inject constructor(
    private val clientProvider: HealthConnectClientProvider,
) {
    suspend fun grantedPermissions(): Set<String> {
        val client = clientProvider.getOrNull() ?: return emptySet()
        return client.permissionController.getGrantedPermissions()
    }

    suspend fun hasRequiredPermissions(): Boolean {
        val granted = grantedPermissions()
        return GymPermissions.required.all { it in granted }
    }

    suspend fun hasBackgroundPermission(): Boolean {
        val granted = grantedPermissions()
        return GymPermissions.recommendedBackground.all { it in granted }
    }
}
