package at.fitnessplatform.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.fitnessplatform.domain.SyncPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PrivacyUiState(val syncEnabled: Boolean = false, val pendingCount: Int = 0)

@HiltViewModel
class PrivacyViewModel @Inject constructor(
    private val repository: SyncPreferencesRepository,
) : ViewModel() {
    val state = combine(repository.observeEnabled(), repository.observePendingCount(), ::PrivacyUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrivacyUiState())

    fun setSyncEnabled(enabled: Boolean) = viewModelScope.launch { repository.setEnabled(enabled) }
}
