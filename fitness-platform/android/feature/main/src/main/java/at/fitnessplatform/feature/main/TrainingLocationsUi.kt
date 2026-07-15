@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package at.fitnessplatform.feature.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.fitnessplatform.core.model.EquipmentDefinition
import at.fitnessplatform.core.model.EquipmentDefinitions
import at.fitnessplatform.core.model.LocationPreset
import at.fitnessplatform.core.model.LocationPresets
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
                else stringResource(
                    R.string.locations_active_summary,
                    active.name,
                    pluralStringResource(
                        R.plurals.locations_equipment_count,
                        active.equipmentSlugs.size,
                        active.equipmentSlugs.size,
                    ),
                ),
            )
            TextButton(onClick = onManage) { Text(stringResource(R.string.locations_switch)) }
        }
    }
}

@Composable
fun TrainingLocationsRoute(viewModel: TrainingLocationsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var editingEquipment by remember { mutableStateOf<TrainingLocation?>(null) }
    var renaming by remember { mutableStateOf<TrainingLocation?>(null) }
    var deleting by remember { mutableStateOf<TrainingLocation?>(null) }
    CompletionEffect(
        state.completedOperation,
        onCreate = { creating = false },
        onUpdate = { renaming = null },
        onEquipment = { editingEquipment = null },
        onDelete = { deleting = null },
        onAcknowledged = viewModel::acknowledgeCompletion,
    )
    Column(
        Modifier.fillMaxSize().testTag("screen-locations").padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.locations_description))
        Button(onClick = { creating = true }, enabled = !state.busy) {
            Text(stringResource(R.string.locations_add))
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!state.loading && state.locations.isEmpty()) {
                item { Text(stringResource(R.string.locations_empty)) }
            }
            items(state.locations, key = { it.id }) { location ->
                var menuExpanded by remember(location.id) { mutableStateOf(false) }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(location.name, style = MaterialTheme.typography.titleMedium)
                        Text(locationTypeLabel(location.type), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            pluralStringResource(
                                R.plurals.locations_equipment_count,
                                location.equipmentSlugs.size,
                                location.equipmentSlugs.size,
                            ),
                        )
                        if (location.isActive) Text(stringResource(R.string.locations_active_badge))
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Button(
                                onClick = { viewModel.setActive(location.id) },
                                enabled = !state.busy && !location.isActive,
                            ) { Text(stringResource(R.string.locations_activate)) }
                            OutlinedButton(onClick = { editingEquipment = location }, enabled = !state.busy) {
                                Text(stringResource(R.string.locations_equipment_edit))
                            }
                            TextButton(onClick = { menuExpanded = true }, enabled = !state.busy) {
                                Text(stringResource(R.string.more_actions))
                            }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.edit)) },
                                    onClick = { menuExpanded = false; renaming = location },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete)) },
                                    onClick = { menuExpanded = false; deleting = location },
                                )
                            }
                        }
                    }
                }
            }
        }
        state.error?.let {
            Row {
                Text(stringResource(R.string.locations_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = viewModel::retry, enabled = !state.busy) { Text(stringResource(R.string.retry)) }
            }
        }
    }
    if (creating) LocationEditorDialog(
        title = stringResource(R.string.locations_add),
        initialName = "",
        initialType = LocationType.HOME,
        showPresets = true,
        onDismiss = { creating = false },
        saving = state.saving == LocationOperation.CREATE,
        error = state.error,
        onSave = viewModel::create,
    )
    renaming?.let { location ->
        LocationEditorDialog(
            title = stringResource(R.string.locations_edit),
            initialName = location.name,
            initialType = location.type,
            showPresets = false,
            onDismiss = { renaming = null },
            saving = state.saving == LocationOperation.UPDATE,
            error = state.error,
            onSave = { name, type, _ -> viewModel.rename(location, name, type) },
        )
    }
    editingEquipment?.let { location ->
        EquipmentEditorDialog(
            location = location,
            onDismiss = { editingEquipment = null },
            saving = state.saving == LocationOperation.EQUIPMENT,
            error = state.error,
            onSave = { viewModel.saveEquipment(location.id, it) },
        )
    }
    deleting?.let { location ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.locations_delete_title)) },
            text = { Text(stringResource(R.string.locations_delete_message, location.name)) },
            confirmButton = { TextButton(onClick = { viewModel.delete(location.id) }, enabled = !state.busy) { Text(stringResource(if (state.saving == LocationOperation.DELETE) R.string.saving else R.string.delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun CompletionEffect(
    operation: LocationOperation?,
    onCreate: () -> Unit,
    onUpdate: () -> Unit,
    onEquipment: () -> Unit,
    onDelete: () -> Unit,
    onAcknowledged: () -> Unit,
) = LaunchedEffect(operation) {
    when (operation) {
        LocationOperation.CREATE -> onCreate()
        LocationOperation.UPDATE -> onUpdate()
        LocationOperation.EQUIPMENT -> onEquipment()
        LocationOperation.DELETE -> onDelete()
        LocationOperation.ACTIVATE, null -> Unit
    }
    if (operation != null) onAcknowledged()
}

@Composable
internal fun LocationEditorDialog(
    title: String,
    initialName: String,
    initialType: LocationType,
    showPresets: Boolean,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String, LocationType, LocationPreset) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var type by remember(initialType) { mutableStateOf(initialType) }
    var preset by remember { mutableStateOf(LocationPreset.EMPTY_CUSTOM) }
    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 560.dp).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
                item {
                    OutlinedTextField(
                        name,
                        { name = it },
                        label = { Text(stringResource(R.string.name)) },
                        enabled = !saving,
                    )
                }
                item { Text(stringResource(R.string.locations_type)) }
                items(LocationType.entries) { option ->
                    Row {
                        RadioButton(type == option, { type = option }, enabled = !saving)
                        Text(locationTypeLabel(option))
                    }
                }
                if (showPresets) {
                    item { Text(stringResource(R.string.locations_preset)) }
                    items(LocationPreset.entries) { option ->
                        Row {
                            RadioButton(preset == option, { preset = option }, enabled = !saving)
                            Text(locationPresetLabel(option))
                        }
                    }
                }
                if (error != null) item { Text(stringResource(R.string.locations_error), color = MaterialTheme.colorScheme.error) }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.cancel)) }
            Button(onClick = { onSave(name, type, preset) }, enabled = name.isNotBlank() && !saving) {
                Text(stringResource(if (saving) R.string.saving else R.string.save))
            }
        }
    }
}

@Composable
internal fun EquipmentEditorDialog(
    location: TrainingLocation,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selected by remember(location.id) { mutableStateOf(location.equipmentSlugs) }
    val localizedLabels = EquipmentDefinitions.all.associateWith { equipmentLabel(it) }
    val localizedCategories = EquipmentDefinitions.all.map(EquipmentDefinition::category).distinct()
        .associateWith { equipmentCategoryLabel(it) }
    val matches = EquipmentDefinitions.all.filter { definition ->
        definition.slug != EquipmentDefinitions.NONE &&
            matchesEquipmentQuery(
                definition,
                localizedLabels.getValue(definition),
                localizedCategories.getValue(definition.category),
                query,
            )
    }
    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }) {
        Text(
            stringResource(R.string.locations_equipment_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 600.dp).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
                item {
                    OutlinedTextField(
                        query,
                        { query = it },
                        label = { Text(stringResource(R.string.locations_equipment_search)) },
                        enabled = !saving,
                    )
                }
                item {
                    Text(
                        pluralStringResource(
                            R.plurals.locations_equipment_selected,
                            selected.size,
                            selected.size,
                        ),
                    )
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(onClick = { selected = emptySet() }, enabled = !saving) {
                            Text(stringResource(R.string.locations_equipment_clear))
                        }
                        LocationPreset.entries.filterNot { it == LocationPreset.EMPTY_CUSTOM }.forEach { preset ->
                            OutlinedButton(
                                onClick = { selected = LocationPresets.equipment(preset) },
                                enabled = !saving,
                            ) {
                                Text(locationPresetLabel(preset))
                            }
                        }
                    }
                }
                matches.groupBy(EquipmentDefinition::category).forEach { (category, definitions) ->
                    item { Text(equipmentCategoryLabel(category), style = MaterialTheme.typography.titleSmall) }
                    items(definitions, key = EquipmentDefinition::slug) { definition ->
                        val label = equipmentLabel(definition)
                        Row(
                            Modifier.fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clickable(enabled = !saving) {
                                    selected = if (definition.slug in selected) {
                                        selected - definition.slug
                                    } else {
                                        selected + definition.slug
                                    }
                                }
                                .semantics { contentDescription = label },
                        ) {
                            Checkbox(
                                checked = definition.slug in selected,
                                onCheckedChange = null,
                                enabled = !saving,
                            )
                            Text(label)
                        }
                    }
                }
                if (error != null) item { Text(stringResource(R.string.locations_error), color = MaterialTheme.colorScheme.error) }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.cancel)) }
            Button(onClick = { onSave(selected) }, enabled = !saving) {
                Text(stringResource(if (saving) R.string.saving else R.string.save))
            }
        }
    }
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
    stringResource(equipmentLabelResource(definition))

internal fun matchesEquipmentQuery(
    definition: EquipmentDefinition,
    localizedLabel: String,
    localizedCategory: String,
    query: String,
): Boolean {
    val needle = query.trim()
    return needle.isEmpty() || sequenceOf(
        localizedLabel,
        definition.slug,
        definition.category,
        localizedCategory,
    ).plus(definition.aliases.asSequence()).any { it.contains(needle, ignoreCase = true) }
}

internal fun equipmentLabelResource(definition: EquipmentDefinition): Int =
    equipmentLabelResources[definition.displayNameKey] ?: R.string.equipment_unknown_generic

private val equipmentLabelResources = mapOf(
    "equipment_none" to R.string.equipment_none,
    "equipment_mat" to R.string.equipment_mat,
    "equipment_resistance_bands" to R.string.equipment_resistance_bands,
    "equipment_dumbbells" to R.string.equipment_dumbbells,
    "equipment_adjustable_dumbbells" to R.string.equipment_adjustable_dumbbells,
    "equipment_barbell" to R.string.equipment_barbell,
    "equipment_plates" to R.string.equipment_plates,
    "equipment_bench" to R.string.equipment_bench,
    "equipment_squat_rack" to R.string.equipment_squat_rack,
    "equipment_power_rack" to R.string.equipment_power_rack,
    "equipment_smith_machine" to R.string.equipment_smith_machine,
    "equipment_cable_machine" to R.string.equipment_cable_machine,
    "equipment_pull_up_bar" to R.string.equipment_pull_up_bar,
    "equipment_dip_bars" to R.string.equipment_dip_bars,
    "equipment_kettlebell" to R.string.equipment_kettlebell,
    "equipment_suspension_trainer" to R.string.equipment_suspension_trainer,
    "equipment_chest_press" to R.string.equipment_chest_press,
    "equipment_shoulder_press" to R.string.equipment_shoulder_press,
    "equipment_lat_pulldown" to R.string.equipment_lat_pulldown,
    "equipment_row_machine" to R.string.equipment_row_machine,
    "equipment_leg_press" to R.string.equipment_leg_press,
    "equipment_hack_squat" to R.string.equipment_hack_squat,
    "equipment_leg_extension" to R.string.equipment_leg_extension,
    "equipment_leg_curl" to R.string.equipment_leg_curl,
    "equipment_calf_machine" to R.string.equipment_calf_machine,
    "equipment_adductor_machine" to R.string.equipment_adductor_machine,
    "equipment_abductor_machine" to R.string.equipment_abductor_machine,
    "equipment_treadmill" to R.string.equipment_treadmill,
    "equipment_exercise_bike" to R.string.equipment_exercise_bike,
    "equipment_cross_trainer" to R.string.equipment_cross_trainer,
    "equipment_rowing_ergometer" to R.string.equipment_rowing_ergometer,
    "equipment_stair_climber" to R.string.equipment_stair_climber,
    "equipment_open_floor" to R.string.equipment_open_floor,
)
