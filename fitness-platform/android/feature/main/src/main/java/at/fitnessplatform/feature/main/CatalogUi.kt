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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.fitnessplatform.core.model.CatalogExercise
import at.fitnessplatform.domain.isCompatibleWith

@Composable
fun CatalogRoute(onOpen: (String) -> Unit, onBack: () -> Unit, viewModel: CatalogViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.catalog_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.catalog_separation))
        if (state.offline) AssistChip(onClick = viewModel::refresh, label = { Text(stringResource(R.string.catalog_offline)) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !state.showAll,
                onClick = { viewModel.setShowAll(false) },
                label = { Text(stringResource(R.string.catalog_compatible)) },
            )
            FilterChip(
                selected = state.showAll,
                onClick = { viewModel.setShowAll(true) },
                label = { Text(stringResource(R.string.catalog_show_all)) },
            )
        }
        if (state.requiresLocationSelection) Text(stringResource(R.string.catalog_select_location))
        state.activeLocation?.let { Text(stringResource(R.string.catalog_active_location, it.name)) }
        OutlinedTextField(
            value = state.filter.query,
            onValueChange = viewModel::setQuery,
            label = { Text(stringResource(R.string.catalog_search)) },
            modifier = Modifier.fillMaxWidth(),
        )
        CatalogFilterMenu(stringResource(R.string.catalog_muscle), state.filter.muscle, state.muscles.map { it.slug to it.name }, viewModel::setMuscle)
        CatalogFilterMenu(stringResource(R.string.catalog_equipment), state.filter.equipment, state.equipment.map { it.slug to it.name }, viewModel::setEquipment)
        when {
            state.loading -> {
                val loadingDescription = stringResource(R.string.catalog_loading)
                CircularProgressIndicator(
                    Modifier.semantics { contentDescription = loadingDescription },
                )
            }
            state.exercises.isEmpty() -> Text(stringResource(R.string.catalog_empty))
            else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.exercises, key = { it.id }) { exercise ->
                    val openDescription = stringResource(R.string.catalog_open_exercise, exercise.name)
                    val activeLocation = state.activeLocation
                    Card(onClick = { onOpen(exercise.id) }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = openDescription }) {
                        Column(Modifier.padding(12.dp)) {
                            Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                            val muscleNames = exercise.muscles.joinToString { muscle ->
                                state.muscles.firstOrNull { it.slug == muscle.slug }?.name ?: muscle.slug
                            }
                            val equipmentNames = exercise.equipment.joinToString { slug ->
                                state.equipment.firstOrNull { it.slug == slug }?.name ?: slug
                            }
                            Text("$muscleNames · $equipmentNames")
                            if (state.showAll && activeLocation?.let { !exercise.isCompatibleWith(it) } == true) {
                                val missing = exercise.equipment.filterNot { it in activeLocation.availableEquipment }
                                Text(stringResource(R.string.catalog_missing_equipment, missing.joinToString()))
                            }
                        }
                    }
                }
            }
        }
        state.error?.let { code ->
            Text(
                stringResource(if (code == "CATALOG_SEED_FAILED") R.string.catalog_seed_failed else R.string.catalog_refresh_failed),
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row { Button(onClick = viewModel::refresh) { Text(stringResource(R.string.catalog_refresh)) }; TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } }
    }
}

@Composable
private fun CatalogFilterMenu(label: String, selected: String?, options: List<Pair<String, String>>, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = it }) {
        OutlinedTextField(
            value = options.firstOrNull { it.first == selected }?.second ?: stringResource(R.string.catalog_all),
            onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem({ Text(stringResource(R.string.catalog_all)) }, onClick = { onSelect(null); expanded = false })
            options.forEach { option -> DropdownMenuItem({ Text(option.second) }, onClick = { onSelect(option.first); expanded = false }) }
        }
    }
}

@Composable
fun CatalogDetailRoute(
    id: String,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: CatalogViewModel = hiltViewModel(),
) {
    val detail by viewModel.detailState(id).collectAsStateWithLifecycle(CatalogDetailState.Loading)
    CatalogDetail(detail, onOpen, onBack)
}

@Composable
private fun CatalogDetail(detail: CatalogDetailState, onOpen: (String) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (detail) {
            CatalogDetailState.Loading -> {
                val loadingDescription = stringResource(R.string.catalog_detail_loading)
                CircularProgressIndicator(
                    Modifier.semantics { contentDescription = loadingDescription },
                )
            }
            CatalogDetailState.NotFound -> Text(stringResource(R.string.catalog_not_found))
            is CatalogDetailState.Error -> Text(stringResource(R.string.catalog_detail_error))
            is CatalogDetailState.Loaded -> with(detail.exercise) {
                Text(name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Text(description)
                Text(
                    stringResource(
                        R.string.catalog_detail_muscles,
                        muscles.joinToString { "${it.slug} (${it.role.name.lowercase()})" },
                    ),
                )
                Text(stringResource(R.string.catalog_detail_equipment, equipment.joinToString()))
                Text(stringResource(R.string.catalog_detail_source, source, licenseName))
                Text(provenance, style = MaterialTheme.typography.bodySmall)
                if (!detail.compatible) {
                    Text(stringResource(R.string.catalog_missing_equipment, detail.missingEquipment.joinToString()))
                    if (detail.alternatives.isNotEmpty()) {
                        Text(stringResource(R.string.catalog_alternatives), style = MaterialTheme.typography.titleMedium)
                        detail.alternatives.forEach { alternative ->
                            TextButton(onClick = { onOpen(alternative.id) }) { Text(alternative.name) }
                        }
                    }
                }
            }
        }
        TextButton(onClick = onBack) { Text(stringResource(R.string.catalog_back)) }
    }
}
