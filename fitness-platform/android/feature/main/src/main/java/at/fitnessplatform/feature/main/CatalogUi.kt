@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package at.fitnessplatform.feature.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
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
fun CatalogRoute(onOpen: (String) -> Unit, viewModel: CatalogViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CatalogScreen(
        state = state,
        onOpen = onOpen,
        onRefresh = viewModel::refresh,
        onShowAll = viewModel::setShowAll,
        onQuery = viewModel::setQuery,
        onMuscle = viewModel::setMuscle,
        onEquipment = viewModel::setEquipment,
    )
}

@Composable
@Suppress("CyclomaticComplexMethod")
internal fun CatalogScreen(
    state: CatalogUiState,
    onOpen: (String) -> Unit,
    onRefresh: () -> Unit,
    onShowAll: (Boolean) -> Unit,
    onQuery: (String) -> Unit,
    onMuscle: (String?) -> Unit,
    onEquipment: (String?) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.catalog_separation))
        if (state.offline) AssistChip(onClick = onRefresh, label = { Text(stringResource(R.string.catalog_offline)) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !state.showAll,
                onClick = { onShowAll(false) },
                label = { Text(stringResource(R.string.catalog_compatible)) },
            )
            FilterChip(
                selected = state.showAll,
                onClick = { onShowAll(true) },
                label = { Text(stringResource(R.string.catalog_show_all)) },
            )
        }
        if (state.requiresLocationSelection) Text(stringResource(R.string.catalog_select_location))
        state.activeLocation?.let { Text(stringResource(R.string.catalog_active_location, it.name)) }
        OutlinedTextField(
            value = state.filter.query,
            onValueChange = onQuery,
            label = { Text(stringResource(R.string.catalog_search)) },
            modifier = Modifier.fillMaxWidth(),
        )
        CatalogFilterMenu(stringResource(R.string.catalog_muscle), state.filter.muscle, state.muscles.map { it.slug to it.name }, onMuscle)
        CatalogFilterMenu(stringResource(R.string.catalog_equipment), state.filter.equipment, state.equipment.map { it.slug to it.name }, onEquipment)
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
                                val missingLabels = missing.joinToString { slug ->
                                    state.equipment.firstOrNull { it.slug == slug }?.name ?: slug
                                }
                                Text(stringResource(R.string.catalog_missing_equipment, missingLabels))
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
        Button(onClick = onRefresh) { Text(stringResource(R.string.catalog_refresh)) }
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
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth(),
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
    onSelectLocation: () -> Unit,
    viewModel: CatalogViewModel = hiltViewModel(),
) {
    val detail by viewModel.detailState(id).collectAsStateWithLifecycle(CatalogDetailState.Loading)
    CatalogDetail(detail, onOpen, onSelectLocation)
}

@Composable
@Suppress("CyclomaticComplexMethod")
internal fun CatalogDetail(
    detail: CatalogDetailState,
    onOpen: (String) -> Unit,
    onSelectLocation: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
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
                val primaryRole = stringResource(R.string.muscle_role_primary)
                val secondaryRole = stringResource(R.string.muscle_role_secondary)
                Text(name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Text(description)
                Text(
                    stringResource(
                        R.string.catalog_detail_muscles,
                        muscles.joinToString { muscle ->
                            val name = detail.muscleLabels[muscle.slug] ?: muscle.slug
                            "$name (${if (muscle.role == at.fitnessplatform.core.model.MuscleRole.PRIMARY) primaryRole else secondaryRole})"
                        },
                    ),
                )
                Text(
                    stringResource(
                        R.string.catalog_detail_equipment,
                        equipment.joinToString { detail.equipmentLabels[it] ?: it },
                    ),
                )
                Text(stringResource(R.string.catalog_detail_tracking, catalogTrackingTypeLabel(trackingType)))
                Text(stringResource(R.string.catalog_detail_source, source, licenseName))
                Text(provenance, style = MaterialTheme.typography.bodySmall)
                when (detail.compatibility) {
                    CatalogCompatibility.COMPATIBLE -> Text(stringResource(R.string.catalog_compatibility_compatible))
                    CatalogCompatibility.LOCATION_REQUIRED -> {
                        Text(stringResource(R.string.catalog_compatibility_location_required))
                        Button(onClick = onSelectLocation) { Text(stringResource(R.string.locations_switch)) }
                    }
                    CatalogCompatibility.MISSING_EQUIPMENT -> {
                        val unknownEquipment = stringResource(R.string.equipment_unknown_generic)
                        val missing = detail.missingEquipment.joinToString { item ->
                            item.label ?: "$unknownEquipment (${item.slug})"
                        }
                        Text(stringResource(R.string.catalog_missing_equipment, missing))
                    }
                }
                if (detail.compatibility == CatalogCompatibility.MISSING_EQUIPMENT) {
                    if (detail.alternatives.isNotEmpty()) {
                        Text(stringResource(R.string.catalog_alternatives), style = MaterialTheme.typography.titleMedium)
                        detail.alternatives.forEach { alternative ->
                            Card(onClick = { onOpen(alternative.id) }, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    alternative.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun catalogTrackingTypeLabel(type: at.fitnessplatform.core.model.TrackingType) = stringResource(
    when (type) {
        at.fitnessplatform.core.model.TrackingType.REPS_WEIGHT -> R.string.tracking_reps_weight
        at.fitnessplatform.core.model.TrackingType.REPS -> R.string.tracking_reps
        at.fitnessplatform.core.model.TrackingType.DURATION -> R.string.tracking_duration
        at.fitnessplatform.core.model.TrackingType.DISTANCE_DURATION -> R.string.tracking_distance_duration
        at.fitnessplatform.core.model.TrackingType.MANUAL -> R.string.tracking_manual
    },
)
