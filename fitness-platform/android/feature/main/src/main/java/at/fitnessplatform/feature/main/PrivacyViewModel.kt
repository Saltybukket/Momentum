package at.fitnessplatform.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.fitnessplatform.domain.SyncPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

data class PrivacyUiState(
    val syncEnabled: Boolean = false,
    val pendingCount: Int = 0,
    val changing: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class PrivacyViewModel @Inject constructor(
    private val repository: SyncPreferencesRepository,
) : ViewModel() {
    private val changing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    val state = combine(
        repository.observeEnabled(),
        repository.observePendingCount(),
        changing,
        error,
    ) { enabled, pending, busy, failure -> PrivacyUiState(enabled, pending, busy, failure) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrivacyUiState())

    fun setSyncEnabled(enabled: Boolean) = viewModelScope.launch {
        changing.value = true
        error.value = null
        runCatching { repository.setEnabled(enabled) }
            .onFailure { error.value = "SYNC_PREFERENCE_FAILED" }
        changing.value = false
    }
}
