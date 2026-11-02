package com.gymmonth.app.vault

import android.content.Context
import com.gymmonth.app.R

/** User-facing one-liner for a vault write result (toast / status line). */
fun GymVaultLogger.Outcome.message(context: Context): String = when (this) {
    GymVaultLogger.Outcome.Disabled -> context.getString(R.string.vault_msg_disabled)
    GymVaultLogger.Outcome.NothingToDo -> context.getString(R.string.vault_msg_nothing)
    is GymVaultLogger.Outcome.NeedPermission -> context.getString(R.string.vault_msg_need_permission)
    is GymVaultLogger.Outcome.Written ->
        if (count > 1) {
            context.getString(R.string.vault_msg_written_many, count, relativePath)
        } else {
            context.getString(R.string.vault_msg_written, relativePath)
        }
    is GymVaultLogger.Outcome.AlreadyThere -> context.getString(R.string.vault_msg_already, relativePath)
    is GymVaultLogger.Outcome.Failed -> context.getString(R.string.vault_msg_failed, message)
}
