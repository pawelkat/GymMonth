package com.gymmonth.app.ui.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gymmonth.app.R
import com.gymmonth.app.domain.MonthlyGymSummary
import com.gymmonth.app.domain.SourceStatus
import com.gymmonth.app.domain.displayName
import com.gymmonth.app.health.GymVisitRepository
import com.gymmonth.app.sync.SyncReason
import com.gymmonth.app.ui.onboarding.SamsungBridgeChecklist
import com.gymmonth.app.ui.theme.AmoledBlack
import com.gymmonth.app.util.RelativeTimeFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DiagnosticsUiState(
    val summary: MonthlyGymSummary? = null,
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val repository: GymVisitRepository,
) : ViewModel() {

    val uiState: StateFlow<DiagnosticsUiState> = repository.summaryFlow
        .map { DiagnosticsUiState(summary = it) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DiagnosticsUiState(),
        )

    fun reportMissingData() {
        viewModelScope.launch {
            repository.setUserReportedMissingData(true)
        }
    }

    fun clearMissingReport() {
        viewModelScope.launch {
            repository.setUserReportedMissingData(false)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            repository.sync(SyncReason.MANUAL)
        }
    }
}

@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    onOpenSession: (sessionId: String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: DiagnosticsViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val summary = ui.summary

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.nav_back))
        }
        Text(
            text = stringResource(R.string.diagnostics_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(16.dp))

        val s = summary
        if (s == null) {
            Text(
                text = stringResource(R.string.diagnostics_no_cache),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            DiagLine(stringResource(R.string.diagnostics_status), s.sourceStatus.name)
            DiagLine(stringResource(R.string.diagnostics_visits), s.visitCount.toString())
            DiagLine(stringResource(R.string.diagnostics_sessions), s.visits.size.toString())
            DiagLine(
                stringResource(R.string.diagnostics_counted),
                s.visits.count { it.countsAsVisit }.toString(),
            )
            DiagLine(
                stringResource(R.string.diagnostics_overlap),
                s.visits.count { it.overlapSuppressed }.toString(),
            )
            DiagLine(
                stringResource(R.string.diagnostics_synced),
                RelativeTimeFormat.formatUpdatedAgo(s.lastSyncedAt),
            )
            DiagLine(
                stringResource(R.string.diagnostics_month_key),
                s.month.identityKey(),
            )
            val origins = s.visits.map { it.dataOriginPackage }.distinct().filter { it.isNotBlank() }
            if (origins.isNotEmpty()) {
                DiagLine(
                    stringResource(R.string.diagnostics_origins),
                    origins.joinToString(),
                )
            }
            if (s.userReportedMissingData) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.diagnostics_user_reported),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.diagnostics_sessions_heading),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (s.visits.isEmpty()) {
                Text(
                    text = stringResource(R.string.diagnostics_no_sessions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val zone = s.month.zoneId
                val timeFmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                    .withLocale(Locale.getDefault())
                s.visits.forEach { visit ->
                    Text(
                        text = buildString {
                            append(visit.gymName ?: stringResource(R.string.session_gym_unset))
                            append('\n')
                            append(timeFmt.format(visit.start.atZone(zone)))
                            append(" · ")
                            append(visit.exerciseType.displayName())
                            if (visit.overlapSuppressed) append(" · overlap")
                            if (!visit.countsAsVisit) append(" · not counted")
                            append('\n')
                            append(visit.dataOriginPackage.ifBlank { "?" })
                            visit.title?.let { append(" · ").append(it) }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenSession(visit.id) }
                            .padding(vertical = 6.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { viewModel.refresh() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.home_refresh))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { viewModel.reportMissingData() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.diagnostics_i_trained))
        }
        if (summary?.userReportedMissingData == true ||
            summary?.sourceStatus == SourceStatus.USER_REPORTED_MISSING_BRIDGE
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = { viewModel.clearMissingReport() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.diagnostics_clear_report))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        SamsungBridgeChecklist(showTitle = true)
    }
}

@Composable
private fun DiagLine(label: String, value: String) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}
