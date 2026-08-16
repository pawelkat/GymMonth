package com.gymmonth.app.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gymmonth.app.R

/**
 * In-app Samsung Health → Health Connect bridge checklist (product-critical for Watch 7).
 * Checkboxes are local UX only — not persisted as truth about the bridge.
 */
@Composable
fun SamsungBridgeChecklist(
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
) {
    val items = listOf(
        R.string.bridge_item_shealth,
        R.string.bridge_item_watch,
        R.string.bridge_item_gym_sync,
        R.string.bridge_item_hc_export,
        R.string.bridge_item_gymmonth_read,
    )
    val checked = remember { mutableStateMapOf<Int, Boolean>() }

    Column(modifier = modifier.fillMaxWidth()) {
        if (showTitle) {
            Text(
                text = stringResource(R.string.bridge_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.bridge_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        items.forEach { resId ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Checkbox(
                    checked = checked[resId] == true,
                    onCheckedChange = { checked[resId] = it == true },
                )
                Text(
                    text = stringResource(resId),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.bridge_footer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}
