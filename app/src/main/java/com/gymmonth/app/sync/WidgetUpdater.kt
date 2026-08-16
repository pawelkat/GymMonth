package com.gymmonth.app.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Triggers Glance widget redraws after cache writes.
 * No-op until the AMOLED widget lands (PR 5).
 */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Suppress("UNUSED_PARAMETER")
    suspend fun updateAll() {
        // GymMonthWidget().updateAll(context) — PR 5
    }
}
