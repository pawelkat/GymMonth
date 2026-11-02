package com.gymmonth.app.ui.home

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymmonth.app.R
import com.gymmonth.app.domain.SourceStatus
import com.gymmonth.app.domain.TargetProgress
import com.gymmonth.app.domain.displayName
import com.gymmonth.app.domain.isManual
import com.gymmonth.app.ui.theme.AmoledBlack
import com.gymmonth.app.widget.GymMonthWidgetReceiver
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    onOpenOnboarding: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenSession: (sessionId: String) -> Unit = {},
    onAddVisit: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val summary = state.summary

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = monthSubtitle(summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(32.dp))

        when {
            state.refreshing && summary == null -> {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            else -> {
                val visits = summary?.visitCount ?: 0
                val target = state.preferences.monthlyTargetVisits
                Text(
                    text = visits.toString(),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.home_visits_label),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (TargetProgress.hasTarget(target)) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val pct = (TargetProgress.fraction(visits, target) * 100f).roundToInt()
                    Text(
                        text = stringResource(R.string.home_target_progress, target, pct),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.home_session_count,
                        summary?.visits?.size ?: 0,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = statusLabel(summary?.sourceStatus),
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (summary?.sourceStatus) {
                        SourceStatus.ERROR,
                        SourceStatus.PERMISSIONS_MISSING,
                        SourceStatus.USER_REPORTED_MISSING_BRIDGE,
                        -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                emptyStateHint(summary?.sourceStatus)?.let { hintRes ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(hintRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                summary?.lastSyncedAt?.let { synced ->
                    Spacer(modifier = Modifier.height(8.dp))
                    val fmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                        .withLocale(Locale.getDefault())
                    val zone = summary.month.zoneId
                    Text(
                        text = stringResource(
                            R.string.home_last_synced,
                            fmt.format(synced.atZone(zone)),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }

        state.errorMessage?.let { err ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = err,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        val visits = summary?.visits.orEmpty()
        if (visits.isNotEmpty()) {
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.home_visits_heading),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.home_visits_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            val timeFmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                .withLocale(Locale.getDefault())
            val zone = summary?.month?.zoneId ?: java.time.ZoneId.systemDefault()
            val manualLabel = stringResource(R.string.home_flag_manual)
            visits.forEach { visit ->
                val day = timeFmt.format(visit.start.atZone(zone))
                val flags = buildList {
                    if (visit.overlapSuppressed) add(stringResource(R.string.home_flag_overlap))
                    if (!visit.countsAsVisit && !visit.overlapSuppressed) {
                        add(stringResource(R.string.home_flag_not_counted))
                    }
                }.joinToString(" · ")
                Text(
                    text = buildString {
                        append(day)
                        append(" · ")
                        append(visit.exerciseType.displayName())
                        visit.gymName?.let { append(" · ").append(it) }
                        if (visit.isManual) append(" · ").append(manualLabel)
                        if (flags.isNotEmpty()) {
                            append(" (")
                            append(flags)
                            append(')')
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSession(visit.id) }
                        .padding(vertical = 8.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = onAddVisit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.home_add_visit))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { viewModel.refresh() },
            enabled = !state.refreshing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (state.refreshing) {
                    stringResource(R.string.home_refreshing)
                } else {
                    stringResource(R.string.home_refresh)
                },
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onOpenDiagnostics,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.home_diagnostics))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onOpenOnboarding,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.home_setup_guide))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                val manager = AppWidgetManager.getInstance(context)
                val provider = ComponentName(context, GymMonthWidgetReceiver::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    manager.isRequestPinAppWidgetSupported
                ) {
                    manager.requestPinAppWidget(provider, null, null)
                    Toast.makeText(
                        context,
                        context.getString(R.string.home_widget_pinned),
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        context.getString(R.string.home_widget_pin_unsupported),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.home_add_widget))
        }
    }
}

@Composable
private fun monthSubtitle(summary: com.gymmonth.app.domain.MonthlyGymSummary?): String {
    val month = summary?.month
    return if (month != null) {
        val label = month.startLocalDate().format(
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()),
        )
        stringResource(R.string.home_month_label, label)
    } else {
        stringResource(R.string.home_month_label_fallback)
    }
}

@Composable
private fun statusLabel(status: SourceStatus?): String =
    stringResource(
        when (status) {
            SourceStatus.OK -> R.string.home_status_ok
            SourceStatus.NO_DATA -> R.string.home_status_no_data
            SourceStatus.PERMISSIONS_MISSING -> R.string.home_status_permissions
            SourceStatus.HEALTH_CONNECT_UNAVAILABLE -> R.string.home_status_hc_unavailable
            SourceStatus.USER_REPORTED_MISSING_BRIDGE -> R.string.home_status_bridge
            SourceStatus.ERROR -> R.string.home_status_error
            null -> R.string.home_status_loading
        },
    )

@Composable
private fun emptyStateHint(status: SourceStatus?): Int? =
    when (status) {
        SourceStatus.NO_DATA -> R.string.home_hint_no_data
        SourceStatus.PERMISSIONS_MISSING -> R.string.home_hint_permissions
        SourceStatus.USER_REPORTED_MISSING_BRIDGE -> R.string.home_hint_bridge
        SourceStatus.HEALTH_CONNECT_UNAVAILABLE -> R.string.home_hint_hc
        else -> null
    }
