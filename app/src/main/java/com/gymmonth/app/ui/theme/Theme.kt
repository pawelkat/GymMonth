package com.gymmonth.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * AMOLED-first theme: pure black surfaces regardless of system light/dark.
 * System light theme is ignored so the companion app matches the widget.
 */
private val AmoledColorScheme = darkColorScheme(
    primary = AmoledAccent,
    onPrimary = AmoledBlack,
    secondary = AmoledTextSecondary,
    onSecondary = AmoledBlack,
    background = AmoledBlack,
    onBackground = AmoledTextPrimary,
    surface = AmoledBlack,
    onSurface = AmoledTextPrimary,
    surfaceVariant = Color(0xFF121212),
    onSurfaceVariant = AmoledTextSecondary,
    error = AmoledError,
    onError = AmoledBlack,
    outline = AmoledTextMuted,
)

@Composable
fun GymMonthTheme(
    // Kept for API compatibility; always use AMOLED black.
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = AmoledColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = AmoledBlack.toArgb()
            window.navigationBarColor = AmoledBlack.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
