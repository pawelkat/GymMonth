package com.gymmonth.app.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider

/**
 * True-black AMOLED palette for Glance (not Material dark gray).
 */
object AmoledColors {
    val Black = Color(0xFF000000)
    val PrimaryText = Color(0xFFC8C8C8)
    val SecondaryText = Color(0xFF666666)
    val TertiaryText = Color(0xFF4A4A4A)
    val Accent = Color(0xFF4A90A4)
    val Error = Color(0xFF8B4545)

    fun primaryText(): ColorProvider = ColorProvider(PrimaryText)
    fun secondaryText(): ColorProvider = ColorProvider(SecondaryText)
    fun tertiaryText(): ColorProvider = ColorProvider(TertiaryText)
    fun errorText(): ColorProvider = ColorProvider(Error)
}
