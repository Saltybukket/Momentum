@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package at.fitnessplatform.feature.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import at.fitnessplatform.core.designsystem.MomentumTheme
import at.fitnessplatform.core.model.CalendarConflict
import at.fitnessplatform.core.model.CalendarConflictType
import at.fitnessplatform.core.model.ScheduledWorkoutOccurrence
import at.fitnessplatform.core.model.ScheduledWorkoutStatus
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun TrainingCalendarRoute(viewModel: TrainingCalendarViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TrainingCalendarScreen(
        state,
        viewModel::setMode,
        viewModel::selectDate,
        viewModel::createDefaultSchedule,
        viewModel::addAdHoc,
        viewModel::saveOccurrence,
        viewModel::copy,
        viewModel::skip,
        viewModel::cancel,
        viewModel::markUnavailable,
        viewModel::saveAvailability,
        viewModel::clearError,
    )
}

@Composable
@Suppress("LongParameterList")
internal fun TrainingCalendarScreen(
    state: TrainingCalendarUiState,
    onMode: (CalendarDisplayMode) -> Unit,
    onDate: (LocalDate) -> Unit,
    onCreateSchedule: (() -> Unit) -> Unit,
    onAddAdHoc: (String, LocalDate, LocalTime?, Int, () -> Unit) -> Unit,
    onSaveOccurrence: (
        ScheduledWorkoutOccurrence,
        LocalDate,
        LocalTime?,
        Int,
        String?,
        CalendarEditScope,
        () -> Unit,
    ) -> Unit,
    onCopy: (String) -> Unit,
    onSkip: (String) -> Unit,
    onCancel: (String) -> Unit,
    onUnavailable: (LocalDate) -> Unit,
    onSaveAvailability: (
        LocalDate,
        LocalTime?,
        LocalTime?,
        Int?,
        String?,
        Boolean,
        AvailabilityEditScope,
        () -> Unit,
    ) -> Unit,
    onClearError: () -> Unit,
) {
    var editing by remember { mutableStateOf<ScheduledWorkoutOccurrence?>(null) }
    var adding by remember { mutableStateOf(false) }
    var editingAvailability by remember { mutableStateOf(false) }
    MomentumScreen(Modifier.fillMaxSize()) {
        item {
            Text(
                stringResource(R.string.calendar_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.calendar_civil_time_note),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CalendarDisplayMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.mode == mode,
                        onClick = { onMode(mode) },
                        label = { Text(mode.label()) },
                        enabled = !state.saving,
                    )
                }
            }
        }
        item { CalendarDateStrip(state, onDate) }
        if (state.loading) {
            repeat(3) { item { MomentumSkeletonLine(Modifier.fillMaxWidth()) } }
        } else if (state.activePlan == null) {
            item {
                MomentumEmptyState(
                    stringResource(R.string.calendar_no_active_plan),
                    stringResource(R.string.calendar_no_active_plan_message),
                )
            }
        } else if (state.activeSchedule == null) {
            item {
                MomentumEmptyState(
                    stringResource(R.string.calendar_no_schedule),
                    stringResource(R.string.calendar_no_schedule_message),
                    stringResource(R.string.calendar_create_schedule),
                ) { onCreateSchedule {} }
            }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { adding = true }, enabled = !state.saving) {
                        Text(stringResource(R.string.calendar_add_workout))
                    }
                    OutlinedButton(onClick = { onUnavailable(state.selectedDate) }, enabled = !state.saving) {
                        Text(stringResource(R.string.calendar_mark_unavailable))
                    }
                    OutlinedButton(onClick = { editingAvailability = true }, enabled = !state.saving) {
                        Text(stringResource(R.string.calendar_edit_availability))
                    }
                }
            }
            if (state.selectedOccurrences.isEmpty()) {
                item {
                    MomentumEmptyState(
                        stringResource(R.string.calendar_empty_day),
                        stringResource(R.string.calendar_empty_day_message),
                        stringResource(R.string.calendar_add_workout),
                    ) { adding = true }
                }
            } else {
                state.selectedOccurrences.forEach { occurrence ->
                    item {
                        OccurrenceCard(
                            occurrence,
                            state.conflicts.filter { it.occurrenceId == occurrence.id },
                            state.saving,
                            onEdit = { editing = occurrence },
                            onCopy = { onCopy(occurrence.id) },
                            onSkip = { onSkip(occurrence.id) },
                            onCancel = { onCancel(occurrence.id) },
                        )
                    }
                }
            }
        }
    }
    editing?.let { occurrence ->
        OccurrenceEditor(
            occurrence,
            state.saving,
            onDismiss = { editing = null },
            onSave = { date, time, duration, location, scope ->
                onSaveOccurrence(occurrence, date, time, duration, location, scope) { editing = null }
            },
        )
    }
    if (adding) {
        AdHocEditor(
            state.selectedDate,
            state.saving,
            onDismiss = { adding = false },
            onSave = { title, date, time, duration ->
                onAddAdHoc(title, date, time, duration) { adding = false }
            },
        )
    }
    if (editingAvailability) {
        AvailabilityEditor(
            state.selectedDate,
            state.saving,
            onDismiss = { editingAvailability = false },
            onSave = { earliest, latest, maximum, location, unavailable, scope ->
                onSaveAvailability(
                    state.selectedDate,
                    earliest,
                    latest,
                    maximum,
                    location,
                    unavailable,
                    scope,
                ) { editingAvailability = false }
            },
        )
    }
    state.error?.let {
        AlertDialog(
            onDismissRequest = onClearError,
            title = { Text(stringResource(R.string.calendar_error_title)) },
            text = { Text(stringResource(R.string.calendar_error_message)) },
            confirmButton = { TextButton(onClick = onClearError) { Text(stringResource(R.string.plans_ok)) } },
        )
    }
}

@Composable
private fun CalendarDateStrip(state: TrainingCalendarUiState, onDate: (LocalDate) -> Unit) {
    val count = when (state.mode) {
        CalendarDisplayMode.TODAY -> 1
        CalendarDisplayMode.WEEK -> 7
        CalendarDisplayMode.MONTH -> 31
        CalendarDisplayMode.AGENDA -> 14
    }
    val start = when (state.mode) {
        CalendarDisplayMode.TODAY -> state.today
        CalendarDisplayMode.WEEK -> state.selectedDate.minusDays((state.selectedDate.dayOfWeek.value - 1).toLong())
        CalendarDisplayMode.MONTH -> state.selectedDate.withDayOfMonth(1)
        CalendarDisplayMode.AGENDA -> state.selectedDate
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        repeat(count) { offset ->
            val date = start.plusDays(offset.toLong())
            FilterChip(
                selected = date == state.selectedDate,
                onClick = { onDate(date) },
                label = { Text(date.format(DateTimeFormatter.ofPattern("EEE d"))) },
                modifier = Modifier.padding(end = 6.dp),
            )
        }
    }
}

@Composable
private fun OccurrenceCard(
    occurrence: ScheduledWorkoutOccurrence,
    conflicts: List<CalendarConflict>,
    saving: Boolean,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onSkip: () -> Unit,
    onCancel: () -> Unit,
) = MomentumCard(
    Modifier.fillMaxWidth().clickable(enabled = !saving) { onEdit() }.semantics {
        contentDescription = occurrence.titleSnapshot
    },
    emphasized = occurrence.status == ScheduledWorkoutStatus.IN_PROGRESS,
) {
    Text(occurrence.titleSnapshot, style = MaterialTheme.typography.titleLarge)
    Text(
        occurrence.scheduledLocalStartTime?.format(DateTimeFormatter.ofPattern("HH:mm"))
            ?: stringResource(R.string.calendar_time_flexible),
    )
    Text(stringResource(R.string.calendar_duration, occurrence.plannedDurationMinutes))
    Text(occurrence.status.statusLabel())
    conflicts.forEach { Text(it.type.conflictLabel(), color = MaterialTheme.colorScheme.error) }
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        TextButton(onClick = onEdit, enabled = !saving) { Text(stringResource(R.string.calendar_move)) }
        TextButton(onClick = onCopy, enabled = !saving) { Text(stringResource(R.string.calendar_copy)) }
        TextButton(onClick = onSkip, enabled = !saving) { Text(stringResource(R.string.calendar_skip)) }
        TextButton(onClick = onCancel, enabled = !saving) { Text(stringResource(R.string.calendar_cancel_workout)) }
    }
}

@Composable
private fun OccurrenceEditor(
    occurrence: ScheduledWorkoutOccurrence,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (LocalDate, LocalTime?, Int, String?, CalendarEditScope) -> Unit,
) {
    var date by remember { mutableStateOf(occurrence.scheduledLocalDate.toString()) }
    var time by remember { mutableStateOf(occurrence.scheduledLocalStartTime?.toString().orEmpty()) }
    var duration by remember { mutableStateOf(occurrence.plannedDurationMinutes.toString()) }
    var location by remember { mutableStateOf(occurrence.trainingLocationId.orEmpty()) }
    var scope by remember { mutableStateOf(CalendarEditScope.OCCURRENCE_ONLY) }
    val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()
    val parsedTime = time.takeIf(String::isNotBlank)?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
    val parsedDuration = duration.toIntOrNull()?.takeIf { it in 1..1_440 }
    val valid = parsedDate != null && (time.isBlank() || parsedTime != null) && parsedDuration != null
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.calendar_edit_workout)) },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(date, { date = it }, label = { Text(stringResource(R.string.calendar_date)) }, enabled = !saving)
                OutlinedTextField(time, { time = it }, label = { Text(stringResource(R.string.calendar_time)) }, enabled = !saving)
                OutlinedTextField(duration, { duration = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.calendar_duration_label)) }, enabled = !saving)
                OutlinedTextField(location, { location = it }, label = { Text(stringResource(R.string.calendar_location_optional)) }, enabled = !saving)
                CalendarEditScope.entries.forEach { option ->
                    FilterChip(
                        selected = scope == option,
                        onClick = { scope = option },
                        label = { Text(option.label()) },
                        enabled = !saving && (option == CalendarEditScope.OCCURRENCE_ONLY || occurrence.planDayId != null),
                    )
                }
                Text(stringResource(R.string.calendar_future_confirmation), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        requireNotNull(parsedDate),
                        parsedTime,
                        requireNotNull(parsedDuration),
                        location.takeIf(String::isNotBlank),
                        scope,
                    )
                },
                enabled = !saving && valid,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun AdHocEditor(
    initialDate: LocalDate,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, LocalDate, LocalTime?, Int) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(initialDate.toString()) }
    var time by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("60") }
    val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()
    val parsedTime = time.takeIf(String::isNotBlank)?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
    val parsedDuration = duration.toIntOrNull()?.takeIf { it in 1..1_440 }
    val valid = parsedDate != null && (time.isBlank() || parsedTime != null) && parsedDuration != null
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.calendar_add_workout)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.workout_title)) }, enabled = !saving)
                OutlinedTextField(date, { date = it }, label = { Text(stringResource(R.string.calendar_date)) }, enabled = !saving)
                OutlinedTextField(time, { time = it }, label = { Text(stringResource(R.string.calendar_time)) }, enabled = !saving)
                OutlinedTextField(duration, { duration = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.calendar_duration_label)) }, enabled = !saving)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(title, requireNotNull(parsedDate), parsedTime, requireNotNull(parsedDuration))
                },
                enabled = !saving && title.isNotBlank() && valid,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun AvailabilityEditor(
    date: LocalDate,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (LocalTime?, LocalTime?, Int?, String?, Boolean, AvailabilityEditScope) -> Unit,
) {
    var earliest by remember { mutableStateOf("") }
    var latest by remember { mutableStateOf("") }
    var maximum by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var unavailable by remember { mutableStateOf(false) }
    var scope by remember { mutableStateOf(AvailabilityEditScope.WEEKDAY) }
    val start = earliest.takeIf(String::isNotBlank)?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
    val end = latest.takeIf(String::isNotBlank)?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
    val duration = maximum.takeIf(String::isNotBlank)?.toIntOrNull()
    val valid = availabilityInputIsValid(
        earliest = earliest,
        latest = latest,
        maximum = maximum,
        start = start,
        end = end,
        duration = duration,
        unavailable = unavailable,
        scope = scope,
    )
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.calendar_edit_availability)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                OutlinedTextField(earliest, { earliest = it }, label = { Text(stringResource(R.string.calendar_earliest)) }, enabled = !saving)
                OutlinedTextField(latest, { latest = it }, label = { Text(stringResource(R.string.calendar_latest)) }, enabled = !saving)
                OutlinedTextField(maximum, { maximum = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.calendar_max_duration)) }, enabled = !saving)
                OutlinedTextField(location, { location = it }, label = { Text(stringResource(R.string.calendar_location_optional)) }, enabled = !saving)
                FilterChip(
                    selected = unavailable,
                    onClick = { unavailable = !unavailable },
                    label = { Text(stringResource(R.string.calendar_unavailable)) },
                    enabled = !saving,
                )
                AvailabilityEditScope.entries.forEach { option ->
                    FilterChip(
                        selected = scope == option,
                        onClick = { scope = option },
                        label = { Text(option.label()) },
                        enabled = !saving,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(start, end, duration, location.takeIf(String::isNotBlank), unavailable, scope) },
                enabled = !saving && valid,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun availabilityInputIsValid(
    earliest: String,
    latest: String,
    maximum: String,
    start: LocalTime?,
    end: LocalTime?,
    duration: Int?,
    unavailable: Boolean,
    scope: AvailabilityEditScope,
): Boolean =
    (earliest.isBlank() || start != null) &&
        (latest.isBlank() || end != null) &&
        (start == null || end == null || start < end) &&
        (maximum.isBlank() || (duration != null && duration in 1..1_440)) &&
        (!unavailable || scope == AvailabilityEditScope.SELECTED_DATE)

@Composable
private fun CalendarDisplayMode.label() = stringResource(
    when (this) {
        CalendarDisplayMode.TODAY -> R.string.calendar_today
        CalendarDisplayMode.WEEK -> R.string.calendar_week
        CalendarDisplayMode.MONTH -> R.string.calendar_month
        CalendarDisplayMode.AGENDA -> R.string.calendar_agenda
    },
)

@Composable
private fun CalendarEditScope.label() = stringResource(
    when (this) {
        CalendarEditScope.OCCURRENCE_ONLY -> R.string.calendar_only_this
        CalendarEditScope.THIS_AND_FUTURE -> R.string.calendar_this_and_future
    },
)

@Composable
private fun AvailabilityEditScope.label() = stringResource(
    when (this) {
        AvailabilityEditScope.WEEKDAY -> R.string.calendar_every_weekday
        AvailabilityEditScope.SELECTED_DATE -> R.string.calendar_only_selected_date
    },
)

@Composable
private fun ScheduledWorkoutStatus.statusLabel() = stringResource(
    when (this) {
        ScheduledWorkoutStatus.PLANNED -> R.string.workout_planned
        ScheduledWorkoutStatus.IN_PROGRESS -> R.string.workout_in_progress
        ScheduledWorkoutStatus.COMPLETED -> R.string.workout_completed
        ScheduledWorkoutStatus.SKIPPED -> R.string.calendar_skipped
        ScheduledWorkoutStatus.MOVED -> R.string.calendar_moved
        ScheduledWorkoutStatus.CANCELLED -> R.string.workout_cancelled
        ScheduledWorkoutStatus.CONFLICT -> R.string.calendar_conflict
    },
)

@Composable
private fun CalendarConflictType.conflictLabel() = stringResource(
    when (this) {
        CalendarConflictType.OUTSIDE_AVAILABILITY -> R.string.calendar_outside_availability
        CalendarConflictType.DURATION_EXCEEDED -> R.string.calendar_duration_exceeded
        CalendarConflictType.LOCATION_REQUIRED -> R.string.calendar_location_required
        CalendarConflictType.MISSING_EQUIPMENT -> R.string.calendar_missing_equipment
        CalendarConflictType.OVERLAP -> R.string.calendar_overlap
        CalendarConflictType.UNAVAILABLE_EXERCISE -> R.string.calendar_unavailable_exercise
    },
)

@Preview(showBackground = true, fontScale = 2f)
@Composable
@Suppress("UnusedPrivateMember")
private fun CalendarConflictPreview() = MomentumTheme {
    TrainingCalendarScreen(
        state = TrainingCalendarUiState(
            loading = false,
            today = LocalDate.of(2026, 7, 13),
            occurrences = listOf(
                ScheduledWorkoutOccurrence(
                    "occurrence",
                    "owner",
                    titleSnapshot = "Full body strength with a deliberately long title",
                    scheduledLocalDate = LocalDate.of(2026, 7, 13),
                    scheduledLocalStartTime = LocalTime.of(18, 0),
                    timeZoneId = "Europe/Berlin",
                    plannedDurationMinutes = 75,
                    createdAtEpochMs = 0,
                    updatedAtEpochMs = 0,
                ),
            ),
            conflicts = listOf(CalendarConflict("occurrence", CalendarConflictType.OVERLAP, "calendar_overlap")),
        ),
        onMode = {},
        onDate = {},
        onCreateSchedule = {},
        onAddAdHoc = { _, _, _, _, _ -> },
        onSaveOccurrence = { _, _, _, _, _, _, _ -> },
        onCopy = {},
        onSkip = {},
        onCancel = {},
        onUnavailable = {},
        onSaveAvailability = { _, _, _, _, _, _, _, _ -> },
        onClearError = {},
    )
}
