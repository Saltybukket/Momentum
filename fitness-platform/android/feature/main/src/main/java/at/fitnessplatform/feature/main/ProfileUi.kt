package at.fitnessplatform.feature.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import at.fitnessplatform.core.designsystem.MomentumCard
import at.fitnessplatform.core.designsystem.MomentumSectionHeader
import at.fitnessplatform.core.designsystem.MomentumSpacing

@Composable
internal fun ProfileScreen(
    currentName: String,
    busy: Boolean,
    onSave: (String) -> Unit,
    onLocations: () -> Unit,
    onPrivacy: () -> Unit,
) {
    var name by rememberSaveable(currentName) { mutableStateOf(currentName) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(MomentumSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(MomentumSpacing.md),
    ) {
        MomentumSectionHeader(stringResource(R.string.profile_title), stringResource(R.string.profile_identity))
        MomentumCard(Modifier.fillMaxWidth()) {
            Text(currentName, style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.display_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button({ onSave(name) }, enabled = !busy && name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.save))
            }
        }
        MomentumSectionHeader(stringResource(R.string.profile_sync_privacy))
        MomentumCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.locations_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.locations_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onLocations, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.locations_manage)) }
        }
        OutlinedButton(onClick = onPrivacy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.home_manage_privacy))
        }
    }
}
