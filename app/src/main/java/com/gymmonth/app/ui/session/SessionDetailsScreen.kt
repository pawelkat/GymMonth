package com.gymmonth.app.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gymmonth.app.R
import com.gymmonth.app.data.DefaultGymsRepository
import com.gymmonth.app.domain.CUSTOM_GYM_PLACE_ID
import com.gymmonth.app.domain.GymPlace
import com.gymmonth.app.domain.GymVisit
import com.gymmonth.app.domain.displayName
import com.gymmonth.app.domain.isManual
import com.gymmonth.app.health.GymVisitRepository
import com.gymmonth.app.ui.theme.AmoledBlack
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SessionDetailsUiState(
    val visit: GymVisit? = null,
    val notFound: Boolean = false,
)

@HiltViewModel
class SessionDetailsViewModel @Inject constructor(
    private val repository: GymVisitRepository,
    defaultGyms: DefaultGymsRepository,
) : ViewModel() {

    val gyms: List<GymPlace> = defaultGyms.selectableGyms()

    private val sessionIdFlow = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SessionDetailsUiState> = combine(
        sessionIdFlow,
        repository.summaryFlow,
    ) { sessionId, summary ->
        if (sessionId == null) {
            return@combine SessionDetailsUiState(notFound = true)
        }
        val visit = summary?.visits?.firstOrNull { it.id == sessionId }
        SessionDetailsUiState(
            visit = visit,
            notFound = visit == null,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SessionDetailsUiState(),
    )

    fun setSessionId(sessionId: String) {
        sessionIdFlow.value = sessionId
    }

    fun setGym(gym: GymPlace) {
        val id = sessionIdFlow.value ?: return
        viewModelScope.launch { repository.setSessionGym(id, gym.id, gym.name) }
    }

    fun setCustomGym(name: String) {
        val id = sessionIdFlow.value ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.setSessionGym(id, CUSTOM_GYM_PLACE_ID, trimmed) }
    }

    fun clearGym() {
        val id = sessionIdFlow.value ?: return
        viewModelScope.launch { repository.clearSessionGym(id) }
    }

    /** Only manual visits can be deleted; HC sessions are read-only. */
    fun deleteManual(onDone: () -> Unit) {
        val id = sessionIdFlow.value ?: return
        viewModelScope.launch {
            repository.deleteManualVisit(id)
            onDone()
        }
    }
}

@Composable
fun SessionDetailsScreen(
    sessionId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SessionDetailsViewModel = hiltViewModel(),
) {
    LaunchedEffect(sessionId) {
        viewModel.setSessionId(sessionId)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
            text = state.visit?.gymName
                ?: stringResource(R.string.session_gym_unset),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.session_place_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))

        val visit = state.visit
        if (visit == null) {
            Text(
                text = stringResource(R.string.session_not_found),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            return@Column
        }

        var showCustom by rememberSaveable(sessionId) { mutableStateOf(false) }
        var customName by rememberSaveable(sessionId) { mutableStateOf("") }
        GymDropdown(
            gyms = viewModel.gyms,
            selectedLabel = visit.gymName,
            onPickGym = {
                showCustom = false
                viewModel.setGym(it)
            },
            onPickCustom = {
                customName = visit.gymName.orEmpty()
                showCustom = true
            },
            onClear = if (visit.gymName != null) {
                {
                    showCustom = false
                    viewModel.clearGym()
                }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (showCustom) {
            CustomGymField(value = customName, onValueChange = { customName = it })
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    viewModel.setCustomGym(customName)
                    showCustom = false
                },
                enabled = customName.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.gym_picker_custom_save))
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.session_details_heading),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(12.dp))

        val zone = ZoneId.systemDefault()
        val dateFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault())
        val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
        val startZ = visit.start.atZone(zone)
        val endZ = visit.end.atZone(zone)
        val minutes = Duration.between(visit.start, visit.end).toMinutes().coerceAtLeast(0)

        DetailLine(
            label = stringResource(R.string.session_when),
            value = "${dateFmt.format(startZ)} · ${timeFmt.format(startZ)} – ${timeFmt.format(endZ)}",
        )
        DetailLine(
            label = stringResource(R.string.session_duration),
            value = stringResource(R.string.session_duration_minutes, minutes),
        )
        DetailLine(
            label = stringResource(R.string.session_type),
            value = visit.exerciseType.displayName(),
        )
        DetailLine(
            label = stringResource(R.string.session_source),
            value = if (visit.isManual) {
                stringResource(R.string.session_source_manual)
            } else {
                visit.dataOriginPackage.ifBlank { "—" }
            },
        )
        visit.title?.let {
            DetailLine(
                label = stringResource(R.string.session_title),
                value = it,
            )
        }
        DetailLine(
            label = stringResource(R.string.session_counts),
            value = if (visit.countsAsVisit) {
                stringResource(R.string.session_counts_yes)
            } else {
                stringResource(R.string.session_counts_no)
            },
        )
        if (visit.overlapSuppressed) {
            DetailLine(
                label = stringResource(R.string.session_flags),
                value = stringResource(R.string.session_flag_overlap),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        if (visit.isManual) {
            var confirmDelete by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.session_delete_manual),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (confirmDelete) {
                AlertDialog(
                    onDismissRequest = { confirmDelete = false },
                    title = { Text(stringResource(R.string.session_delete_manual_title)) },
                    text = { Text(stringResource(R.string.session_delete_manual_body)) },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmDelete = false
                            viewModel.deleteManual(onDone = onBack)
                        }) {
                            Text(
                                stringResource(R.string.session_delete_confirm),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmDelete = false }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    },
                    containerColor = AmoledBlack,
                )
            }
        } else {
            Text(
                text = stringResource(R.string.session_hc_readonly),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(0.6f),
        )
    }
}
