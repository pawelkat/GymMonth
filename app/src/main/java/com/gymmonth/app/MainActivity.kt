package com.gymmonth.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gymmonth.app.data.PreferencesStore
import com.gymmonth.app.ui.addvisit.AddVisitScreen
import com.gymmonth.app.ui.diagnostics.DiagnosticsScreen
import com.gymmonth.app.ui.home.HomeScreen
import com.gymmonth.app.ui.navigation.AppDestination
import com.gymmonth.app.ui.onboarding.OnboardingScreen
import com.gymmonth.app.ui.session.SessionDetailsScreen
import com.gymmonth.app.ui.theme.GymMonthTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MainViewModel @Inject constructor(
    preferencesStore: PreferencesStore,
) : ViewModel() {
    val onboardingCompleted: StateFlow<Boolean?> =
        preferencesStore.preferencesFlow
            .map { it.onboardingCompleted }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/**
 * Companion navigation: onboarding, home, diagnostics, session details, add visit.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GymMonthTheme {
                val mainVm: MainViewModel = hiltViewModel()
                val onboardingDone by mainVm.onboardingCompleted.collectAsStateWithLifecycle()
                var destination by remember { mutableStateOf(AppDestination.HOME) }
                var selectedSessionId by remember { mutableStateOf<String?>(null) }
                var sessionDetailsBack by remember { mutableStateOf(AppDestination.HOME) }

                LaunchedEffect(onboardingDone) {
                    when (onboardingDone) {
                        false -> destination = AppDestination.ONBOARDING
                        true -> {
                            if (destination == AppDestination.ONBOARDING) {
                                destination = AppDestination.HOME
                            }
                        }
                        null -> Unit
                    }
                }

                fun openSession(sessionId: String, from: AppDestination) {
                    selectedSessionId = sessionId
                    sessionDetailsBack = from
                    destination = AppDestination.SESSION_DETAILS
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val pad = Modifier.padding(innerPadding)
                    when (destination) {
                        AppDestination.ONBOARDING,
                        AppDestination.ONBOARDING_REPLAY,
                        -> OnboardingScreen(
                            onFinished = { destination = AppDestination.HOME },
                            modifier = pad,
                        )
                        AppDestination.HOME,
                        AppDestination.SETTINGS,
                        -> HomeScreen(
                            onOpenOnboarding = {
                                destination = AppDestination.ONBOARDING_REPLAY
                            },
                            onOpenDiagnostics = {
                                destination = AppDestination.DIAGNOSTICS
                            },
                            onOpenSession = { openSession(it, AppDestination.HOME) },
                            onAddVisit = { destination = AppDestination.ADD_VISIT },
                            modifier = pad,
                        )
                        AppDestination.DIAGNOSTICS -> DiagnosticsScreen(
                            onBack = { destination = AppDestination.HOME },
                            onOpenSession = { openSession(it, AppDestination.DIAGNOSTICS) },
                            modifier = pad,
                        )
                        AppDestination.ADD_VISIT -> AddVisitScreen(
                            onBack = { destination = AppDestination.HOME },
                            modifier = pad,
                        )
                        AppDestination.SESSION_DETAILS -> {
                            val id = selectedSessionId
                            if (id == null) {
                                destination = sessionDetailsBack
                            } else {
                                SessionDetailsScreen(
                                    sessionId = id,
                                    onBack = { destination = sessionDetailsBack },
                                    modifier = pad,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
