package com.gymmonth.app.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gymmonth.app.BuildConfig
import com.gymmonth.app.health.GymVisitRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Flex midnight job: force month-identity resync, then reschedule next midnight.
 * Not the sole authority — repository/widget also recompute month identity.
 */
@HiltWorker
class MidnightRolloverWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: GymVisitRepository,
    private val syncScheduler: SyncScheduler,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            repository.sync(SyncReason.MIDNIGHT)
            syncScheduler.scheduleMidnightRollover()
            Result.success()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "midnight rollover failed: ${e.message}")
            }
            syncScheduler.scheduleMidnightRollover()
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "MidnightRolloverWorker"
    }
}
