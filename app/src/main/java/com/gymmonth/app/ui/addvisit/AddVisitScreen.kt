package com.gymmonth.app.ui.addvisit

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymmonth.app.R
import com.gymmonth.app.data.DefaultGymsRepository
import com.gymmonth.app.domain.CUSTOM_GYM_PLACE_ID
import com.gymmonth.app.domain.GymPlace
import com.gymmonth.app.domain.ManualVisit
import com.gymmonth.app.domain.MonthRange
import com.gymmonth.app.health.GymVisitRepository
import com.gymmonth.app.ui.session.CustomGymField
import com.gymmonth.app.ui.session.GymDropdown
import com.gymmonth.app.ui.theme.AmoledBlack
import com.gymmonth.app.util.ClockProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class AddVisitViewModel @Inject constructor(
    private val repository: GymVisitRepository,
    private val clock: ClockProvider,
    defaultGyms: DefaultGymsRepository,
) : ViewModel() {
    val gyms: List<GymPlace> = defaultGyms.selectableGyms()

    fun zone(): ZoneId = clock.zoneId()

    fun today(): LocalDate = clock.now().atZone(clock.zoneId()).toLocalDate()

    fun isInCurrentMonth(date: LocalDate): Boolean {
        val month = MonthRange.current(zoneId = clock.zoneId(), clock = clock.clock())
        return month.containsSessionStart(date.atStartOfDay(clock.zoneId()).toInstant())
    }

    /** Saves an app-local manual visit. Never written to Health Connect. */
    fun save(
        date: LocalDate,
        time: LocalTime,
        durationMinutes: Int,
        gymPlaceId: String?,
        gymName: String?,
        onSaved: () -> Unit,
    ) {
        val start = date.atTime(time).atZone(clock.zoneId()).toInstant()
        val visit = ManualVisit.create(
            start = start,
            duration = Duration.ofMinutes(durationMinutes.toLong()),
            gymPlaceId = gymPlaceId,
            gymName = gymName,
            nowEpochMs = clock.now().toEpochMilli(),
        )
        viewModelScope.launch {
            repository.addManualVisit(visit)
            onSaved()
        }
    }
}

private val DURATION_OPTIONS = listOf(45, 60, 90, 120)

@Composable
fun AddVisitScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddVisitViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val defaultGym = viewModel.gyms.firstOrNull()

    var epochDay by rememberSaveable { mutableLongStateOf(viewModel.today().toEpochDay()) }
    var minuteOfDay by rememberSaveable {
        mutableIntStateOf(
            LocalTime.now(viewModel.zone()).let { it.hour * 60 + (it.minute / 15) * 15 },
        )
    }
    var durationMinutes by rememberSaveable { mutableIntStateOf(60) }
    var gymPlaceId by rememberSaveable { mutableStateOf(defaultGym?.id) }
    var gymName by rememberSaveable { mutableStateOf(defaultGym?.name) }
    var showCustom by rememberSaveable { mutableStateOf(false) }
    var customName by rememberSaveable { mutableStateOf("") }
    var saving by rememberSaveable { mutableStateOf(false) }

    val date = LocalDate.ofEpochDay(epochDay)
    val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
    val dateFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
    val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault())

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.nav_back)) }

        Text(
            text = stringResource(R.string.add_visit_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.add_visit_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))

        SectionLabel(stringResource(R.string.add_visit_date))
        OutlinedButton(
            onClick = {
                DatePickerDialog(
                    context,
                    { _, y, m, d -> epochDay = LocalDate.of(y, m + 1, d).toEpochDay() },
                    date.year,
                    date.monthValue - 1,
                    date.dayOfMonth,
                ).show()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(dateFmt.format(date)) }
        if (!viewModel.isInCurrentMonth(date)) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.add_visit_other_month),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        SectionLabel(stringResource(R.string.add_visit_start_time))
        OutlinedButton(
            onClick = {
                TimePickerDialog(
                    context,
                    { _, h, min -> minuteOfDay = h * 60 + min },
                    time.hour,
                    time.minute,
                    DateFormat.is24HourFormat(context),
                ).show()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(timeFmt.format(time)) }
        Spacer(modifier = Modifier.height(16.dp))

        SectionLabel(stringResource(R.string.add_visit_duration))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DURATION_OPTIONS.forEach { mins ->
                FilterChip(
                    selected = durationMinutes == mins,
                    onClick = { durationMinutes = mins },
                    label = { Text(stringResource(R.string.session_duration_minutes, mins)) },
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        SectionLabel(stringResource(R.string.add_visit_gym))
        GymDropdown(
            gyms = viewModel.gyms,
            selectedLabel = gymName,
            onPickGym = {
                showCustom = false
                gymPlaceId = it.id
                gymName = it.name
            },
            onPickCustom = {
                showCustom = true
                gymPlaceId = CUSTOM_GYM_PLACE_ID
                gymName = customName.ifBlank { null }
            },
            onClear = {
                showCustom = false
                gymPlaceId = null
                gymName = null
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (showCustom) {
            CustomGymField(
                value = customName,
                onValueChange = {
                    customName = it
                    gymName = it.trim().ifEmpty { null }
                },
            )
        }

        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = {
                saving = true
                viewModel.save(
                    date = date,
                    time = time,
                    durationMinutes = durationMinutes,
                    gymPlaceId = if (gymName == null) null else gymPlaceId,
                    gymName = gymName,
                    onSaved = onBack,
                )
            },
            enabled = !saving && !(showCustom && customName.isBlank()),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.add_visit_save))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.add_visit_local_only),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}
