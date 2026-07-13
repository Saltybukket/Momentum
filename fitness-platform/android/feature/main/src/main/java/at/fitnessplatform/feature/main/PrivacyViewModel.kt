package at.fitnessplatform.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.fitnessplatform.domain.SyncPreferencesRepository
import at.fitnessplatform.domain.GuestCredentialStatus
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
    val credentialState: CredentialRecoveryUiState = CredentialRecoveryUiState.READY,
)

enum class CredentialRecoveryUiState {
    READY,
    RECOVERY_REJECTED,
    INVALIDATED,
    RESETTING,
    RESET_ERROR,
}

@HiltViewModel
class PrivacyViewModel @Inject constructor(
    private val repository: SyncPreferencesRepository,
) : ViewModel() {
    private val changing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val resetState = MutableStateFlow<CredentialRecoveryUiState?>(null)
    private val credentialState = combine(
        repository.observeCredentialState(),
        resetState,
    ) { credentials, reset -> reset ?: credentials.toUiState() }
    val state = combine(
        repository.observeEnabled(),
        repository.observePendingCount(),
        changing,
        error,
        credentialState,
    ) { enabled, pending, busy, failure, credentials ->
        PrivacyUiState(
            enabled,
            pending,
            busy,
            failure,
            credentials,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrivacyUiState())

    fun setSyncEnabled(enabled: Boolean) = viewModelScope.launch {
        changing.value = true
        error.value = null
        runCatching { repository.setEnabled(enabled) }
            .onFailure { error.value = "SYNC_PREFERENCE_FAILED" }
        changing.value = false
    }

    fun resetCredentialsForNewIdentity() = viewModelScope.launch {
        resetState.value = CredentialRecoveryUiState.RESETTING
        error.value = null
        runCatching { repository.resetCredentialsForNewIdentity() }
            .onSuccess { resetState.value = null }
            .onFailure { resetState.value = CredentialRecoveryUiState.RESET_ERROR }
    }

    private fun GuestCredentialStatus.toUiState() = when (this) {
        GuestCredentialStatus.READY -> CredentialRecoveryUiState.READY
        GuestCredentialStatus.INVALIDATED -> CredentialRecoveryUiState.INVALIDATED
        GuestCredentialStatus.RECOVERY_REJECTED -> CredentialRecoveryUiState.RECOVERY_REJECTED
    }
}
