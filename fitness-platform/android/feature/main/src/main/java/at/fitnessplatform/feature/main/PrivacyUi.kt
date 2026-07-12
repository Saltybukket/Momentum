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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PrivacyScreen(onBack: () -> Unit, viewModel: PrivacyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmEnable by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Privacy and sync", style = MaterialTheme.typography.headlineSmall)
        Text("Your profile, custom exercises and workouts stay on this device unless you explicitly enable private-data sync.")
        Text("Public catalog updates never upload private data.")
        Text("${state.pendingCount} local change(s) are waiting or need attention.")
        Switch(
            checked = state.syncEnabled,
            onCheckedChange = { enabled ->
                if (enabled) confirmEnable = true else viewModel.setSyncEnabled(false)
            },
        )
        Text(if (state.syncEnabled) "Private-data sync enabled" else "Private-data sync disabled")
        Text("Disabling stops future uploads. It does not delete data that may already have been uploaded.")
        TextButton(onClick = onBack) { Text("Back") }
    }
    if (confirmEnable) AlertDialog(
        onDismissRequest = { confirmEnable = false },
        title = { Text("Enable private-data sync?") },
        text = { Text("This creates or recovers a temporary guest session and uploads your profile, custom exercises and workouts.") },
        confirmButton = { Button(onClick = { viewModel.setSyncEnabled(true); confirmEnable = false }) { Text("Enable sync") } },
        dismissButton = { TextButton(onClick = { confirmEnable = false }) { Text("Keep local") } },
    )
}
