package at.fitnessplatform.feature.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import at.fitnessplatform.core.designsystem.MomentumCard
import at.fitnessplatform.core.designsystem.MomentumSectionHeader
import at.fitnessplatform.core.designsystem.MomentumSegmentedControl
import at.fitnessplatform.core.designsystem.MomentumSpacing
import at.fitnessplatform.core.designsystem.MomentumStatusChip
import at.fitnessplatform.core.designsystem.MomentumStatusVariant
import at.fitnessplatform.core.model.CustomExercise
import at.fitnessplatform.core.model.ExerciseConflict
import at.fitnessplatform.core.model.ExerciseConflictResolution
import at.fitnessplatform.core.model.ExerciseConflictType
import at.fitnessplatform.core.model.TrackingType

@Composable
internal fun ExerciseListScreen(
    exercises: List<CustomExercise>,
    conflicts: List<ExerciseConflict>,
    onNew: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onResolve: (String) -> Unit,
    onCatalog: () -> Unit,
    onConflicts: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val visibleExercises = exercises.filter { it.name.contains(query.trim(), ignoreCase = true) }
    Column(Modifier.fillMaxSize().padding(MomentumSpacing.lg)) {
        MomentumSegmentedControl(
            listOf(stringResource(R.string.exercises_mine), stringResource(R.string.exercises_public), stringResource(R.string.exercises_conflicts)),
            0,
            onSelect = { index ->
                when (index) {
                    1 -> onCatalog()
                    2 -> onConflicts()
                }
            },
            modifier = Modifier.testTag("exercises-sections"),
            optionTestTagPrefix = "exercise-section",
        )
        Spacer(Modifier.height(MomentumSpacing.sm))
        Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add)) }
        OutlinedTextField(
            query,
            { query = it },
            label = { Text(stringResource(R.string.exercises_search)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MomentumSpacing.sm)) {
            items(visibleExercises, key = { it.id }) { exercise ->
                val hasConflict = conflicts.any { it.exerciseId == exercise.id }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(MomentumSpacing.lg), verticalArrangement = Arrangement.spacedBy(MomentumSpacing.sm)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(MomentumSpacing.sm)) {
                            Text(exercise.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (hasConflict) MomentumStatusChip(MomentumStatusVariant.CONFLICT, stringResource(R.string.sync_conflict))
                        }
                        Text(
                            stringResource(R.string.exercise_summary, exercise.primaryMuscleGroup, exercise.requiredEquipment),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row {
                            TextButton({ if (hasConflict) onResolve(exercise.id) else onEdit(exercise.id) }) {
                                Text(stringResource(if (hasConflict) R.string.resolve else R.string.edit))
                            }
                            TextButton({ onDelete(exercise.id) }, enabled = !hasConflict) { Text(stringResource(R.string.delete)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ExerciseEditorScreen(
    exercise: CustomExercise?,
    busy: Boolean,
    onSave: (String, String, String, String, TrackingType, String) -> Unit,
) {
    val title = stringResource(if (exercise == null) R.string.exercise_new else R.string.exercise_edit)
    var name by rememberSaveable(exercise) { mutableStateOf(exercise?.name.orEmpty()) }
    var description by rememberSaveable(exercise) { mutableStateOf(exercise?.description.orEmpty()) }
    var muscle by rememberSaveable(exercise) { mutableStateOf(exercise?.primaryMuscleGroup.orEmpty()) }
    var equipment by rememberSaveable(exercise) { mutableStateOf(exercise?.requiredEquipment.orEmpty()) }
    var trackingType by rememberSaveable(exercise) { mutableStateOf(exercise?.trackingType ?: TrackingType.REPS) }
    var notes by rememberSaveable(exercise) { mutableStateOf(exercise?.notes.orEmpty()) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(MomentumSpacing.lg), verticalArrangement = Arrangement.spacedBy(MomentumSpacing.md)) {
        MomentumSectionHeader(title)
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name)) }, enabled = !busy, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(description, { description = it }, label = { Text(stringResource(R.string.description)) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(muscle, { muscle = it }, label = { Text(stringResource(R.string.primary_muscle)) }, enabled = !busy, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(equipment, { equipment = it }, label = { Text(stringResource(R.string.equipment)) }, enabled = !busy, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.notes)) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
        MomentumSectionHeader(stringResource(R.string.tracking_type))
        TrackingTypeSelector(trackingType, busy, { trackingType = it })
        Button(
            onClick = { onSave(name, description, muscle, equipment, trackingType, notes) },
            enabled = !busy && name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.save)) }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun TrackingTypeSelector(
    selected: TrackingType,
    busy: Boolean,
    onSelect: (TrackingType) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MomentumSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(MomentumSpacing.sm),
    ) {
        TrackingType.entries.forEach { type ->
            FilterChip(
                selected = selected == type,
                onClick = { onSelect(type) },
                enabled = !busy,
                label = { Text(trackingTypeLabel(type)) },
                modifier = Modifier.testTag("tracking-type-${type.name}"),
            )
        }
    }
}

@Composable
internal fun ConflictListScreen(conflicts: List<ExerciseConflict>, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(MomentumSpacing.lg), verticalArrangement = Arrangement.spacedBy(MomentumSpacing.md)) {
        MomentumSectionHeader(stringResource(R.string.conflicts_title), stringResource(R.string.conflicts_description))
        conflicts.forEach { conflict ->
            MomentumCard(Modifier.fillMaxWidth()) {
                Text(conflict.localSnapshot.name, style = MaterialTheme.typography.titleMedium)
                Text(conflictTypeLabel(conflict))
                Button({ onOpen(conflict.exerciseId) }) { Text(stringResource(R.string.resolve)) }
            }
        }
    }
}

@Composable
internal fun ConflictResolverScreen(
    conflict: ExerciseConflict,
    busy: Boolean,
    onResolve: (ExerciseConflictResolution, CustomExercise?) -> Unit,
    onBack: () -> Unit,
) {
    var showConfirm by rememberSaveable { mutableStateOf(false) }
    var pendingResolution by rememberSaveable { mutableStateOf<ExerciseConflictResolution?>(null) }
    var pendingMerged by rememberSaveable { mutableStateOf<CustomExercise?>(null) }
    var mergeName by rememberSaveable { mutableStateOf(conflict.localSnapshot.name) }
    var mergeDescription by rememberSaveable { mutableStateOf(conflict.localSnapshot.description) }
    var mergeMuscle by rememberSaveable { mutableStateOf(conflict.localSnapshot.primaryMuscleGroup) }
    var mergeEquipment by rememberSaveable { mutableStateOf(conflict.localSnapshot.requiredEquipment) }
    var mergeNotes by rememberSaveable { mutableStateOf(conflict.localSnapshot.notes) }

    if (showConfirm) {
        ConflictConfirmationDialog(
            resolution = pendingResolution,
            busy = busy,
            onConfirm = {
                val resolved = pendingResolution
                if (resolved != null) onResolve(resolved, pendingMerged)
            },
            onDismiss = {
                if (!busy) {
                    showConfirm = false
                    pendingResolution = null
                    pendingMerged = null
                }
            },
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(MomentumSpacing.lg), verticalArrangement = Arrangement.spacedBy(MomentumSpacing.md)) {
        MomentumSectionHeader(stringResource(R.string.conflict_resolve_title), conflictTypeLabel(conflict))
        MomentumSectionHeader(stringResource(R.string.local_version, conflict.localRevision?.toString() ?: "?"))
        MomentumCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.field_name, conflict.localSnapshot.name))
            Text(stringResource(R.string.field_description, conflict.localSnapshot.description))
            Text(stringResource(R.string.field_notes, conflict.localSnapshot.notes))
        }
        MomentumSectionHeader(stringResource(R.string.server_version, conflict.remoteRevision))
        MomentumCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.field_name, conflict.remoteSnapshot.name))
            Text(stringResource(R.string.field_description, conflict.remoteSnapshot.description))
            Text(stringResource(R.string.field_notes, conflict.remoteSnapshot.notes))
        }
        Button(
            { pendingResolution = ExerciseConflictResolution.KEEP_LOCAL; pendingMerged = null; showConfirm = true },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("conflict-keep-local"),
        ) { Text(stringResource(R.string.keep_local_version)) }
        Button(
            { pendingResolution = ExerciseConflictResolution.TAKE_SERVER; pendingMerged = null; showConfirm = true },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("conflict-take-server"),
        ) { Text(stringResource(R.string.use_server_version)) }
        MomentumSectionHeader(stringResource(R.string.manual_merge))
        OutlinedTextField(
            mergeName,
            { mergeName = it },
            label = { Text(stringResource(R.string.name)) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("conflict-merge-name"),
            singleLine = true,
        )
        OutlinedTextField(mergeDescription, { mergeDescription = it }, label = { Text(stringResource(R.string.description)) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(mergeMuscle, { mergeMuscle = it }, label = { Text(stringResource(R.string.primary_muscle)) }, enabled = !busy, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(mergeEquipment, { mergeEquipment = it }, label = { Text(stringResource(R.string.equipment)) }, enabled = !busy, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(mergeNotes, { mergeNotes = it }, label = { Text(stringResource(R.string.notes)) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
        Button(
            {
                pendingResolution = ExerciseConflictResolution.MERGE
                pendingMerged = conflict.localSnapshot.copy(
                    name = mergeName.trim(),
                    description = mergeDescription.trim(),
                    primaryMuscleGroup = mergeMuscle.trim(),
                    requiredEquipment = mergeEquipment.trim(),
                    notes = mergeNotes.trim(),
                )
                showConfirm = true
            },
            enabled = !busy && mergeName.isNotBlank(),
            modifier = Modifier.fillMaxWidth().testTag("conflict-merge"),
        ) { Text(stringResource(R.string.save_manual_merge)) }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.cancel))
        }
    }
}

@Composable
internal fun ConflictConfirmationDialog(
    resolution: ExerciseConflictResolution?,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.confirm_resolution)) },
        text = {
            Text(
                stringResource(
                    if (resolution == ExerciseConflictResolution.TAKE_SERVER) {
                        R.string.confirm_take_server
                    } else {
                        R.string.confirm_queue_version
                    },
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !busy,
                modifier = Modifier.testTag("conflict-confirm"),
            ) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !busy,
                modifier = Modifier.testTag("conflict-confirm-cancel"),
            ) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
internal fun trackingTypeLabel(type: TrackingType): String = stringResource(
    when (type) {
        TrackingType.REPS_WEIGHT -> R.string.tracking_reps_weight
        TrackingType.REPS -> R.string.tracking_reps
        TrackingType.DURATION -> R.string.tracking_duration
        TrackingType.DISTANCE_DURATION -> R.string.tracking_distance_duration
        TrackingType.MANUAL -> R.string.tracking_manual
    },
)

@Composable
internal fun conflictTypeLabel(conflict: ExerciseConflict): String = stringResource(
    when (conflict.type) {
        ExerciseConflictType.BOTH_MODIFIED -> R.string.conflict_both_modified
        ExerciseConflictType.REVISION_MISMATCH -> R.string.conflict_revision_mismatch
        ExerciseConflictType.REMOTE_DELETED_LOCAL_MODIFIED -> R.string.conflict_remote_deleted
        ExerciseConflictType.LOCAL_DELETED_REMOTE_MODIFIED -> R.string.conflict_local_deleted
    },
)
