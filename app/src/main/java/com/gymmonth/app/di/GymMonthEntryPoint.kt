package com.gymmonth.app.di

import android.content.Context
import com.gymmonth.app.data.PreferencesStore
import com.gymmonth.app.data.SummaryCacheStore
import com.gymmonth.app.sync.SyncScheduler
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Entry point for non-Hilt contexts (Glance widgets, boot receiver).
 * Do not @Inject into GlanceAppWidget constructors.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface GymMonthEntryPoint {
    fun summaryCacheStore(): SummaryCacheStore
    fun preferencesStore(): PreferencesStore
    fun syncScheduler(): SyncScheduler
}

fun Context.gymMonthEntryPoint(): GymMonthEntryPoint =
    EntryPointAccessors.fromApplication(
        applicationContext,
        GymMonthEntryPoint::class.java,
    )
