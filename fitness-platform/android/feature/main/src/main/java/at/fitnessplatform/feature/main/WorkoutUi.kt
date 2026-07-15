package at.fitnessplatform.feature.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import at.fitnessplatform.core.designsystem.MomentumCard
import at.fitnessplatform.core.designsystem.MomentumEmptyState
import at.fitnessplatform.core.designsystem.MomentumListCard
import at.fitnessplatform.core.designsystem.MomentumSectionHeader
import at.fitnessplatform.core.designsystem.MomentumSpacing
import at.fitnessplatform.core.designsystem.MomentumStatusChip
import at.fitnessplatform.core.designsystem.MomentumStatusVariant
import at.fitnessplatform.core.designsystem.MomentumSegmentedControl
import at.fitnessplatform.core.model.CustomExercise
import at.fitnessplatform.core.model.Workout
import at.fitnessplatform.core.model.WorkoutExercise
import at.fitnessplatform.core.model.WorkoutStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

sealed interface WorkoutDetailUiState {
    data object Loading : WorkoutDetailUiState
    data object NotFound : WorkoutDetailUiState
    data class Content(
        val workout: Workout,
        val exercises: List<ResolvedWorkoutExercise>,
        val repeatAllowed: Boolean,
    ) : WorkoutDetailUiState
}

data class ResolvedWorkoutExercise(
    val link: WorkoutExercise,
    val exercise: CustomExercise?,
)

internal fun resolveWorkoutExercises(
    workout: Workout,
    available: List<CustomExercise>,
): List<ResolvedWorkoutExercise> {
    val map = available.associateBy { it.id }
    return workout.exercises.sortedBy { it.position }.map { we ->
        ResolvedWorkoutExercise(we, map[we.exerciseId])
    }
}

internal fun workoutDetailState(
    workoutId: String,
    state: PlatformUiState,
): WorkoutDetailUiState = when {
    state.isLoading -> WorkoutDetailUiState.Loading
    else -> {
        val workout = state.workouts.firstOrNull { it.id == workoutId }
        if (workout == null) WorkoutDetailUiState.NotFound
        else {
            val exercises = resolveWorkoutExercises(workout, state.exercises)
            val repeatAllowed = workout.status == WorkoutStatus.COMPLETED &&
                exercises.none { it.exercise == null }
            WorkoutDetailUiState.Content(workout, exercises, repeatAllowed)
        }
    }
}

internal fun workoutDetailRoute(workoutId: String): String {
    require(workoutId.isNotBlank() && '/' !in workoutId) {
        "Workout ID is not route-safe."
    }
    return "workout-detail/$workoutId"
}

internal fun formatWorkoutDateTime(epochMs: Long, locale: Locale, zone: ZoneId): String {
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale)
    return formatter.format(Instant.ofEpochMilli(epochMs).atZone(zone))
}

internal fun newWorkoutExerciseIds(): List<String> = emptyList()

internal fun workoutStatusVariant(status: WorkoutStatus): MomentumStatusVariant = when (status) {
    WorkoutStatus.PLANNED -> MomentumStatusVariant.PLANNED
    WorkoutStatus.IN_PROGRESS -> MomentumStatusVariant.ACTIVE
    WorkoutStatus.PAUSED -> MomentumStatusVariant.PAUSED
    WorkoutStatus.COMPLETED -> MomentumStatusVariant.COMPLETED
    WorkoutStatus.CANCELLED -> MomentumStatusVariant.CANCELLED
}

@Composable
internal fun workoutStatusLabel(status: WorkoutStatus): String = stringResource(
    when (status) {
        WorkoutStatus.PLANNED -> R.string.workout_planned
        WorkoutStatus.IN_PROGRESS -> R.string.workout_in_progress
        WorkoutStatus.PAUSED -> R.string.workout_paused
        WorkoutStatus.COMPLETED -> R.string.workout_completed
        WorkoutStatus.CANCELLED -> R.string.workout_cancelled
    },
)

@Composable
internal fun WorkoutScreen(
    state: PlatformUiState,
    onCreate: (String, List<String>) -> Unit,
    onStart: (String) -> Unit,
    onComplete: (String) -> Unit,
    onOpen: (String) -> Unit,
    onPlans: () -> Unit,
    onCalendar: () -> Unit,
) {
    val defaultTitle = stringResource(R.string.workout_default_title)
    var title by rememberSaveable { mutableStateOf(defaultTitle) }
    val active = state.workouts.filter { it.status == WorkoutStatus.IN_PROGRESS || it.status == WorkoutStatus.PAUSED }
    val planned = state.workouts.filter { it.status == WorkoutStatus.PLANNED }
    val history = state.workouts.filter { it.status == WorkoutStatus.COMPLETED || it.status == WorkoutStatus.CANCELLED }
    var section by rememberSaveable { mutableIntStateOf(0) }
    val busy = state.operationInProgress

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(MomentumSpacing.lg)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MomentumSpacing.sm)) {
            FilterChip(
                selected = false,
                onClick = onCalendar,
                label = { Text(stringResource(R.string.calendar_title)) },
                modifier = Modifier.testTag("workouts-open-calendar"),
            )
            FilterChip(
                selected = false,
                onClick = onPlans,
                label = { Text(stringResource(R.string.plans_title)) },
                modifier = Modifier.testTag("workouts-open-plans"),
            )
        }
        Spacer(Modifier.height(MomentumSpacing.sm))
        OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.workout_title)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(
            onClick = { if (!busy) onCreate(title, newWorkoutExerciseIds()) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.workout_create)) }
        Text(stringResource(R.string.workout_empty_creation_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(MomentumSpacing.md))
        MomentumSegmentedControl(
            listOf(stringResource(R.string.calendar_today), stringResource(R.string.workouts_history)),
            section,
            onSelect = { section = it },
            optionTestTagPrefix = "workout-section",
        )
        Spacer(Modifier.height(MomentumSpacing.md))
        if (section == 0) {
            WorkoutSection(R.string.workouts_active, active, busy, onStart, onComplete, onOpen)
            WorkoutSection(R.string.workouts_planned, planned, busy, onStart, onComplete, onOpen)
        } else {
            WorkoutSection(R.string.workouts_history, history, busy, onStart, onComplete, onOpen)
        }
    }
}

@Composable
private fun WorkoutSection(
    title: Int,
    workouts: List<Workout>,
    busy: Boolean,
    onStart: (String) -> Unit,
    onComplete: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    if (workouts.isEmpty()) return
    Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = MomentumSpacing.md, bottom = MomentumSpacing.sm))
    workouts.forEach { workout ->
        MomentumListCard(Modifier.fillMaxWidth(), onClick = { onOpen(workout.id) }) {
            Text(workout.title, style = MaterialTheme.typography.titleMedium)
            MomentumStatusChip(workoutStatusVariant(workout.status), workoutStatusLabel(workout.status))
            when (workout.status) {
                WorkoutStatus.PLANNED -> Button({ if (!busy) onStart(workout.id) }, Modifier, enabled = !busy) { Text(stringResource(R.string.start)) }
                WorkoutStatus.IN_PROGRESS -> Button({ if (!busy) onComplete(workout.id) }, Modifier, enabled = !busy) { Text(stringResource(R.string.complete)) }
                else -> Unit
            }
        }
    }
}

@Composable
@Suppress("CyclomaticComplexMethod")
internal fun WorkoutDetailScreen(
    workoutId: String,
    state: PlatformUiState,
    onStart: (String) -> Unit,
    onComplete: (String) -> Unit,
    onRepeat: (String) -> Unit,
) {
    val detailState = workoutDetailState(workoutId, state)
    val busy = state.operationInProgress
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val zone = java.time.ZoneId.systemDefault()

    when (detailState) {
        WorkoutDetailUiState.Loading -> {
            MomentumEmptyState(stringResource(R.string.workout_detail_loading), "")
        }
        WorkoutDetailUiState.NotFound -> {
            MomentumEmptyState(
                stringResource(R.string.workout_detail_not_found_title),
                stringResource(R.string.workout_detail_not_found_body),
            )
        }
        is WorkoutDetailUiState.Content -> {
            val workout = detailState.workout
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(MomentumSpacing.lg), verticalArrangement = Arrangement.spacedBy(MomentumSpacing.md)) {
                MomentumSectionHeader(workout.title, workoutStatusLabel(workout.status))
                val startMs = workout.startTimeEpochMs
                if (startMs != null) {
                    MomentumCard(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.workout_started, formatWorkoutDateTime(startMs, locale, zone)))
                    }
                }
                val endMs = workout.endTimeEpochMs
                if (endMs != null) {
                    MomentumCard(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.workout_ended, formatWorkoutDateTime(endMs, locale, zone)))
                    }
                }
                if (workout.notes.isNotBlank()) {
                    MomentumCard(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.workout_notes_label), style = MaterialTheme.typography.titleMedium)
                        Text(workout.notes, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (detailState.exercises.isNotEmpty()) {
                    MomentumSectionHeader(stringResource(R.string.workout_exercises_label))
                    detailState.exercises.forEach { resolved ->
                        val missingDescription = stringResource(R.string.workout_exercise_missing_description)
                        MomentumCard(
                            Modifier.fillMaxWidth().then(
                                if (resolved.exercise == null) {
                                    Modifier.semantics { contentDescription = missingDescription }
                                } else {
                                    Modifier
                                },
                            ),
                        ) {
                            if (resolved.exercise != null) {
                                Text(resolved.exercise.name, style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.exercise_summary, resolved.exercise.primaryMuscleGroup, resolved.exercise.requiredEquipment), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Text(stringResource(R.string.workout_exercise_missing), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                when (workout.status) {
                    WorkoutStatus.PLANNED -> Button({ if (!busy) onStart(workout.id) }, Modifier.fillMaxWidth(), enabled = !busy) { Text(stringResource(R.string.start)) }
                    WorkoutStatus.IN_PROGRESS -> Button({ if (!busy) onComplete(workout.id) }, Modifier.fillMaxWidth(), enabled = !busy) { Text(stringResource(R.string.complete)) }
                    WorkoutStatus.COMPLETED -> Button(
                        { if (!busy) onRepeat(workout.id) },
                        Modifier.fillMaxWidth(),
                        enabled = detailState.repeatAllowed && !busy,
                    ) {
                        Text(if (detailState.repeatAllowed) stringResource(R.string.workout_repeat) else stringResource(R.string.workout_repeat_unavailable_missing))
                    }
                    else -> Unit
                }
            }
        }
    }
}
