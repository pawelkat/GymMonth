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

@HiltWorker
class GymSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: GymVisitRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val reasonName = inputData.getString(KEY_REASON) ?: SyncReason.PERIODIC.name
        val reason = runCatching { SyncReason.valueOf(reasonName) }.getOrDefault(SyncReason.PERIODIC)
        return try {
            repository.sync(reason)
            Result.success()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "sync failed: ${e.message}")
            }
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_REASON = "reason"
        private const val TAG = "GymSyncWorker"
    }
}
