package com.gymmonth.app.health

import android.util.Log
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.request.ChangesTokenRequest
import com.gymmonth.app.BuildConfig
import com.gymmonth.app.data.ChangesTokenStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Health Connect Changes token lifecycle:
 * drain all pages, always persist next token, re-aggregate on exercise
 * upsert or delete, full resync path on expiry.
 *
 * Distance is not tracked — visit count only needs ExerciseSessionRecord.
 */
@Singleton
class ChangesSyncEngine @Inject constructor(
    private val clientProvider: HealthConnectClientProvider,
    private val tokenStore: ChangesTokenStore,
) {
    data class DrainResult(
        val nextToken: String?,
        val needsReaggregate: Boolean,
        val tokenExpired: Boolean,
        val error: Exception? = null,
    )

    suspend fun issueToken(): String? {
        val client = clientProvider.getOrNull() ?: return null
        return try {
            val token = client.getChangesToken(
                ChangesTokenRequest(
                    recordTypes = setOf(ExerciseSessionRecord::class),
                ),
            )
            tokenStore.save(token)
            token
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "issueToken failed: ${e.message}")
            }
            null
        }
    }

    /**
     * Drain changes for the stored token (or [tokenOverride]).
     * Always attempts to persist the latest nextChangesToken when available.
     */
    suspend fun drain(tokenOverride: String? = null): DrainResult {
        val client = clientProvider.getOrNull()
            ?: return DrainResult(null, needsReaggregate = true, tokenExpired = false)

        var token = tokenOverride ?: tokenStore.get()
        if (token.isNullOrBlank()) {
            return DrainResult(null, needsReaggregate = true, tokenExpired = false)
        }

        var needsReaggregate = false
        try {
            var hasMore = true
            while (hasMore) {
                val response = client.getChanges(token!!)
                for (change in response.changes) {
                    when (change) {
                        is DeletionChange -> {
                            needsReaggregate = true
                        }
                        is UpsertionChange -> {
                            if (change.record is ExerciseSessionRecord) {
                                needsReaggregate = true
                            }
                        }
                    }
                }
                token = response.nextChangesToken
                hasMore = response.hasMore
            }
            if (!token.isNullOrBlank()) {
                tokenStore.save(token!!)
            }
            return DrainResult(
                nextToken = token,
                needsReaggregate = needsReaggregate,
                tokenExpired = false,
            )
        } catch (e: Exception) {
            val expired = TokenExpiryDetector.isExpired(e)
            if (expired) {
                tokenStore.clear()
            }
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "drain failed expired=$expired: ${e.message}")
            }
            return DrainResult(
                nextToken = null,
                needsReaggregate = true,
                tokenExpired = expired,
                error = e,
            )
        }
    }

    companion object {
        private const val TAG = "ChangesSyncEngine"
    }
}
