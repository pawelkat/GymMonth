package com.gymmonth.app.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.gymmonth.app.health.HealthConnectAvailability
import com.gymmonth.app.health.HealthConnectSdkStatus
import com.gymmonth.app.health.HealthPermissionChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Schedules periodic gym sync and flex midnight month rollover.
 * Periodic work is only enqueued when HC is available and required
 * permissions allow useful work.
 */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val availability: HealthConnectAvailability,
    private val permissionChecker: HealthPermissionChecker,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val workManager get() = WorkManager.getInstance(context)

    fun ensureScheduled() {
        scope.launch {
            val canBackground = availability.status() == HealthConnectSdkStatus.AVAILABLE &&
                permissionChecker.hasRequiredPermissions()

            if (canBackground) {
                enqueuePeriodic()
            }
            scheduleMidnightRollover()
        }
    }

    fun enqueuePeriodic() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(false)
            .build()

        val request = PeriodicWorkRequestBuilder<GymSyncWorker>(
            6,
            TimeUnit.HOURS,
            1,
            TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .addTag(TAG_PERIODIC)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun enqueueNow(reason: SyncReason) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(false)
            .build()

        val request = OneTimeWorkRequestBuilder<GymSyncWorker>()
            .setConstraints(constraints)
            .addTag(TAG_ONESHOT)
            .setInputData(workDataOf(GymSyncWorker.KEY_REASON to reason.name))
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_ONESHOT,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /**
     * One-shot toward next local midnight + 5 min (no exact alarms).
     * Rescheduled by [MidnightRolloverWorker] after each run.
     */
    fun scheduleMidnightRollover() {
        val delay = MidnightRollover.delayUntilNext(ZonedDateTime.now())
        val delayMinutes = delay.toMinutes().coerceAtLeast(1)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(false)
            .build()

        val request = OneTimeWorkRequestBuilder<MidnightRolloverWorker>()
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .addTag(TAG_MIDNIGHT)
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_MIDNIGHT,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    companion object {
        const val UNIQUE_PERIODIC = "gymmonth_periodic_sync"
        const val UNIQUE_ONESHOT = "gymmonth_oneshot_sync"
        const val UNIQUE_MIDNIGHT = "gymmonth_midnight_rollover"
        const val TAG_PERIODIC = "periodic"
        const val TAG_ONESHOT = "oneshot"
        const val TAG_MIDNIGHT = "midnight"
    }
}
