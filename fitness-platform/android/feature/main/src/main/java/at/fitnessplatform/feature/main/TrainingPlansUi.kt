@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package at.fitnessplatform.feature.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.fitnessplatform.core.designsystem.MomentumCard
import at.fitnessplatform.core.designsystem.MomentumEmptyState
import at.fitnessplatform.core.designsystem.MomentumScreen
import at.fitnessplatform.core.designsystem.MomentumSkeletonLine
import at.fitnessplatform.core.designsystem.MomentumSpacing
import at.fitnessplatform.core.designsystem.MomentumTheme
import at.fitnessplatform.core.model.ExerciseReference
import at.fitnessplatform.core.model.ExerciseReferenceKind
import at.fitnessplatform.core.model.ExerciseResolutionStatus
import at.fitnessplatform.core.model.ExerciseSnapshot
import at.fitnessplatform.core.model.EquipmentDefinitions
import at.fitnessplatform.core.model.PlanBlock
import at.fitnessplatform.core.model.PlanBlockType
import at.fitnessplatform.core.model.PlanDay
import at.fitnessplatform.core.model.PlanExercise
import at.fitnessplatform.core.model.PlanSetType
import at.fitnessplatform.core.model.PlanWeek
import at.fitnessplatform.core.model.SetPrescription
import at.fitnessplatform.core.model.TempoPrescription
import at.fitnessplatform.core.model.TrackingType
import at.fitnessplatform.core.model.TrainingPlan
import at.fitnessplatform.core.model.TrainingPlanGoal
import at.fitnessplatform.domain.PlanStructureKind
import at.fitnessplatform.domain.PlanScheduleActivationDecision

@Composable
fun TrainingPlansRoute(
    ownerProfileId: String,
    viewModel: TrainingPlansViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TrainingPlansScreen(
        state = state,
        ownerProfileId = ownerProfileId,
        onSelect = viewModel::select,
        onCreate = viewModel::create,
        onRename = viewModel::rename,
        onAddExercise = viewModel::addExercise,
        onAddWeek = viewModel::addWeek,
        onAddDay = viewModel::addDay,
        onAddBlock = viewModel::addBlock,
        onRemoveStructure = viewModel::removeStructure,
        onMoveStructure = viewModel::moveStructure,
        onRemoveExercise = viewModel::removeExercise,
        onSaveSet = viewModel::saveSet,
        onAddSet = viewModel::addSet,
        onDeleteSet = viewModel::deleteSet,
        onMoveSet = viewModel::moveSet,
        onMove = viewModel::move,
        onCopy = viewModel::copy,
        onAdaptCopy = viewModel::adaptCopy,
        onActivate = viewModel::activate,
        onResolveActivation = viewModel::resolveActivation,
        onArchive = viewModel::archive,
        onDelete = viewModel::delete,
        onClearError = viewModel::clearError,
    )
}

@Composable
@Suppress("LongParameterList")
internal fun TrainingPlansScreen(
    state: TrainingPlansUiState,
    ownerProfileId: String,
    onSelect: (String?) -> Unit,
    onCreate: (String, String, String, TrainingPlanGoal, () -> Unit) -> Unit,
    onRename: (TrainingPlan, String, String, TrainingPlanGoal, () -> Unit) -> Unit,
    onAddExercise: (TrainingPlan, String, PlanExerciseChoice, () -> Unit) -> Unit,
    onAddWeek: (TrainingPlan) -> Unit,
    onAddDay: (TrainingPlan, String) -> Unit,
    onAddBlock: (TrainingPlan, String) -> Unit,
    onRemoveStructure: (TrainingPlan, PlanStructureKind, String) -> Unit,
    onMoveStructure: (TrainingPlan, PlanStructureKind, String, Int) -> Unit,
    onRemoveExercise: (TrainingPlan, String) -> Unit,
    onSaveSet: (TrainingPlan, String, SetPrescription, () -> Unit) -> Unit,
    onAddSet: (TrainingPlan, String) -> Unit,
    onDeleteSet: (TrainingPlan, String, String) -> Unit,
    onMoveSet: (TrainingPlan, String, String, Int) -> Unit,
    onMove: (TrainingPlan, String, Int) -> Unit,
    onCopy: (String) -> Unit,
    onAdaptCopy: (String) -> Unit,
    onActivate: (String) -> Unit,
    onResolveActivation: (PlanScheduleActivationDecision) -> Unit,
    onArchive: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onClearError: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var pickingBlockId by remember { mutableStateOf<String?>(null) }
    var editingSet by remember { mutableStateOf<Pair<PlanExercise, SetPrescription>?>(null) }
    var adapting by remember { mutableStateOf<TrainingPlan?>(null) }
    val selected = state.selectedPlan
    if (selected == null) {
        PlanList(
            state,
            onSelect,
            onCreate = { creating = true },
            onCopy = onCopy,
            onActivate = onActivate,
        )
    } else {
        PlanDetail(
            selected,
            state,
            onBack = { onSelect(null) },
            onEdit = { editing = true },
            onPick = { pickingBlockId = it },
            onEditSet = { exercise, prescription -> editingSet = exercise to prescription },
            onAddWeek = onAddWeek,
            onAddDay = onAddDay,
            onAddBlock = onAddBlock,
            onRemoveStructure = onRemoveStructure,
            onMoveStructure = onMoveStructure,
            onRemoveExercise = onRemoveExercise,
            onAddSet = onAddSet,
            onDeleteSet = onDeleteSet,
            onMoveSet = onMoveSet,
            onMove = onMove,
            onCopy = onCopy,
            onAdaptCopy = { adapting = selected },
            onActivate = onActivate,
            onArchive = onArchive,
            onDelete = onDelete,
        )
    }
    if (creating) PlanEditorSheet(
        title = stringResource(R.string.plans_create),
        initialName = "",
        initialDescription = "",
        initialGoal = TrainingPlanGoal.GENERAL_FITNESS,
        saving = state.saving,
        onDismiss = { creating = false },
        onSave = { name, description, goal ->
            onCreate(ownerProfileId, name, description, goal) { creating = false }
        },
    )
    if (editing && selected != null) PlanEditorSheet(
        title = stringResource(R.string.plans_edit),
        initialName = selected.name,
        initialDescription = selected.description,
        initialGoal = selected.goal,
        saving = state.saving,
        onDismiss = { editing = false },
        onSave = { name, description, goal ->
            onRename(selected, name, description, goal) { editing = false }
        },
    )
    pickingBlockId?.let { blockId -> if (selected != null) ExercisePickerSheet(
        choices = state.choices,
        saving = state.saving,
        onDismiss = { pickingBlockId = null },
        onPick = { choice -> onAddExercise(selected, blockId, choice) { pickingBlockId = null } },
    ) }
    editingSet?.let { (exercise, prescription) ->
        if (selected != null) SetEditorDialog(
            prescription = prescription,
            saving = state.saving,
            onDismiss = { editingSet = null },
            onSave = { prescription ->
                onSaveSet(selected, exercise.id, prescription) { editingSet = null }
            },
        )
    }
    adapting?.let { plan ->
        AlertDialog(
            onDismissRequest = { adapting = null },
            title = { Text(stringResource(R.string.plans_adapt_copy)) },
            text = { Text(stringResource(R.string.plans_adapt_confirm)) },
            confirmButton = {
                TextButton(onClick = { adapting = null; onAdaptCopy(plan.id) }) {
                    Text(stringResource(R.string.plans_adapt_copy))
                }
            },
            dismissButton = {
                TextButton(onClick = { adapting = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    state.pendingActivationPlanId?.let {
        AlertDialog(
            onDismissRequest = { onResolveActivation(PlanScheduleActivationDecision.CANCEL) },
            title = { Text(stringResource(R.string.plans_schedule_decision_title)) },
            text = { Text(stringResource(R.string.plans_schedule_decision_message)) },
            confirmButton = {
                TextButton(
                    enabled = !state.saving,
                    onClick = {
                        onResolveActivation(PlanScheduleActivationDecision.START_NEW_SCHEDULE_SETUP)
                    },
                ) {
                    Text(stringResource(R.string.plans_schedule_start_new))
                }
            },
            dismissButton = {
                Column {
                    TextButton(
                        enabled = !state.saving,
                        onClick = {
                            onResolveActivation(PlanScheduleActivationDecision.KEEP_CURRENT_SCHEDULE)
                        },
                    ) {
                        Text(stringResource(R.string.plans_schedule_keep_current))
                    }
                    TextButton(
                        enabled = !state.saving,
                        onClick = { onResolveActivation(PlanScheduleActivationDecision.CANCEL) },
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            },
        )
    }
    state.error?.let {
        AlertDialog(
            onDismissRequest = onClearError,
            title = { Text(stringResource(R.string.plans_error_title)) },
            text = { Text(stringResource(R.string.plans_error_message)) },
            confirmButton = { TextButton(onClick = onClearError) { Text(stringResource(R.string.plans_ok)) } },
        )
    }
}

@Composable
private fun PlanList(
    state: TrainingPlansUiState,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit,
    onCopy: (String) -> Unit,
    onActivate: (String) -> Unit,
) = MomentumScreen(Modifier.fillMaxSize()) {
    item {
        Text(
            stringResource(R.string.plans_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.plans_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onCreate, enabled = !state.saving) { Text(stringResource(R.string.plans_create)) }
    }
    if (state.loading) {
        repeat(3) { item { MomentumSkeletonLine(Modifier.fillMaxWidth()) } }
    } else if (state.plans.isEmpty()) {
        item {
            MomentumEmptyState(
                stringResource(R.string.plans_empty_title),
                stringResource(R.string.plans_empty_message),
                stringResource(R.string.plans_create),
                onCreate,
            )
        }
    } else {
        items(state.plans, key = TrainingPlan::id) { plan ->
            MomentumCard(
                Modifier.fillMaxWidth().clickable(enabled = !state.saving) { onSelect(plan.id) },
                emphasized = plan.isActive,
            ) {
                Text(plan.name, style = MaterialTheme.typography.titleLarge)
                Text(plan.goal.displayName())
                Text(
                    stringResource(
                        when {
                            plan.isActive -> R.string.plans_active
                            plan.isArchived -> R.string.plans_archived
                            else -> R.string.plans_available
                        },
                    ),
                )
                Column(verticalArrangement = Arrangement.spacedBy(MomentumSpacing.xs)) {
                    if (!plan.isActive && !plan.isArchived) {
                        TextButton(onClick = { onActivate(plan.id) }, enabled = !state.saving) {
                            Text(stringResource(R.string.plans_activate))
                        }
                    }
                    TextButton(onClick = { onCopy(plan.id) }, enabled = !state.saving) {
                        Text(stringResource(R.string.plans_copy))
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun PlanDetail(
    plan: TrainingPlan,
    state: TrainingPlansUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onPick: (String) -> Unit,
    onEditSet: (PlanExercise, SetPrescription) -> Unit,
    onAddWeek: (TrainingPlan) -> Unit,
    onAddDay: (TrainingPlan, String) -> Unit,
    onAddBlock: (TrainingPlan, String) -> Unit,
    onRemoveStructure: (TrainingPlan, PlanStructureKind, String) -> Unit,
    onMoveStructure: (TrainingPlan, PlanStructureKind, String, Int) -> Unit,
    onRemoveExercise: (TrainingPlan, String) -> Unit,
    onAddSet: (TrainingPlan, String) -> Unit,
    onDeleteSet: (TrainingPlan, String, String) -> Unit,
    onMoveSet: (TrainingPlan, String, String, Int) -> Unit,
    onMove: (TrainingPlan, String, Int) -> Unit,
    onCopy: (String) -> Unit,
    onAdaptCopy: (String) -> Unit,
    onActivate: (String) -> Unit,
    onArchive: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
) {
    var actions by remember { mutableStateOf(false) }
    MomentumScreen(Modifier.fillMaxSize()) {
        item {
            TextButton(onClick = onBack) { Text(stringResource(R.string.navigate_up)) }
            Text(plan.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Text(plan.description.ifBlank { stringResource(R.string.plans_no_description) })
            Text(plan.goal.displayName())
            state.activeLocation?.let { Text(stringResource(R.string.plans_location, it.name)) }
            Row(horizontalArrangement = Arrangement.spacedBy(MomentumSpacing.sm)) {
                OutlinedButton(onClick = onEdit, enabled = !state.saving) { Text(stringResource(R.string.edit)) }
                TextButton(onClick = { actions = true }, enabled = !state.saving) {
                    Text(stringResource(R.string.more_actions))
                }
                DropdownMenu(expanded = actions, onDismissRequest = { actions = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.plans_copy)) },
                        onClick = { actions = false; onCopy(plan.id) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.plans_adapt_copy)) },
                        onClick = { actions = false; onAdaptCopy(plan.id) },
                    )
                    if (!plan.isActive && !plan.isArchived) DropdownMenuItem(
                        text = { Text(stringResource(R.string.plans_activate)) },
                        onClick = { actions = false; onActivate(plan.id) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(if (plan.isArchived) R.string.plans_restore else R.string.plans_archive)) },
                        onClick = { actions = false; onArchive(plan.id, !plan.isArchived) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        onClick = { actions = false; onDelete(plan.id) },
                    )
                }
            }
            if (!plan.isArchived) {
                OutlinedButton(onClick = { onAddWeek(plan) }, enabled = !state.saving) {
                    Text(stringResource(R.string.plans_add_week))
                }
            }
        }
        plan.weeks.sortedBy { it.position }.forEach { week ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(MomentumSpacing.xs)) {
                    Text(week.title, style = MaterialTheme.typography.titleLarge)
                    if (!plan.isArchived) TextButton(
                        onClick = { onAddDay(plan, week.id) },
                        enabled = !state.saving,
                    ) { Text(stringResource(R.string.plans_add_day)) }
                    StructureActions(plan, PlanStructureKind.WEEK, week.id, state.saving, onMoveStructure, onRemoveStructure)
                }
            }
            week.days.sortedBy { it.position }.forEach { day ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(MomentumSpacing.xs)) {
                        Text(day.title, style = MaterialTheme.typography.titleMedium)
                        if (!plan.isArchived) TextButton(
                            onClick = { onAddBlock(plan, day.id) },
                            enabled = !state.saving,
                        ) { Text(stringResource(R.string.plans_add_block)) }
                        StructureActions(plan, PlanStructureKind.DAY, day.id, state.saving, onMoveStructure, onRemoveStructure)
                    }
                }
                day.blocks.sortedBy { it.position }.forEach { block ->
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(MomentumSpacing.xs)) {
                            Text(block.title, style = MaterialTheme.typography.labelLarge)
                            if (!plan.isArchived) TextButton(
                                onClick = { onPick(block.id) },
                                enabled = !state.saving,
                            ) { Text(stringResource(R.string.plans_add_exercise)) }
                            StructureActions(
                                plan,
                                PlanStructureKind.BLOCK,
                                block.id,
                                state.saving,
                                onMoveStructure,
                                onRemoveStructure,
                            )
                        }
                    }
                    if (block.exercises.isEmpty()) {
                        item { Text(stringResource(R.string.plans_day_empty)) }
                    } else {
                        items(block.exercises.sortedBy { it.position }, key = PlanExercise::id) { exercise ->
                            PlanExerciseCard(
                                plan,
                                exercise,
                                state,
                                onEditSet,
                                onMove,
                                onRemoveExercise,
                                onAddSet,
                                onDeleteSet,
                                onMoveSet,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StructureActions(
    plan: TrainingPlan,
    kind: PlanStructureKind,
    id: String,
    saving: Boolean,
    onMove: (TrainingPlan, PlanStructureKind, String, Int) -> Unit,
    onRemove: (TrainingPlan, PlanStructureKind, String) -> Unit,
) {
    if (plan.isArchived) return
    TextButton(onClick = { onMove(plan, kind, id, -1) }, enabled = !saving) {
        Text(stringResource(R.string.plans_move_up))
    }
    TextButton(onClick = { onMove(plan, kind, id, 1) }, enabled = !saving) {
        Text(stringResource(R.string.plans_move_down))
    }
    TextButton(onClick = { onRemove(plan, kind, id) }, enabled = !saving) {
        Text(stringResource(R.string.delete))
    }
}

@Composable
@Suppress("CyclomaticComplexMethod")
private fun PlanExerciseCard(
    plan: TrainingPlan,
    exercise: PlanExercise,
    state: TrainingPlansUiState,
    onEditSet: (PlanExercise, SetPrescription) -> Unit,
    onMove: (TrainingPlan, String, Int) -> Unit,
    onRemoveExercise: (TrainingPlan, String) -> Unit,
    onAddSet: (TrainingPlan, String) -> Unit,
    onDeleteSet: (TrainingPlan, String, String) -> Unit,
    onMoveSet: (TrainingPlan, String, String, Int) -> Unit,
) = Card(Modifier.fillMaxWidth().semantics { contentDescription = exercise.reference.snapshot.name }) {
    val equipmentLabels = exercise.reference.snapshot.equipment.sorted()
        .map { it.localizedEquipmentLabel() }
        .joinToString()
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(exercise.reference.snapshot.name, style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(
                if (exercise.reference.kind == ExerciseReferenceKind.CATALOG) {
                    R.string.plans_public_catalog
                } else {
                    R.string.plans_private_exercise
                },
            ),
        )
        Text(
            stringResource(
                R.string.plans_equipment,
                equipmentLabels,
            ),
        )
        Text(exercise.reference.resolutionStatus.localizedLabel())
        exercise.sets.sortedBy { it.position }.forEachIndexed { index, set ->
            val restSeconds = set.restSeconds ?: 0
            Text(
                pluralStringResource(
                    R.plurals.plans_set_summary,
                    restSeconds,
                    set.repsMin?.toString() ?: set.durationSeconds?.let { "${it}s" } ?: "—",
                    restSeconds,
                ),
            )
            Row {
                TextButton(
                    onClick = { onMoveSet(plan, exercise.id, set.id, -1) },
                    enabled = !state.saving && !plan.isArchived && index > 0,
                ) { Text(stringResource(R.string.plans_move_up)) }
                TextButton(
                    onClick = { onMoveSet(plan, exercise.id, set.id, 1) },
                    enabled = !state.saving && !plan.isArchived && index < exercise.sets.lastIndex,
                ) { Text(stringResource(R.string.plans_move_down)) }
                TextButton(
                    onClick = { onEditSet(exercise, set) },
                    enabled = !state.saving && !plan.isArchived,
                ) { Text(stringResource(R.string.plans_edit_set_number, index + 1)) }
                TextButton(
                    onClick = { onDeleteSet(plan, exercise.id, set.id) },
                    enabled = !state.saving && !plan.isArchived,
                ) { Text(stringResource(R.string.delete)) }
            }
        }
        Row {
            TextButton(onClick = { onMove(plan, exercise.id, -1) }, enabled = !state.saving && !plan.isArchived) {
                Text(stringResource(R.string.plans_move_up))
            }
            TextButton(onClick = { onMove(plan, exercise.id, 1) }, enabled = !state.saving && !plan.isArchived) {
                Text(stringResource(R.string.plans_move_down))
            }
            TextButton(onClick = { onAddSet(plan, exercise.id) }, enabled = !state.saving && !plan.isArchived) {
                Text(stringResource(R.string.plans_add_set))
            }
            TextButton(
                onClick = { onRemoveExercise(plan, exercise.id) },
                enabled = !state.saving && !plan.isArchived,
            ) {
                Text(stringResource(R.string.delete))
            }
        }
    }
}

@Composable
private fun PlanEditorSheet(
    title: String,
    initialName: String,
    initialDescription: String,
    initialGoal: TrainingPlanGoal,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, TrainingPlanGoal) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var description by remember(initialDescription) { mutableStateOf(initialDescription) }
    var goal by remember(initialGoal) { mutableStateOf(initialGoal) }
    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name)) }, enabled = !saving)
            OutlinedTextField(
                description,
                { description = it },
                label = { Text(stringResource(R.string.description)) },
                enabled = !saving,
            )
            TrainingPlanGoal.entries.forEach { option ->
                FilterChip(
                    selected = goal == option,
                    onClick = { goal = option },
                    label = { Text(option.displayName()) },
                    enabled = !saving,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.cancel)) }
                Button(onClick = { onSave(name, description, goal) }, enabled = name.isNotBlank() && !saving) {
                    Text(stringResource(if (saving) R.string.saving else R.string.save))
                }
            }
        }
    }
}

@Composable
private fun ExercisePickerSheet(
    choices: List<PlanExerciseChoice>,
    saving: Boolean,
    onDismiss: () -> Unit,
    onPick: (PlanExerciseChoice) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val visible = choices.filter { it.name.contains(query, ignoreCase = true) }
    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Text(stringResource(R.string.plans_pick_exercise), style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                query,
                { query = it },
                label = { Text(stringResource(R.string.plans_search)) },
                enabled = !saving,
            )
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp).padding(horizontal = 24.dp)) {
            items(visible, key = PlanExerciseChoice::key) { choice ->
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .clickable(enabled = !saving) { onPick(choice) },
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(choice.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(
                                if (choice.reference.kind == ExerciseReferenceKind.CATALOG) {
                                    R.string.plans_public_catalog
                                } else {
                                    R.string.plans_private_exercise
                                },
                            ),
                        )
                        val equipment = choice.equipment.sorted().map { it.localizedEquipmentLabel() }.joinToString()
                        Text(stringResource(R.string.plans_equipment, equipment))
                        if (!choice.compatible) {
                            Text(stringResource(R.string.plans_incompatible), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetEditorDialog(
    prescription: SetPrescription,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (SetPrescription) -> Unit,
) {
    var setType by remember { mutableStateOf(prescription.setType) }
    var repsMin by remember { mutableStateOf(prescription.repsMin?.toString().orEmpty()) }
    var repsMax by remember { mutableStateOf(prescription.repsMax?.toString().orEmpty()) }
    var duration by remember { mutableStateOf(prescription.durationSeconds?.toString().orEmpty()) }
    var distance by remember { mutableStateOf(prescription.distanceMeters?.toString().orEmpty()) }
    var weight by remember { mutableStateOf(prescription.targetWeightKg?.toString().orEmpty()) }
    var rpe by remember { mutableStateOf(prescription.targetRpe?.toString().orEmpty()) }
    var rir by remember { mutableStateOf(prescription.targetRir?.toString().orEmpty()) }
    var rest by remember { mutableStateOf(prescription.restSeconds?.toString().orEmpty()) }
    var eccentric by remember { mutableStateOf(prescription.tempo?.eccentric.orEmpty()) }
    var bottomPause by remember { mutableStateOf(prescription.tempo?.bottomPause.orEmpty()) }
    var concentric by remember { mutableStateOf(prescription.tempo?.concentric.orEmpty()) }
    var topPause by remember { mutableStateOf(prescription.tempo?.topPause.orEmpty()) }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.plans_edit_set)) },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlanSetType.entries.forEach { option ->
                    FilterChip(
                        selected = option == setType,
                        onClick = { setType = option },
                        label = { Text(option.localizedLabel()) },
                        enabled = !saving,
                    )
                }
                PlanNumberField(repsMin, { repsMin = it }, R.string.plans_reps_min, false)
                PlanNumberField(repsMax, { repsMax = it }, R.string.plans_reps_max, false)
                PlanNumberField(duration, { duration = it }, R.string.plans_duration_seconds, false)
                PlanNumberField(distance, { distance = it }, R.string.plans_distance_meters, true)
                PlanNumberField(weight, { weight = it }, R.string.plans_weight_kg, true)
                PlanNumberField(rpe, { rpe = it }, R.string.plans_rpe, true)
                PlanNumberField(rir, { rir = it }, R.string.plans_rir, false)
                PlanNumberField(rest, { rest = it }, R.string.plans_rest, false)
                Text(stringResource(R.string.plans_tempo), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TempoField(eccentric, { eccentric = it }, R.string.plans_tempo_eccentric, Modifier.widthIn(64.dp, 84.dp))
                    TempoField(bottomPause, { bottomPause = it }, R.string.plans_tempo_bottom, Modifier.widthIn(64.dp, 84.dp))
                    TempoField(concentric, { concentric = it }, R.string.plans_tempo_concentric, Modifier.widthIn(64.dp, 84.dp))
                    TempoField(topPause, { topPause = it }, R.string.plans_tempo_top, Modifier.widthIn(64.dp, 84.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val tempoValues = listOf(eccentric, bottomPause, concentric, topPause)
                    onSave(
                        prescription.copy(
                            setType = setType,
                            repsMin = repsMin.toIntOrNull(),
                            repsMax = repsMax.toIntOrNull(),
                            durationSeconds = duration.toIntOrNull(),
                            distanceMeters = distance.toDoubleOrNull(),
                            targetWeightKg = weight.toDoubleOrNull(),
                            targetRpe = rpe.toDoubleOrNull(),
                            targetRir = rir.toIntOrNull(),
                            restSeconds = rest.toIntOrNull(),
                            tempo = tempoValues.takeIf { values -> values.any(String::isNotBlank) }
                                ?.let { TempoPrescription(it[0], it[1], it[2], it[3]) },
                        ),
                    )
                },
                enabled = !saving,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun String.localizedEquipmentLabel(): String {
    val definition = EquipmentDefinitions.all.firstOrNull { it.slug == this }
    return if (definition == null) stringResource(R.string.equipment_unknown_generic)
    else stringResource(equipmentLabelResource(definition))
}

@Composable
private fun ExerciseResolutionStatus.localizedLabel() = stringResource(
    when (this) {
        ExerciseResolutionStatus.RESOLVED -> R.string.plans_resolution_resolved
        ExerciseResolutionStatus.UNAVAILABLE -> R.string.plans_resolution_unavailable
        ExerciseResolutionStatus.DEPRECATED -> R.string.plans_resolution_deprecated
        ExerciseResolutionStatus.DELETED_CUSTOM -> R.string.plans_resolution_deleted_custom
    },
)

@Composable
private fun PlanSetType.localizedLabel() = stringResource(
    when (this) {
        PlanSetType.WARMUP -> R.string.plans_set_type_warmup
        PlanSetType.WORK -> R.string.plans_set_type_work
        PlanSetType.DROP -> R.string.plans_set_type_drop
        PlanSetType.AMRAP -> R.string.plans_set_type_amrap
        PlanSetType.TIME -> R.string.plans_set_type_time
        PlanSetType.DISTANCE -> R.string.plans_set_type_distance
    },
)

@Composable
private fun PlanNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: Int,
    decimal: Boolean,
) = OutlinedTextField(
    value,
    { candidate -> onValueChange(candidate.filter { it.isDigit() || (decimal && it == '.') }) },
    label = { Text(stringResource(label)) },
    singleLine = true,
)

@Composable
private fun TempoField(
    value: String,
    onValueChange: (String) -> Unit,
    label: Int,
    modifier: Modifier,
) = OutlinedTextField(
    value,
    { candidate -> onValueChange(candidate.take(1).filter { it.isDigit() || it == 'X' || it == 'x' }) },
    label = { Text(stringResource(label)) },
    singleLine = true,
    modifier = modifier,
)

@Composable
private fun TrainingPlanGoal.displayName() = stringResource(
    when (this) {
        TrainingPlanGoal.GENERAL_FITNESS -> R.string.plan_goal_general
        TrainingPlanGoal.STRENGTH -> R.string.plan_goal_strength
        TrainingPlanGoal.MUSCLE_BUILDING -> R.string.plan_goal_muscle
        TrainingPlanGoal.MOBILITY -> R.string.plan_goal_mobility
        TrainingPlanGoal.RUNNING -> R.string.plan_goal_running
        TrainingPlanGoal.CUSTOM -> R.string.plan_goal_custom
    },
)

@Preview(showBackground = true)
@Composable
@Suppress("UnusedPrivateMember")
private fun TrainingPlansPreview() = MomentumTheme {
    TrainingPlansScreen(
        state = TrainingPlansUiState(loading = false, plans = listOf(previewPlan())),
        ownerProfileId = "profile",
        onSelect = {},
        onCreate = { _, _, _, _, success -> success() },
        onRename = { _, _, _, _, success -> success() },
        onAddExercise = { _, _, _, success -> success() },
        onAddWeek = {},
        onAddDay = { _, _ -> },
        onAddBlock = { _, _ -> },
        onRemoveStructure = { _, _, _ -> },
        onMoveStructure = { _, _, _, _ -> },
        onRemoveExercise = { _, _ -> },
        onSaveSet = { _, _, _, success -> success() },
        onAddSet = { _, _ -> },
        onDeleteSet = { _, _, _ -> },
        onMoveSet = { _, _, _, _ -> },
        onMove = { _, _, _ -> },
        onCopy = {},
        onAdaptCopy = {},
        onActivate = {},
        onResolveActivation = {},
        onArchive = { _, _ -> },
        onDelete = {},
        onClearError = {},
    )
}

private fun previewPlan() = TrainingPlan(
    "preview-plan",
    "profile",
    "Balanced strength",
    "Three calm sessions each week.",
    TrainingPlanGoal.STRENGTH,
    true,
    createdAtEpochMs = 1,
    updatedAtEpochMs = 1,
    weeks = listOf(
        PlanWeek(
            "week",
            0,
            "Week 1",
            listOf(
                PlanDay(
                    "day",
                    0,
                    "Day 1",
                    listOf(
                        PlanBlock(
                            "block",
                            0,
                            PlanBlockType.MAIN,
                            "Main",
                            listOf(
                                PlanExercise(
                                    "exercise",
                                    0,
                                    ExerciseReference(
                                        ExerciseReferenceKind.CATALOG,
                                        catalogSource = "self-authored",
                                        catalogExternalId = "squat",
                                        snapshot = ExerciseSnapshot("Squat", TrackingType.REPS, setOf("none")),
                                    ),
                                    sets = listOf(SetPrescription("set", 0, repsMin = 8, restSeconds = 90)),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    ),
)
