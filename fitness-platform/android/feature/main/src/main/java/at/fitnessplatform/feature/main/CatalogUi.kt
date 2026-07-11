@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package at.fitnessplatform.feature.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.fitnessplatform.core.model.CatalogExercise

@Composable
fun CatalogRoute(onOpen: (String) -> Unit, onBack: () -> Unit, viewModel: CatalogViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Public exercise catalog", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text("Reviewed public exercises saved on this device. Your custom exercises remain separate.")
        if (state.offline) AssistChip(onClick = viewModel::refresh, label = { Text("Offline · saved catalog") })
        CatalogFilterMenu("Muscle", state.filter.muscle, state.muscles.map { it.slug to it.name }, viewModel::setMuscle)
        CatalogFilterMenu("Equipment", state.filter.equipment, state.equipment.map { it.slug to it.name }, viewModel::setEquipment)
        when {
            state.loading -> CircularProgressIndicator(Modifier.semantics { contentDescription = "Loading exercise catalog" })
            state.exercises.isEmpty() -> Text("No catalog exercises match these filters.")
            else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.exercises, key = { it.id }) { exercise ->
                    Card(onClick = { onOpen(exercise.id) }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Open ${exercise.name}" }) {
                        Column(Modifier.padding(12.dp)) {
                            Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                            Text(exercise.muscles.joinToString { it.slug } + " · " + exercise.equipment.joinToString())
                        }
                    }
                }
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row { Button(onClick = viewModel::refresh) { Text("Refresh") }; TextButton(onClick = onBack) { Text("Back") } }
    }
}

@Composable
private fun CatalogFilterMenu(label: String, selected: String?, options: List<Pair<String, String>>, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = it }) {
        OutlinedTextField(
            value = options.firstOrNull { it.first == selected }?.second ?: "All",
            onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem({ Text("All") }, onClick = { onSelect(null); expanded = false })
            options.forEach { option -> DropdownMenuItem({ Text(option.second) }, onClick = { onSelect(option.first); expanded = false }) }
        }
    }
}

@Composable
fun CatalogDetailRoute(id: String, onBack: () -> Unit, viewModel: CatalogViewModel = hiltViewModel()) {
    val exercise by viewModel.observeExercise(id).collectAsStateWithLifecycle(null)
    CatalogDetail(exercise, onBack)
}

@Composable
private fun CatalogDetail(exercise: CatalogExercise?, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (exercise == null) Text("Catalog exercise not found.") else {
            Text(exercise.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(exercise.description)
            Text("Muscles: ${exercise.muscles.joinToString { "${it.slug} (${it.role.name.lowercase()})" }}")
            Text("Equipment: ${exercise.equipment.joinToString()}")
            Text("Source: ${exercise.source} · ${exercise.licenseName}")
            Text(exercise.provenance, style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onBack) { Text("Back to catalog") }
    }
}
