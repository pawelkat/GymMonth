package com.gymmonth.app.sync

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.gymmonth.app.widget.GymMonthWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Triggers Glance widget redraws after cache writes.
 */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun updateAll() {
        GymMonthWidget().updateAll(context)
    }
}
