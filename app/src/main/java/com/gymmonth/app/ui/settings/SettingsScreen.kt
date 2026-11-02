package com.gymmonth.app.ui.settings

import android.content.ActivityNotFoundException
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gymmonth.app.R
import com.gymmonth.app.data.DefaultGymsRepository
import com.gymmonth.app.data.PreferencesStore
import com.gymmonth.app.data.UserPreferences
import com.gymmonth.app.domain.GymMonthSettingsNote
import com.gymmonth.app.domain.GymPlace
import com.gymmonth.app.domain.VaultWorkoutFormat
import com.gymmonth.app.domain.VisitCountingMode
import com.gymmonth.app.health.GymVisitRepository
import com.gymmonth.app.ui.theme.AmoledBlack
import com.gymmonth.app.util.ClockProvider
import com.gymmonth.app.vault.GymVaultLogger
import com.gymmonth.app.vault.message
import com.vaultwrite.VaultCatalog
import com.vaultwrite.VaultPaths
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val prefs: UserPreferences = UserPreferences(),
    val gyms: List<GymPlace> = emptyList(),
    val hasStorageAccess: Boolean = false,
    val catalog: DefaultGymsRepository.CatalogStatus? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesStore: PreferencesStore,
    private val defaultGyms: DefaultGymsRepository,
    private val vaultLogger: GymVaultLogger,
    private val repository: GymVisitRepository,
    private val clock: ClockProvider,
) : ViewModel() {

    private val access = MutableStateFlow(vaultLogger.hasStorageAccess())
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<Pair<String, Boolean>?>(null)

    private val base = combine(
        preferencesStore.preferencesFlow,
        defaultGyms.gymsFlow,
        defaultGyms.statusFlow,
        access,
    ) { prefs, gyms, catalog, granted ->
        SettingsUiState(
            prefs = prefs,
            gyms = (gyms ?: defaultGyms.all()).filter { it.selectable },
            hasStorageAccess = granted,
            catalog = catalog,
        )
    }

    val uiState: StateFlow<SettingsUiState> = combine(base, busy, message) { s, b, m ->
        s.copy(busy = b, message = m?.first, messageIsError = m?.second ?: false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun manageAccessIntent() = vaultLogger.manageAccessIntent()

    fun manageAccessFallbackIntent() = vaultLogger.manageAccessFallbackIntent()

    /** Called on resume (user may have just granted All files access). */
    fun refreshAccess() {
        access.value = vaultLogger.hasStorageAccess()
        viewModelScope.launch { defaultGyms.reload() }
    }

    fun setMonthlyTarget(text: String) {
        val v = text.trim().toIntOrNull() ?: if (text.isBlank()) 0 else return
        viewModelScope.launch { preferencesStore.setMonthlyTargetVisits(v) }
    }

    fun setVisitMode(mode: VisitCountingMode) {
        viewModelScope.launch {
            preferencesStore.setVisitCountingMode(mode)
            repository.recomputeLocal()
        }
    }

    fun setDefaultGym(id: String?) {
        viewModelScope.launch { preferencesStore.setDefaultGymId(id) }
    }

    fun setVaultWriteEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setVaultWriteEnabled(enabled) }
    }

    fun setAutoLog(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setVaultAutoLogSynced(enabled, clock.now().toEpochMilli()) }
    }

    fun setVaultRoot(root: String) {
        viewModelScope.launch { preferencesStore.setVaultRoot(root) }
    }

    fun reloadCatalog() {
        viewModelScope.launch { defaultGyms.reload() }
    }

    fun saveToVault(context: android.content.Context) = run(context) { vaultLogger.saveSettings() }

    fun logMonth(context: android.content.Context) = run(context) {
        val summary = repository.summaryFlow.first()
        if (summary == null) GymVaultLogger.Outcome.NothingToDo else vaultLogger.logMonth(summary)
    }

    fun loadFromVault(context: android.content.Context) {
        viewModelScope.launch {
            busy.value = true
            val r = vaultLogger.loadSettings()
            message.value = when (r) {
                is GymVaultLogger.LoadOutcome.Applied -> {
                    repository.recomputeLocal()
                    context.getString(R.string.settings_loaded, GymMonthSettingsNote.RELATIVE_PATH) to false
                }
                GymVaultLogger.LoadOutcome.Missing ->
                    context.getString(R.string.settings_load_missing, GymMonthSettingsNote.RELATIVE_PATH) to true
                is GymVaultLogger.LoadOutcome.NeedPermission ->
                    context.getString(R.string.vault_msg_need_permission) to true
                is GymVaultLogger.LoadOutcome.Failed ->
                    context.getString(R.string.vault_msg_failed, r.message) to true
            }
            busy.value = false
        }
    }

    private fun run(context: android.content.Context, block: suspend () -> GymVaultLogger.Outcome) {
        viewModelScope.launch {
            busy.value = true
            val outcome = block()
            message.value = outcome.message(context) to (
                outcome is GymVaultLogger.Outcome.Failed || outcome is GymVaultLogger.Outcome.NeedPermission
                )
            busy.value = false
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val prefs = state.prefs

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshAccess() }

    var targetText by rememberSaveable { mutableStateOf<String?>(null) }
    var rootText by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(prefs.monthlyTargetVisits) {
        if (targetText == null) targetText = prefs.monthlyTargetVisits.takeIf { it > 0 }?.toString() ?: ""
    }
    LaunchedEffect(prefs.vaultRoot) {
        if (rootText == null) rootText = prefs.vaultRoot
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.nav_back)) }
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(20.dp))

        // ---- Counting ----
        Section(stringResource(R.string.settings_target))
        OutlinedTextField(
            value = targetText.orEmpty(),
            onValueChange = { t ->
                val digits = t.filter(Char::isDigit).take(3)
                targetText = digits
                viewModel.setMonthlyTarget(digits)
            },
            placeholder = { Text(stringResource(R.string.settings_target_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        Section(stringResource(R.string.settings_counting))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = prefs.visitCountingMode == VisitCountingMode.PER_SESSION,
                onClick = { viewModel.setVisitMode(VisitCountingMode.PER_SESSION) },
                label = { Text(stringResource(R.string.settings_counting_per_session)) },
            )
            FilterChip(
                selected = prefs.visitCountingMode == VisitCountingMode.AT_MOST_ONE_PER_DAY,
                onClick = { viewModel.setVisitMode(VisitCountingMode.AT_MOST_ONE_PER_DAY) },
                label = { Text(stringResource(R.string.settings_counting_per_day)) },
            )
        }
        Spacer(Modifier.height(16.dp))

        Section(stringResource(R.string.settings_default_gym))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val effective = prefs.defaultGymId?.takeIf { id -> state.gyms.any { it.id == id } }
                ?: state.gyms.firstOrNull()?.id
            state.gyms.forEach { gym ->
                FilterChip(
                    selected = effective == gym.id,
                    onClick = { viewModel.setDefaultGym(gym.id) },
                    label = { Text(gym.name) },
                )
            }
        }
        CatalogLine(state)

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        // ---- Vault ----
        Text(
            text = stringResource(R.string.settings_vault_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.settings_vault_hint, VaultWorkoutFormat.HEADING),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        ToggleRow(
            label = stringResource(R.string.settings_vault_enable),
            checked = prefs.vaultWriteEnabled,
            onChange = viewModel::setVaultWriteEnabled,
        )
        ToggleRow(
            label = stringResource(R.string.settings_vault_autolog),
            checked = prefs.vaultAutoLogSynced,
            enabled = prefs.vaultWriteEnabled,
            onChange = viewModel::setAutoLog,
        )
        Spacer(Modifier.height(12.dp))

        Section(stringResource(R.string.settings_vault_root))
        OutlinedTextField(
            value = rootText.orEmpty(),
            onValueChange = {
                rootText = it
                viewModel.setVaultRoot(it)
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (rootText != VaultPaths.DEFAULT_VAULT_ROOT) {
            TextButton(onClick = {
                rootText = VaultPaths.DEFAULT_VAULT_ROOT
                viewModel.setVaultRoot(VaultPaths.DEFAULT_VAULT_ROOT)
                viewModel.reloadCatalog()
            }) { Text(stringResource(R.string.settings_vault_root_reset)) }
        }
        Spacer(Modifier.height(12.dp))

        Text(
            text = if (state.hasStorageAccess) {
                stringResource(R.string.settings_storage_ok)
            } else {
                stringResource(R.string.settings_storage_missing)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.hasStorageAccess) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        if (!state.hasStorageAccess) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    try {
                        context.startActivity(viewModel.manageAccessIntent())
                    } catch (_: ActivityNotFoundException) {
                        runCatching { context.startActivity(viewModel.manageAccessFallbackIntent()) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_grant_storage)) }
        }
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { viewModel.saveToVault(context) },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.settings_save_to_vault)) }
        Text(
            text = GymMonthSettingsNote.RELATIVE_PATH,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { viewModel.loadFromVault(context) },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.settings_load_from_vault)) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { viewModel.logMonth(context) },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.settings_log_month)) }

        state.message?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.messageIsError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.settings_vault_privacy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun CatalogLine(state: SettingsUiState) {
    val c = state.catalog ?: return
    val text = when (c.source) {
        VaultCatalog.Source.VAULT -> stringResource(R.string.settings_gyms_from_vault, DefaultGymsRepository.VAULT_CATALOG_PATH)
        VaultCatalog.Source.SEEDED -> stringResource(R.string.settings_gyms_seeded, DefaultGymsRepository.VAULT_CATALOG_PATH)
        VaultCatalog.Source.BUNDLED -> stringResource(R.string.settings_gyms_bundled, DefaultGymsRepository.VAULT_CATALOG_PATH)
    }
    Spacer(Modifier.height(6.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
    )
}

@Composable
private fun Section(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
