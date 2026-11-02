package com.gymmonth.app.widget

import android.content.Context
import android.content.Intent
import android.util.TypedValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.gymmonth.app.MainActivity
import com.gymmonth.app.R
import com.gymmonth.app.data.UserPreferences
import com.gymmonth.app.di.gymMonthEntryPoint
import com.gymmonth.app.domain.MonthRange
import com.gymmonth.app.domain.MonthlyGymSummary
import com.gymmonth.app.domain.SourceStatus
import com.gymmonth.app.domain.TargetProgress
import com.gymmonth.app.util.RelativeTimeFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * AMOLED home-screen widget: pure #000000.
 * Visit count + optional progress ring around a dumbbell.
 */
class GymMonthWidget : GlanceAppWidget() {

    /** Exact size so LocalSize matches the real widget bounds on the launcher. */
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = context.gymMonthEntryPoint()
        val prefs = entry.preferencesStore().get()
        val cached = entry.summaryCacheStore().getOnce()
        val zone = ZoneId.systemDefault()
        val currentMonth = MonthRange.current(zoneId = zone)
        val summary = when {
            cached == null -> null
            cached.month.identityKey() != currentMonth.identityKey() -> {
                MonthlyGymSummary.empty(
                    month = currentMonth,
                    lastSyncedAt = Instant.now(),
                    sourceStatus = SourceStatus.NO_DATA,
                )
            }
            else -> cached
        }

        provideContent {
            GymMonthWidgetContent(
                summary = summary,
                preferences = prefs,
            )
        }
    }
}

@Composable
private fun GymMonthWidgetContent(
    summary: MonthlyGymSummary?,
    preferences: UserPreferences,
) {
    val context = LocalContext.current
    val size = LocalSize.current
    val visits = summary?.visitCount ?: 0
    val target = preferences.monthlyTargetVisits
    val compact = size.height < 90.dp
    val monthLabel = summary?.month?.startLocalDate()?.format(
        DateTimeFormatter.ofPattern("MMM", Locale.getDefault()),
    ) ?: DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
        .format(java.time.LocalDate.now())
    val status = summary?.sourceStatus
    val subtitle = when (status) {
        SourceStatus.PERMISSIONS_MISSING -> "grant permissions"
        SourceStatus.HEALTH_CONNECT_UNAVAILABLE -> "Health Connect needed"
        SourceStatus.ERROR -> "tap to open"
        SourceStatus.USER_REPORTED_MISSING_BRIDGE -> "check Samsung bridge"
        else -> if (compact) {
            "visits"
        } else {
            "$monthLabel · visits"
        }
    }
    val targetLine = if (TargetProgress.hasTarget(target)) {
        val pct = (TargetProgress.fraction(visits, target) * 100f).roundToInt()
        "$target · $pct%"
    } else {
        null
    }
    val updated = summary?.lastSyncedAt?.let {
        RelativeTimeFormat.formatUpdatedAgo(it)
    }.orEmpty()
    val openApp = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    val gap = 16.dp
    val minTextCol = 48.dp
    val availH = size.height.coerceAtLeast(40.dp)
    val availWForIcon = (size.width - gap - minTextCol).coerceAtLeast(40.dp)
    val maxIconSide = when {
        availH.value <= availWForIcon.value -> availH
        else -> availWForIcon
    }
    val iconSide = (maxIconSide.value * 0.85f).dp.coerceAtLeast(36.dp)

    val countSp = when {
        iconSide.value >= 120f -> 32.sp
        iconSide.value >= 90f -> 28.sp
        iconSide.value >= 64f -> 24.sp
        else -> 20.sp
    }
    val secondarySp = if (compact) 10.sp else 12.sp

    val iconSidePx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        iconSide.value,
        context.resources.displayMetrics,
    ).roundToInt().coerceAtLeast(96)

    val ringBitmap = ProgressRingBitmap.create(
        sizePx = iconSidePx,
        visitCount = visits,
        targetVisits = target,
        strokePx = (iconSidePx * 0.09f).coerceIn(6f, 18f),
    )
    val iconPad = if (ringBitmap != null) {
        (iconSide.value * 0.14f).dp.coerceIn(4.dp, 14.dp)
    } else {
        4.dp
    }

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(AmoledColors.Black)
            .padding(horizontal = 4.dp, vertical = 0.dp)
            .clickable(actionStartActivity(openApp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .size(iconSide)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            if (ringBitmap != null) {
                Image(
                    provider = ImageProvider(ringBitmap),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
            Image(
                provider = ImageProvider(R.drawable.ic_dumbbell),
                contentDescription = "Dumbbell",
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(iconPad),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(AmoledColors.primaryText()),
            )
        }
        Spacer(modifier = GlanceModifier.width(gap))
        Column(
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = visits.toString(),
                style = TextStyle(
                    color = AmoledColors.primaryText(),
                    fontSize = countSp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = subtitle,
                style = TextStyle(
                    color = AmoledColors.secondaryText(),
                    fontSize = secondarySp,
                    fontWeight = FontWeight.Normal,
                ),
                maxLines = 1,
            )
            if (targetLine != null) {
                Spacer(modifier = GlanceModifier.height(1.dp))
                Text(
                    text = targetLine,
                    style = TextStyle(
                        color = AmoledColors.secondaryText(),
                        fontSize = secondarySp,
                        fontWeight = FontWeight.Normal,
                    ),
                    maxLines = 1,
                )
            }
            if (!compact && updated.isNotEmpty() && status != SourceStatus.PERMISSIONS_MISSING) {
                Spacer(modifier = GlanceModifier.height(1.dp))
                Text(
                    text = updated,
                    style = TextStyle(
                        color = AmoledColors.tertiaryText(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}
