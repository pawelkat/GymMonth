package com.gymmonth.app.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gymmonth.app.di.gymMonthEntryPoint

/**
 * Reschedule periodic sync + midnight rollover after reboot / update.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val pending = goAsync()
        try {
            context.applicationContext.gymMonthEntryPoint()
                .syncScheduler()
                .ensureScheduled()
        } finally {
            pending.finish()
        }
    }
}
