package com.gymmonth.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymmonth.app.data.PreferencesStore
import com.gymmonth.app.data.UserPreferences
import com.gymmonth.app.domain.MonthlyGymSummary
import com.gymmonth.app.health.GymVisitRepository
import com.gymmonth.app.sync.SyncReason
import com.gymmonth.app.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val summary: MonthlyGymSummary? = null,
    val preferences: UserPreferences = UserPreferences(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: GymVisitRepository,
    private val syncScheduler: SyncScheduler,
    preferencesStore: PreferencesStore,
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        repository.summaryFlow,
        preferencesStore.preferencesFlow,
        combine(refreshing, error) { r, e -> r to e },
    ) { summary, prefs, refreshErr ->
        val (isRefreshing, err) = refreshErr
        HomeUiState(
            summary = summary,
            preferences = prefs,
            loading = summary == null && isRefreshing,
            refreshing = isRefreshing,
            errorMessage = err,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(loading = true),
    )

    init {
        syncScheduler.ensureScheduled()
        refresh(reason = SyncReason.APP_FOREGROUND)
    }

    fun refresh(reason: SyncReason = SyncReason.MANUAL) {
        viewModelScope.launch {
            refreshing.value = true
            error.value = null
            try {
                repository.sync(reason)
                syncScheduler.ensureScheduled()
            } catch (e: Exception) {
                error.value = e.message ?: e::class.java.simpleName
            } finally {
                refreshing.value = false
            }
        }
    }
}
