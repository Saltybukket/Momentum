package at.fitnessplatform.feature.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PrivacyScreen(viewModel: PrivacyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PrivacyContent(state, viewModel::setSyncEnabled, viewModel::resetCredentialsForNewIdentity)
}

@Composable
internal fun PrivacyContent(
    state: PrivacyUiState,
    onSetSyncEnabled: (Boolean) -> Unit,
    onResetCredentials: () -> Unit,
) {
    var confirmEnable by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val credentialBlocked = state.credentialState in setOf(
        CredentialRecoveryUiState.RECOVERY_REJECTED,
        CredentialRecoveryUiState.INVALIDATED,
        CredentialRecoveryUiState.RESET_ERROR,
    )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.privacy_local_default))
        Text(stringResource(R.string.privacy_catalog_independent))
        Text(pluralStringResource(R.plurals.privacy_pending, state.pendingCount, state.pendingCount))
        Switch(
            checked = state.syncEnabled,
            enabled = !state.changing && !credentialBlocked &&
                state.credentialState != CredentialRecoveryUiState.RESETTING,
            onCheckedChange = { enabled ->
                if (enabled) confirmEnable = true else onSetSyncEnabled(false)
            },
        )
        Text(stringResource(if (state.syncEnabled) R.string.privacy_enabled else R.string.privacy_disabled))
        if (state.changing) Text(stringResource(R.string.privacy_changing))
        state.error?.let { Text(stringResource(R.string.privacy_error), color = MaterialTheme.colorScheme.error) }
        if (credentialBlocked) {
            Text(
                stringResource(
                    if (state.credentialState == CredentialRecoveryUiState.RECOVERY_REJECTED) {
                        R.string.privacy_credentials_rejected
                    } else {
                        R.string.privacy_credentials_invalidated
                    },
                ),
                color = MaterialTheme.colorScheme.error,
            )
            Text(stringResource(R.string.privacy_credentials_local_data))
            Button(onClick = { confirmReset = true }) {
                Text(stringResource(R.string.privacy_credentials_reset))
            }
        }
        if (state.credentialState == CredentialRecoveryUiState.RESETTING) {
            Text(stringResource(R.string.privacy_credentials_resetting))
        }
        if (state.credentialState == CredentialRecoveryUiState.RESET_ERROR) {
            Text(
                stringResource(R.string.privacy_credentials_reset_error),
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(stringResource(R.string.privacy_disable_note))
    }
    if (confirmEnable) AlertDialog(
        onDismissRequest = { confirmEnable = false },
        title = { Text(stringResource(R.string.privacy_confirm_title)) },
        text = { Text(stringResource(R.string.privacy_confirm_text)) },
        confirmButton = { Button(onClick = { onSetSyncEnabled(true); confirmEnable = false }) { Text(stringResource(R.string.privacy_enable)) } },
        dismissButton = { TextButton(onClick = { confirmEnable = false }) { Text(stringResource(R.string.privacy_keep_local)) } },
    )
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text(stringResource(R.string.privacy_credentials_confirm_title)) },
        text = { Text(stringResource(R.string.privacy_credentials_confirm_text)) },
        confirmButton = {
            Button(onClick = {
                onResetCredentials()
                confirmReset = false
            }) { Text(stringResource(R.string.privacy_credentials_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = { confirmReset = false }) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
