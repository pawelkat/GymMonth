package com.gymmonth.app.ui.onboarding

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymmonth.app.R
import com.gymmonth.app.data.PreferencesStore
import com.gymmonth.app.health.HealthConnectSdkStatus
import com.gymmonth.app.ui.permissions.PermissionsRationaleActivity
import com.gymmonth.app.ui.theme.AmoledBlack
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Multi-step onboarding: Health Connect → permissions → Samsung bridge checklist.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PermissionOnboardingViewModel = hiltViewModel(),
    finishViewModel: OnboardingFinishViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) } // 0 HC, 1 perms, 2 bridge

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        viewModel.onPermissionsResult(granted)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun completeOnboarding() {
        scope.launch {
            finishViewModel.markComplete()
            onFinished()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.onboarding_step_indicator, step + 1, 3),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))

        when (step) {
            0 -> {
                Text(
                    text = stringResource(R.string.onboarding_step_hc),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (state.loading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                } else {
                    Text(
                        text = when (state.sdkStatus) {
                            HealthConnectSdkStatus.AVAILABLE ->
                                stringResource(R.string.hc_status_available)
                            HealthConnectSdkStatus.UPDATE_REQUIRED ->
                                stringResource(R.string.hc_status_update_required)
                            HealthConnectSdkStatus.UNAVAILABLE ->
                                stringResource(R.string.hc_status_unavailable)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.sdkStatus != HealthConnectSdkStatus.AVAILABLE) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { openHealthConnectPlayStore(context) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.hc_install_or_update))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { step = 1 },
                    enabled = state.sdkStatus == HealthConnectSdkStatus.AVAILABLE || !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.onboarding_next))
                }
                TextButton(
                    onClick = { step = 1 },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.onboarding_skip_for_now))
                }
            }

            1 -> {
                Text(
                    text = stringResource(R.string.onboarding_step_permissions),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (state.hasRequired) {
                        stringResource(R.string.permissions_granted)
                    } else {
                        stringResource(R.string.permissions_needed)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.hasRequired && !state.hasBackground) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.permissions_background_optional),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (state.sdkStatus == HealthConnectSdkStatus.AVAILABLE) {
                            permissionLauncher.launch(viewModel.permissionsToRequest())
                        }
                    },
                    enabled = state.sdkStatus == HealthConnectSdkStatus.AVAILABLE,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.permissions_request))
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(context, PermissionsRationaleActivity::class.java),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.privacy_open))
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { step = 0 }) {
                        Text(stringResource(R.string.onboarding_back))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(onClick = { step = 2 }) {
                        Text(stringResource(R.string.onboarding_next))
                    }
                }
            }

            else -> {
                Text(
                    text = stringResource(R.string.onboarding_step_bridge),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(12.dp))
                SamsungBridgeChecklist(showTitle = false)
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { step = 1 }) {
                        Text(stringResource(R.string.onboarding_back))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(onClick = { completeOnboarding() }) {
                        Text(stringResource(R.string.onboarding_finish))
                    }
                }
                TextButton(
                    onClick = { completeOnboarding() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.onboarding_skip_for_now))
                }
            }
        }

        state.lastError?.let { err ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = err,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@HiltViewModel
class OnboardingFinishViewModel @Inject constructor(
    private val preferencesStore: PreferencesStore,
) : ViewModel() {
    suspend fun markComplete() {
        preferencesStore.setOnboardingCompleted(true)
    }
}

internal fun openHealthConnectPlayStore(context: android.content.Context) {
    val packageName = "com.google.android.apps.healthdata"
    val market = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse("market://details?id=$packageName")
        setPackage("com.android.vending")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val web = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(market)
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(web)
        } catch (_: ActivityNotFoundException) {
            // no store
        }
    }
}
