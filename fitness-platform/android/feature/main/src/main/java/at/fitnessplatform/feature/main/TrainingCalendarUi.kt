@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package at.fitnessplatform.feature.main

import android.content.res.Configuration
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
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
import at.fitnessplatform.core.designsystem.MomentumTheme
import at.fitnessplatform.core.model.CalendarConflict
import at.fitnessplatform.core.model.CalendarConflictType
import at.fitnessplatform.core.model.AvailabilityRule
import at.fitnessplatform.core.model.ScheduleOverride
import at.fitnessplatform.core.model.ScheduledWorkoutOccurrence
import at.fitnessplatform.core.model.ScheduledWorkoutStatus
import at.fitnessplatform.core.model.TrainingLocation
import at.fitnessplatform.core.model.LocationType
import at.fitnessplatform.core.model.PlanSchedule
import at.fitnessplatform.core.model.TrainingPlan
import at.fitnessplatform.domain.CalendarAction
import at.fitnessplatform.domain.ScheduleRuleDraft
import at.fitnessplatform.domain.allowedCalendarActions
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TrainingCalendarRoute(viewModel: TrainingCalendarViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TrainingCalendarScreen(
        state,
        viewModel::setMode,
        viewModel::selectDate,
        viewModel::navigatePeriod,
        viewModel::previewSchedule,
        viewModel::confirmScheduleSetup,
        viewModel::cancelScheduleSetup,
        viewModel::addAdHoc,
        viewModel::saveOccurrence,
        viewModel::copy,
        viewModel::skip,
        viewModel::cancel,
        viewModel::markUnavailable,
        viewModel::saveAvailability,
        viewModel::resetAvailability,
        viewModel::confirmScheduleChange,
        viewModel::cancelScheduleChange,
        viewModel::clearError,
    )
}

@Composable
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
internal fun TrainingCalendarScreen(
    state: TrainingCalendarUiState,
    onMode: (CalendarDisplayMode) -> Unit,
    onDate: (LocalDate) -> Unit,
    onNavigate: (Long) -> Unit,
    onPreviewSchedule: (LocalDate, String, List<ScheduleRuleDraft>, () -> Unit) -> Unit,
    onConfirmScheduleSetup: (() -> Unit) -> Unit,
    onCancelScheduleSetup: () -> Unit,
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
    onResetAvailability: (LocalDate, AvailabilityEditScope, () -> Unit) -> Unit,
    onConfirmScheduleChange: (() -> Unit) -> Unit,
    onCancelScheduleChange: () -> Unit,
    onClearError: () -> Unit,
) {
    var editing by remember { mutableStateOf<ScheduledWorkoutOccurrence?>(null) }
    var adding by remember { mutableStateOf(false) }
    var editingAvailability by remember { mutableStateOf(false) }
    var settingUpSchedule by remember { mutableStateOf(false) }
    MomentumScreen(Modifier.fillMaxSize().testTag("screen-calendar")) {
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
        item { CalendarPeriodNavigation(state, onDate, onNavigate) }
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
                ) { settingUpSchedule = true }
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
            item { AvailabilitySummary(state) }
            if (state.mode == CalendarDisplayMode.AGENDA && state.occurrences.isNotEmpty()) {
                var lastDate: LocalDate? = null
                state.occurrences.forEach { occurrence ->
                    if (lastDate != occurrence.scheduledLocalDate) {
                        item {
                            Text(
                                occurrence.scheduledLocalDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.semantics { heading() },
                            )
                        }
                        lastDate = occurrence.scheduledLocalDate
                    }
                    item {
                        OccurrenceCard(
                            occurrence,
                            state.conflicts.filter { it.occurrenceId == occurrence.id },
                            state.saving,
                            locationName = state.locations.firstOrNull {
                                it.id == occurrence.trainingLocationId
                            }?.name,
                            onEdit = { editing = occurrence },
                            onCopy = { onCopy(occurrence.id) },
                            onSkip = { onSkip(occurrence.id) },
                            onCancel = { onCancel(occurrence.id) },
                        )
                    }
                }
            } else if (state.selectedOccurrences.isEmpty()) {
                item {
                    MomentumEmptyState(
                        stringResource(R.string.calendar_empty_day),
                        stringResource(R.string.calendar_empty_day_message),
                        stringResource(R.string.calendar_add_workout),
                    ) { adding = true }
                }
            } else {
                state.selectedOccurrences.forEachIndexed { index, occurrence ->
                    item {
                        OccurrenceCard(
                            occurrence,
                            state.conflicts.filter { it.occurrenceId == occurrence.id },
                            state.saving,
                            locationName = state.locations.firstOrNull {
                                it.id == occurrence.trainingLocationId
                            }?.name,
                            emphasized = state.mode == CalendarDisplayMode.TODAY && index == 0,
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
            state.locations,
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
            state.availability.firstOrNull { it.dayOfWeek == state.selectedDate.dayOfWeek },
            state.overrides.firstOrNull { it.localDate == state.selectedDate },
            state.locations,
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
            onReset = { scope ->
                onResetAvailability(state.selectedDate, scope) { editingAvailability = false }
            },
        )
    }
    if (settingUpSchedule) {
        ScheduleSetupDialog(
            plan = requireNotNull(state.activePlan),
            locations = state.locations,
            availability = state.availability,
            today = state.today,
            saving = state.saving,
            onDismiss = { settingUpSchedule = false },
            onConfirm = { startDate, timeZoneId, drafts ->
                onPreviewSchedule(startDate, timeZoneId, drafts) { settingUpSchedule = false }
            },
        )
    }
    state.pendingScheduleSetup?.let { preview ->
        AlertDialog(
            onDismissRequest = { if (!state.saving) onCancelScheduleSetup() },
            title = { Text(stringResource(R.string.calendar_schedule_preview_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        pluralStringResource(
                            R.plurals.calendar_preview_occurrences,
                            preview.occurrences.size,
                            preview.occurrences.size,
                        ),
                    )
                    Text(
                        pluralStringResource(
                            R.plurals.calendar_preview_conflicts,
                            preview.conflicts.size,
                            preview.conflicts.size,
                        ),
                        color = if (preview.conflicts.isEmpty()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    preview.conflicts.groupingBy { it.type }.eachCount().forEach { (type, count) ->
                        Text(
                            stringResource(R.string.calendar_conflict_count, type.conflictLabel(), count),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onConfirmScheduleSetup {} }, enabled = !state.saving) {
                    Text(stringResource(R.string.calendar_materialize_schedule))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelScheduleSetup, enabled = !state.saving) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
    state.pendingScheduleChange?.let { preview ->
        AlertDialog(
            onDismissRequest = { if (!state.saving) onCancelScheduleChange() },
            title = { Text(stringResource(R.string.calendar_confirm_future_change)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.calendar_future_occurrences,
                        preview.affectedOccurrenceIds.size,
                        preview.affectedOccurrenceIds.size,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { onConfirmScheduleChange {} }, enabled = !state.saving) {
                    Text(stringResource(R.string.calendar_apply_change))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelScheduleChange, enabled = !state.saving) {
                    Text(stringResource(R.string.cancel))
                }
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
private fun CalendarPeriodNavigation(
    state: TrainingCalendarUiState,
    onDate: (LocalDate) -> Unit,
    onNavigate: (Long) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = { onNavigate(-1) }) { Text(stringResource(R.string.calendar_previous)) }
        Text(
            when (state.mode) {
                CalendarDisplayMode.TODAY -> state.selectedDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))
                CalendarDisplayMode.WEEK -> stringResource(R.string.calendar_week_of, state.selectedDate.toString())
                CalendarDisplayMode.MONTH -> YearMonth.from(state.selectedDate)
                    .format(DateTimeFormatter.ofPattern("MMMM yyyy"))
                CalendarDisplayMode.AGENDA -> stringResource(R.string.calendar_next_fifty_six_days)
            },
            style = MaterialTheme.typography.titleMedium,
        )
        TextButton(onClick = { onNavigate(1) }) { Text(stringResource(R.string.calendar_next)) }
    }
    when (state.mode) {
        CalendarDisplayMode.TODAY -> Unit
        CalendarDisplayMode.WEEK -> {
            val start = state.selectedDate.minusDays((state.selectedDate.dayOfWeek.value - 1).toLong())
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                repeat(7) { offset ->
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
        CalendarDisplayMode.MONTH -> MonthGrid(state, onDate)
        CalendarDisplayMode.AGENDA -> Unit
    }
}

@Composable
private fun MonthGrid(state: TrainingCalendarUiState, onDate: (LocalDate) -> Unit) {
    val cells = calendarMonthCells(YearMonth.from(state.selectedDate))
    Column(Modifier.fillMaxWidth()) {
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    val count = date?.let { day -> state.occurrences.count { it.scheduledLocalDate == day } } ?: 0
                    val label = date?.let { day ->
                        if (count == 0) day.dayOfMonth.toString() else "${day.dayOfMonth} •$count"
                    }.orEmpty()
                    TextButton(
                        onClick = { date?.let(onDate) },
                        enabled = date != null,
                        modifier = Modifier.padding(horizontal = 2.dp),
                    ) {
                        Text(
                            label,
                            color = if (date == state.selectedDate) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
        }
    }
}

internal fun calendarMonthCells(month: YearMonth): List<LocalDate?> {
    val leading = month.atDay(1).dayOfWeek.value - 1
    val days = (1..month.lengthOfMonth()).map(month::atDay)
    val cells = List<LocalDate?>(leading) { null } + days
    val trailing = (7 - cells.size % 7) % 7
    return cells + List<LocalDate?>(trailing) { null }
}

@Composable
private fun OccurrenceCard(
    occurrence: ScheduledWorkoutOccurrence,
    conflicts: List<CalendarConflict>,
    saving: Boolean,
    locationName: String?,
    emphasized: Boolean = false,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onSkip: () -> Unit,
    onCancel: () -> Unit,
) = MomentumCard(
    Modifier.fillMaxWidth().clickable(
        enabled = !saving && CalendarAction.EDIT in allowedCalendarActions(occurrence.status),
    ) { onEdit() }.semantics {
        contentDescription = occurrence.titleSnapshot
    },
    emphasized = emphasized || occurrence.status == ScheduledWorkoutStatus.IN_PROGRESS,
) {
    if (emphasized) {
        Text(stringResource(R.string.calendar_next_workout), style = MaterialTheme.typography.labelLarge)
    }
    Text(occurrence.titleSnapshot, style = MaterialTheme.typography.titleLarge)
    Text(
        occurrence.scheduledLocalStartTime?.format(DateTimeFormatter.ofPattern("HH:mm"))
            ?: stringResource(R.string.calendar_time_flexible),
    )
    Text(
        pluralStringResource(
            R.plurals.calendar_duration_minutes,
            occurrence.plannedDurationMinutes,
            occurrence.plannedDurationMinutes,
        ),
    )
    Text(
        when {
            occurrence.trainingLocationId == null -> stringResource(R.string.calendar_no_location)
            locationName != null -> locationName
            else -> stringResource(R.string.calendar_location_unavailable)
        },
    )
    Text(occurrence.status.statusLabel())
    conflicts.forEach { Text(it.type.conflictLabel(), color = MaterialTheme.colorScheme.error) }
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        val actions = allowedCalendarActions(occurrence.status)
        if (CalendarAction.MOVE in actions) {
            TextButton(onClick = onEdit, enabled = !saving) { Text(stringResource(R.string.calendar_move)) }
        }
        if (CalendarAction.COPY in actions) {
            TextButton(onClick = onCopy, enabled = !saving) { Text(stringResource(R.string.calendar_copy)) }
        }
        if (CalendarAction.SKIP in actions) {
            TextButton(onClick = onSkip, enabled = !saving) { Text(stringResource(R.string.calendar_skip)) }
        }
        if (CalendarAction.CANCEL in actions) {
            TextButton(onClick = onCancel, enabled = !saving) {
                Text(stringResource(R.string.calendar_cancel_workout))
            }
        }
    }
}

@Composable
private fun OccurrenceEditor(
    occurrence: ScheduledWorkoutOccurrence,
    locations: List<TrainingLocation>,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (LocalDate, LocalTime?, Int, String?, CalendarEditScope) -> Unit,
) {
    var date by remember { mutableStateOf(occurrence.scheduledLocalDate.toString()) }
    var time by remember { mutableStateOf(occurrence.scheduledLocalStartTime?.toString().orEmpty()) }
    var duration by remember { mutableStateOf(occurrence.plannedDurationMinutes.toString()) }
    var locationId by remember { mutableStateOf(occurrence.trainingLocationId) }
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
                LocationSelector(locationId, locations, !saving) { locationId = it }
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
                        locationId,
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
@Suppress("CyclomaticComplexMethod")
private fun AvailabilityEditor(
    date: LocalDate,
    recurring: AvailabilityRule?,
    override: ScheduleOverride?,
    locations: List<TrainingLocation>,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (LocalTime?, LocalTime?, Int?, String?, Boolean, AvailabilityEditScope) -> Unit,
    onReset: (AvailabilityEditScope) -> Unit,
) {
    val initialEarliest = override?.earliestLocalTime ?: recurring?.earliestLocalTime
    val initialLatest = override?.latestLocalTime ?: recurring?.latestLocalTime
    val initialMaximum = override?.maxDurationMinutes ?: recurring?.maxDurationMinutes
    var earliest by remember(date, initialEarliest) { mutableStateOf(initialEarliest?.toString().orEmpty()) }
    var latest by remember(date, initialLatest) { mutableStateOf(initialLatest?.toString().orEmpty()) }
    var maximum by remember(date, initialMaximum) { mutableStateOf(initialMaximum?.toString().orEmpty()) }
    var locationId by remember(date, recurring?.id, override?.id) {
        mutableStateOf(override?.locationId ?: recurring?.preferredLocationId)
    }
    var unavailable by remember(date, override) { mutableStateOf(override?.unavailable == true) }
    var scope by remember(date, override) {
        mutableStateOf(
            if (override != null) AvailabilityEditScope.SELECTED_DATE else AvailabilityEditScope.WEEKDAY,
        )
    }
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
                LocationSelector(locationId, locations, !saving) { locationId = it }
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
                onClick = { onSave(start, end, duration, locationId, unavailable, scope) },
                enabled = !saving && valid,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (hasAvailabilityValueToReset(scope, recurring, override)) {
                    TextButton(onClick = { onReset(scope) }, enabled = !saving) {
                        Text(stringResource(R.string.calendar_reset))
                    }
                }
                TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

private fun hasAvailabilityValueToReset(
    scope: AvailabilityEditScope,
    recurring: AvailabilityRule?,
    override: ScheduleOverride?,
): Boolean = when (scope) {
    AvailabilityEditScope.WEEKDAY -> recurring != null
    AvailabilityEditScope.SELECTED_DATE -> override != null
}

@Composable
private fun LocationSelector(
    selectedId: String?,
    locations: List<TrainingLocation>,
    enabled: Boolean,
    onSelect: (String?) -> Unit,
) {
    Text(stringResource(R.string.calendar_location_optional), style = MaterialTheme.typography.labelLarge)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        FilterChip(
            selected = selectedId == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.calendar_no_location)) },
            enabled = enabled,
            modifier = Modifier.padding(end = 6.dp),
        )
        locations.forEach { location ->
            FilterChip(
                selected = selectedId == location.id,
                onClick = { onSelect(location.id) },
                label = {
                    Text(
                        stringResource(
                            R.string.calendar_location_name_and_type,
                            location.name,
                            locationTypeLabel(location.type),
                        ),
                    )
                },
                enabled = enabled,
                modifier = Modifier.padding(end = 6.dp),
            )
        }
        if (selectedId != null && locations.none { it.id == selectedId }) {
            FilterChip(
                selected = true,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.calendar_location_unavailable)) },
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun locationTypeLabel(type: LocationType): String = stringResource(
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
private fun AvailabilitySummary(state: TrainingCalendarUiState) {
    if (state.availability.isEmpty() && state.overrides.isEmpty()) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(R.string.calendar_availability_overview), style = MaterialTheme.typography.titleSmall)
            state.availability.sortedBy { it.dayOfWeek.value }.forEach { rule ->
                Text(
                    stringResource(
                        R.string.calendar_availability_rule,
                        rule.dayOfWeek.localizedLabel(),
                        rule.earliestLocalTime?.toString() ?: "–",
                        rule.latestLocalTime?.toString() ?: "–",
                    ),
                )
            }
            state.overrides.sortedBy(ScheduleOverride::localDate).forEach { override ->
                Text(
                    stringResource(
                        R.string.calendar_availability_override,
                        override.localDate.toString(),
                        if (override.unavailable) {
                            stringResource(R.string.calendar_selected_date_unavailable)
                        } else {
                            stringResource(R.string.calendar_selected_date_override)
                        },
                    ),
                )
            }
        }
    }
}

private data class ScheduleRuleInput(
    val planDayId: String,
    val title: String,
    val dayOfWeek: DayOfWeek,
    val time: String,
    val duration: String,
    val locationId: String?,
)

@Composable
@Suppress("CyclomaticComplexMethod")
internal fun ScheduleSetupDialog(
    plan: TrainingPlan,
    locations: List<TrainingLocation>,
    availability: List<AvailabilityRule>,
    today: LocalDate,
    saving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, String, List<ScheduleRuleDraft>) -> Unit,
) {
    var startDate by remember(plan.id) { mutableStateOf(today.toString()) }
    var timeZoneId by remember(plan.id) { mutableStateOf(ZoneId.systemDefault().id) }
    val activeLocationId = locations.firstOrNull { it.isActive }?.id
    var rules by remember(plan.id) {
        mutableStateOf(
            plan.weeks.sortedBy { it.weekIndex }.flatMap { it.days.sortedBy { day -> day.position } }
                .map { day ->
                    ScheduleRuleInput(
                        planDayId = day.id,
                        title = day.title,
                        dayOfWeek = today.plusDays(day.relativeDayIndex.toLong()).dayOfWeek,
                        time = "",
                        duration = (day.estimatedDurationMinutes ?: 60).toString(),
                        locationId = activeLocationId,
                    )
                },
        )
    }
    val parsedDate = runCatching { LocalDate.parse(startDate) }.getOrNull()
    val validZone = runCatching { ZoneId.of(timeZoneId) }.isSuccess
    val drafts = rules.mapNotNull { row ->
        val parsedTime = row.time.takeIf(String::isNotBlank)?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        val duration = row.duration.toIntOrNull()?.takeIf { it in 1..1_440 }
        if ((row.time.isNotBlank() && parsedTime == null) || duration == null) null else {
            ScheduleRuleDraft(row.planDayId, row.dayOfWeek, parsedTime, duration, row.locationId)
        }
    }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.calendar_schedule_setup)) },
        text = {
            Column(
                Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(plan.name, style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    startDate,
                    { startDate = it },
                    label = { Text(stringResource(R.string.calendar_start_date)) },
                    enabled = !saving,
                )
                OutlinedTextField(
                    timeZoneId,
                    { timeZoneId = it },
                    label = { Text(stringResource(R.string.calendar_time_zone)) },
                    enabled = !saving,
                )
                rules.forEachIndexed { index, row ->
                    Text(row.title, style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        DayOfWeek.entries.forEach { dayOfWeek ->
                            FilterChip(
                                selected = row.dayOfWeek == dayOfWeek,
                                onClick = {
                                    rules = rules.toMutableList().also {
                                        it[index] = row.copy(dayOfWeek = dayOfWeek)
                                    }
                                },
                                label = { Text(dayOfWeek.localizedLabel()) },
                                enabled = !saving,
                                modifier = Modifier.padding(end = 4.dp),
                            )
                        }
                    }
                    OutlinedTextField(
                        row.time,
                        { value -> rules = rules.toMutableList().also { it[index] = row.copy(time = value) } },
                        label = { Text(stringResource(R.string.calendar_time_optional)) },
                        enabled = !saving,
                    )
                    OutlinedTextField(
                        row.duration,
                        { value ->
                            rules = rules.toMutableList().also {
                                it[index] = row.copy(duration = value.filter(Char::isDigit))
                            }
                        },
                        label = { Text(stringResource(R.string.calendar_duration_label)) },
                        enabled = !saving,
                    )
                    LocationSelector(row.locationId, locations, !saving) { locationId ->
                        rules = rules.toMutableList().also { it[index] = row.copy(locationId = locationId) }
                    }
                }
                Text(
                    pluralStringResource(R.plurals.calendar_setup_sessions, rules.size, rules.size),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    stringResource(R.string.calendar_availability_overview),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (availability.isEmpty()) {
                    Text(stringResource(R.string.calendar_no_availability_rules))
                } else {
                    availability.sortedBy { it.dayOfWeek.value }.forEach { rule ->
                        Text(
                            stringResource(
                                R.string.calendar_availability_rule,
                                rule.dayOfWeek.localizedLabel(),
                                rule.earliestLocalTime?.toString() ?: "–",
                                rule.latestLocalTime?.toString() ?: "–",
                            ),
                        )
                    }
                }
                Text(stringResource(R.string.calendar_setup_review_note), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(requireNotNull(parsedDate), timeZoneId, drafts) },
                enabled = !saving && parsedDate != null && validZone && drafts.size == rules.size,
            ) { Text(stringResource(R.string.calendar_confirm_schedule)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun DayOfWeek.localizedLabel(): String {
    val locale = LocalConfiguration.current.locales[0]
    return getDisplayName(java.time.format.TextStyle.SHORT, locale)
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
        CalendarConflictType.INVALID_LOCAL_TIME -> R.string.calendar_invalid_local_time
    },
)

@Preview(name = "Compact light", showBackground = true, widthDp = 320)
@Preview(name = "Expanded dark", showBackground = true, widthDp = 840, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Two hundred percent text", showBackground = true, fontScale = 2f)
@Composable
@Suppress("UnusedPrivateMember")
private fun CalendarConflictPreview() = MomentumTheme {
    TrainingCalendarScreen(
        state = TrainingCalendarUiState(
            loading = false,
            today = LocalDate.of(2026, 7, 13),
            mode = CalendarDisplayMode.TODAY,
            activePlan = TrainingPlan(
                id = "plan",
                ownerProfileId = "owner",
                name = "Strength",
                createdAtEpochMs = 0,
                updatedAtEpochMs = 0,
            ),
            activeSchedule = PlanSchedule(
                id = "schedule",
                ownerProfileId = "owner",
                planId = "plan",
                startDate = LocalDate.of(2026, 7, 13),
                timeZoneId = "Europe/Berlin",
                isActive = true,
                createdAtEpochMs = 0,
                updatedAtEpochMs = 0,
            ),
            locations = listOf(
                TrainingLocation("gym", "Riverside Gym", LocationType.FITNESS_CENTER, emptySet(), true, 0, 0),
            ),
            occurrences = listOf(
                ScheduledWorkoutOccurrence(
                    "occurrence",
                    "owner",
                    titleSnapshot = "Full body strength with a deliberately long title",
                    scheduledLocalDate = LocalDate.of(2026, 7, 13),
                    scheduledLocalStartTime = LocalTime.of(18, 0),
                    timeZoneId = "Europe/Berlin",
                    plannedDurationMinutes = 75,
                    trainingLocationId = "gym",
                    createdAtEpochMs = 0,
                    updatedAtEpochMs = 0,
                ),
            ),
            conflicts = listOf(CalendarConflict("occurrence", CalendarConflictType.OVERLAP, "calendar_overlap")),
        ),
        onMode = {},
        onDate = {},
        onNavigate = {},
        onPreviewSchedule = { _, _, _, _ -> },
        onConfirmScheduleSetup = { _ -> },
        onCancelScheduleSetup = {},
        onAddAdHoc = { _, _, _, _, _ -> },
        onSaveOccurrence = { _, _, _, _, _, _, _ -> },
        onCopy = {},
        onSkip = {},
        onCancel = {},
        onUnavailable = {},
        onSaveAvailability = { _, _, _, _, _, _, _, _ -> },
        onResetAvailability = { _, _, _ -> },
        onConfirmScheduleChange = {},
        onCancelScheduleChange = {},
        onClearError = {},
    )
}
