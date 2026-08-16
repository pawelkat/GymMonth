package com.gymmonth.app.sync

enum class SyncReason {
    APP_FOREGROUND,
    MANUAL,
    PERIODIC,
    CHANGES,
    MIDNIGHT,
    PERMISSION_GRANTED,
    MONTH_IDENTITY_CHANGED,
    TOKEN_EXPIRED,
    WIDGET_PIN,
}
