package com.gymmonth.app.ui.session

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gymmonth.app.R
import com.gymmonth.app.domain.GymPlace

/**
 * Dropdown of default gyms (PureGym first) plus an "Other gym…" free-text option.
 * Shared by session details and the Add visit screen.
 */
@Composable
fun GymDropdown(
    gyms: List<GymPlace>,
    selectedLabel: String?,
    onPickGym: (GymPlace) -> Unit,
    onPickCustom: () -> Unit,
    onClear: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(selectedLabel ?: stringResource(R.string.gym_picker_choose))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            gyms.forEach { gym ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(gym.name, color = MaterialTheme.colorScheme.onBackground)
                            gym.address?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onPickGym(gym)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.gym_picker_custom)) },
                onClick = {
                    expanded = false
                    onPickCustom()
                },
            )
            if (onClear != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.gym_picker_clear)) },
                    onClick = {
                        expanded = false
                        onClear()
                    },
                )
            }
        }
    }
}

@Composable
fun CustomGymField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            label = { Text(stringResource(R.string.gym_picker_custom_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
