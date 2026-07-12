package at.fitnessplatform.feature.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PrivacyScreen(onBack: () -> Unit, viewModel: PrivacyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmEnable by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.privacy_local_default))
        Text(stringResource(R.string.privacy_catalog_independent))
        Text(stringResource(R.string.privacy_pending, state.pendingCount))
        Switch(
            checked = state.syncEnabled,
            enabled = !state.changing,
            onCheckedChange = { enabled ->
                if (enabled) confirmEnable = true else viewModel.setSyncEnabled(false)
            },
        )
        Text(stringResource(if (state.syncEnabled) R.string.privacy_enabled else R.string.privacy_disabled))
        if (state.changing) Text(stringResource(R.string.privacy_changing))
        state.error?.let { Text(stringResource(R.string.privacy_error), color = MaterialTheme.colorScheme.error) }
        Text(stringResource(R.string.privacy_disable_note))
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
    }
    if (confirmEnable) AlertDialog(
        onDismissRequest = { confirmEnable = false },
        title = { Text(stringResource(R.string.privacy_confirm_title)) },
        text = { Text(stringResource(R.string.privacy_confirm_text)) },
        confirmButton = { Button(onClick = { viewModel.setSyncEnabled(true); confirmEnable = false }) { Text(stringResource(R.string.privacy_enable)) } },
        dismissButton = { TextButton(onClick = { confirmEnable = false }) { Text(stringResource(R.string.privacy_keep_local)) } },
    )
}
