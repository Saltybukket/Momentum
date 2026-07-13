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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.fitnessplatform.core.model.EquipmentDefinition
import at.fitnessplatform.core.model.EquipmentDefinitions
import at.fitnessplatform.core.model.LocationPreset
import at.fitnessplatform.core.model.LocationType
import at.fitnessplatform.core.model.TrainingLocation

@Composable
fun ActiveLocationCard(
    onManage: () -> Unit,
    viewModel: TrainingLocationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.locations_active_title), style = MaterialTheme.typography.titleLarge)
            val active = state.active
            Text(
                if (active == null) stringResource(R.string.locations_none_selected)
                else stringResource(R.string.locations_active_summary, active.name, active.equipmentSlugs.size),
            )
            TextButton(onClick = onManage) { Text(stringResource(R.string.locations_switch)) }
        }
    }
}

@Composable
fun TrainingLocationsRoute(
    onBack: () -> Unit,
    viewModel: TrainingLocationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var editingEquipment by remember { mutableStateOf<TrainingLocation?>(null) }
    var renaming by remember { mutableStateOf<TrainingLocation?>(null) }
    var deleting by remember { mutableStateOf<TrainingLocation?>(null) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.locations_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.locations_description))
        Button(onClick = { creating = true }, enabled = !state.busy) {
            Text(stringResource(R.string.locations_add))
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!state.loading && state.locations.isEmpty()) {
                item { Text(stringResource(R.string.locations_empty)) }
            }
            items(state.locations, key = { it.id }) { location ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(location.name, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.locations_equipment_count, location.equipmentSlugs.size))
                        if (location.isActive) Text(stringResource(R.string.locations_active_badge))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = { viewModel.setActive(location.id) },
                                enabled = !state.busy && !location.isActive,
                            ) { Text(stringResource(R.string.locations_activate)) }
                            TextButton(onClick = { editingEquipment = location }) {
                                Text(stringResource(R.string.locations_equipment_edit))
                            }
                            TextButton(onClick = { renaming = location }) { Text(stringResource(R.string.edit)) }
                            TextButton(onClick = { deleting = location }) { Text(stringResource(R.string.delete)) }
                        }
                    }
                }
            }
        }
        state.error?.let { Text(stringResource(R.string.locations_error), color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
    }
    if (creating) LocationEditorDialog(
        title = stringResource(R.string.locations_add),
        initialName = "",
        initialType = LocationType.HOME,
        showPresets = true,
        onDismiss = { creating = false },
        onSave = { name, type, preset -> viewModel.create(name, type, preset); creating = false },
    )
    renaming?.let { location ->
        LocationEditorDialog(
            title = stringResource(R.string.locations_edit),
            initialName = location.name,
            initialType = location.type,
            showPresets = false,
            onDismiss = { renaming = null },
            onSave = { name, type, _ -> viewModel.rename(location, name, type); renaming = null },
        )
    }
    editingEquipment?.let { location ->
        EquipmentEditorDialog(
            location = location,
            onDismiss = { editingEquipment = null },
            onSave = { viewModel.saveEquipment(location.id, it); editingEquipment = null },
        )
    }
    deleting?.let { location ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.locations_delete_title)) },
            text = { Text(stringResource(R.string.locations_delete_message, location.name)) },
            confirmButton = { TextButton(onClick = { viewModel.delete(location.id); deleting = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun LocationEditorDialog(
    title: String,
    initialName: String,
    initialType: LocationType,
    showPresets: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, LocationType, LocationPreset) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var type by remember(initialType) { mutableStateOf(initialType) }
    var preset by remember { mutableStateOf(LocationPreset.EMPTY_CUSTOM) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name)) }) }
                item { Text(stringResource(R.string.locations_type)) }
                items(LocationType.entries) { option ->
                    Row { RadioButton(type == option, { type = option }); Text(locationTypeLabel(option)) }
                }
                if (showPresets) {
                    item { Text(stringResource(R.string.locations_preset)) }
                    items(LocationPreset.entries) { option ->
                        Row { RadioButton(preset == option, { preset = option }); Text(locationPresetLabel(option)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, type, preset) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun EquipmentEditorDialog(
    location: TrainingLocation,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selected by remember(location.id) { mutableStateOf(location.equipmentSlugs) }
    val matches = EquipmentDefinitions.all.filter { definition ->
        definition.slug != EquipmentDefinitions.NONE &&
            (query.isBlank() || definition.slug.contains(query, ignoreCase = true) || definition.aliases.any { it.contains(query, true) })
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.locations_equipment_title)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item {
                    OutlinedTextField(
                        query,
                        { query = it },
                        label = { Text(stringResource(R.string.locations_equipment_search)) },
                    )
                }
                item { Text(stringResource(R.string.locations_equipment_selected, selected.size)) }
                matches.groupBy(EquipmentDefinition::category).forEach { (category, definitions) ->
                    item { Text(equipmentCategoryLabel(category), style = MaterialTheme.typography.titleSmall) }
                    items(definitions, key = EquipmentDefinition::slug) { definition ->
                        val label = equipmentLabel(definition)
                        Row(
                            Modifier.fillMaxWidth().semantics {
                                contentDescription = label
                            },
                        ) {
                            Checkbox(
                                checked = definition.slug in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + definition.slug else selected - definition.slug
                                },
                            )
                            Text(label)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(selected) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun locationTypeLabel(type: LocationType) = stringResource(
    when (type) {
        LocationType.FITNESS_CENTER -> R.string.location_type_fitness_center
        LocationType.HOME -> R.string.location_type_home
        LocationType.OUTDOOR -> R.string.location_type_outdoor
        LocationType.HOTEL -> R.string.location_type_hotel
        LocationType.UNIVERSITY -> R.string.location_type_university
        LocationType.CUSTOM -> R.string.location_type_custom
    },
)

@Composable
private fun locationPresetLabel(preset: LocationPreset) = stringResource(
    when (preset) {
        LocationPreset.HOME_BASIC -> R.string.location_preset_home
        LocationPreset.GYM_FULL -> R.string.location_preset_gym
        LocationPreset.OUTDOOR_MINIMAL -> R.string.location_preset_outdoor
        LocationPreset.EMPTY_CUSTOM -> R.string.location_preset_empty
    },
)

@Composable
private fun equipmentCategoryLabel(category: String) = stringResource(
    when (category) {
        "accessories" -> R.string.equipment_category_accessories
        "free_weights" -> R.string.equipment_category_free_weights
        "stations" -> R.string.equipment_category_stations
        "racks" -> R.string.equipment_category_racks
        "machines" -> R.string.equipment_category_machines
        "cardio" -> R.string.equipment_category_cardio
        "space" -> R.string.equipment_category_space
        else -> R.string.equipment_category_general
    },
)

@Composable
private fun equipmentLabel(definition: EquipmentDefinition) =
    stringResource(equipmentLabelResources[definition.slug] ?: R.string.equipment_none)

private val equipmentLabelResources = mapOf(
    "mat" to R.string.equipment_mat,
    "resistance-bands" to R.string.equipment_resistance_bands,
    "dumbbells" to R.string.equipment_dumbbells,
    "adjustable-dumbbells" to R.string.equipment_adjustable_dumbbells,
    "barbell" to R.string.equipment_barbell,
    "plates" to R.string.equipment_plates,
    "bench" to R.string.equipment_bench,
    "squat-rack" to R.string.equipment_squat_rack,
    "power-rack" to R.string.equipment_power_rack,
    "smith-machine" to R.string.equipment_smith_machine,
    "cable-machine" to R.string.equipment_cable_machine,
    "pull-up-bar" to R.string.equipment_pull_up_bar,
    "dip-bars" to R.string.equipment_dip_bars,
    "kettlebell" to R.string.equipment_kettlebell,
    "suspension-trainer" to R.string.equipment_suspension_trainer,
    "chest-press" to R.string.equipment_chest_press,
    "shoulder-press" to R.string.equipment_shoulder_press,
    "lat-pulldown" to R.string.equipment_lat_pulldown,
    "row-machine" to R.string.equipment_row_machine,
    "leg-press" to R.string.equipment_leg_press,
    "hack-squat" to R.string.equipment_hack_squat,
    "leg-extension" to R.string.equipment_leg_extension,
    "leg-curl" to R.string.equipment_leg_curl,
    "calf-machine" to R.string.equipment_calf_machine,
    "adductor-machine" to R.string.equipment_adductor_machine,
    "abductor-machine" to R.string.equipment_abductor_machine,
    "treadmill" to R.string.equipment_treadmill,
    "exercise-bike" to R.string.equipment_exercise_bike,
    "cross-trainer" to R.string.equipment_cross_trainer,
    "rowing-ergometer" to R.string.equipment_rowing_ergometer,
    "stair-climber" to R.string.equipment_stair_climber,
    "open-floor" to R.string.equipment_open_floor,
)
