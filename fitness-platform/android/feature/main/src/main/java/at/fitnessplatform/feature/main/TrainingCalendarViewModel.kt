package at.fitnessplatform.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.fitnessplatform.core.model.CalendarConflict
import at.fitnessplatform.core.model.Clock
import at.fitnessplatform.core.model.AvailabilityRule
import at.fitnessplatform.core.model.PlanSchedule
import at.fitnessplatform.core.model.ScheduleOverride
import at.fitnessplatform.core.model.ScheduledWorkoutOccurrence
import at.fitnessplatform.core.model.ScheduledWorkoutStatus
import at.fitnessplatform.core.model.TrainingLocation
import at.fitnessplatform.core.model.TrainingPlan
import at.fitnessplatform.core.model.UuidProvider
import at.fitnessplatform.domain.ChangeOccurrenceStatusUseCase
import at.fitnessplatform.domain.CreatePlanScheduleUseCase
import at.fitnessplatform.domain.DetectCalendarConflictsUseCase
import at.fitnessplatform.domain.EnsureCalendarHorizonUseCase
import at.fitnessplatform.domain.FutureScheduleChangePreview
import at.fitnessplatform.domain.MoveOccurrenceUseCase
import at.fitnessplatform.domain.ObserveActiveTrainingPlanUseCase
import at.fitnessplatform.domain.ScheduleRuleDraft
import at.fitnessplatform.domain.ScheduleSetupPreview
import at.fitnessplatform.domain.PreviewPlanScheduleUseCase
import at.fitnessplatform.domain.TrainingCalendarRepository
import at.fitnessplatform.domain.UpdateScheduleRuleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi

enum class CalendarDisplayMode { TODAY, WEEK, MONTH, AGENDA }
enum class CalendarEditScope { OCCURRENCE_ONLY, THIS_AND_FUTURE }
enum class AvailabilityEditScope { WEEKDAY, SELECTED_DATE }

data class TrainingCalendarUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val today: LocalDate,
    val selectedDate: LocalDate = today,
    val mode: CalendarDisplayMode = CalendarDisplayMode.WEEK,
    val occurrences: List<ScheduledWorkoutOccurrence> = emptyList(),
    val activeSchedule: PlanSchedule? = null,
    val activePlan: TrainingPlan? = null,
    val locations: List<TrainingLocation> = emptyList(),
    val availability: List<AvailabilityRule> = emptyList(),
    val overrides: List<ScheduleOverride> = emptyList(),
    val conflicts: List<CalendarConflict> = emptyList(),
    val pendingScheduleSetup: ScheduleSetupPreview? = null,
    val pendingScheduleChange: FutureScheduleChangePreview? = null,
    val error: String? = null,
) {
    val selectedOccurrences: List<ScheduledWorkoutOccurrence>
        get() = occurrences.filter { it.scheduledLocalDate == selectedDate }
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LongParameterList")
class TrainingCalendarViewModel @Inject constructor(
    repository: TrainingCalendarRepository,
    observeActivePlan: ObserveActiveTrainingPlanUseCase,
    private val createPlanSchedule: CreatePlanScheduleUseCase,
    private val previewPlanSchedule: PreviewPlanScheduleUseCase,
    private val ensureHorizon: EnsureCalendarHorizonUseCase,
    private val moveOccurrence: MoveOccurrenceUseCase,
    private val updateScheduleRule: UpdateScheduleRuleUseCase,
    private val changeStatus: ChangeOccurrenceStatusUseCase,
    private val conflicts: DetectCalendarConflictsUseCase,
    private val calendarRepository: TrainingCalendarRepository,
    locationRepository: at.fitnessplatform.domain.TrainingLocationRepository,
    private val ids: UuidProvider,
    private val clock: Clock,
) : ViewModel() {
    private val zone = ZoneId.systemDefault()
    private val today = Instant.ofEpochMilli(clock.nowEpochMs()).atZone(zone).toLocalDate()
    val state = MutableStateFlow(TrainingCalendarUiState(today = today))
    private val visibleRange = MutableStateFlow(calendarQueryRange(CalendarDisplayMode.WEEK, today))

    init {
        viewModelScope.launch {
            repository.observeActiveSchedule()
                .filterNotNull()
                .map { it.id }
                .distinctUntilChanged()
                .collect { scheduleId ->
                    runCatching { ensureHorizon(scheduleId, today) }
                        .onFailure { error ->
                            state.update {
                                it.copy(error = error.message ?: "CALENDAR_HORIZON_FAILED")
                            }
                        }
                }
        }
        viewModelScope.launch {
            val constraints = combine(
                repository.observeAvailability(),
                repository.observeOverrides(),
                locationRepository.observeLocations(),
            ) { availability, overrides, locations -> Triple(availability, overrides, locations) }
            combine(
                visibleRange.flatMapLatest { range ->
                    val (from, through) = calendarConflictQueryRange(range)
                    repository.observeOccurrences(from, through)
                },
                repository.observeActiveSchedule(),
                observeActivePlan(),
                constraints,
            ) { occurrences, schedule, plan, constraintRows ->
                val (availability, overrides, locations) = constraintRows
                val (visibleFrom, visibleThrough) = visibleRange.value
                val visibleOccurrences = occurrences.filter {
                    it.scheduledLocalDate in visibleFrom..visibleThrough
                }
                val visibleOccurrenceIds = visibleOccurrences.mapTo(mutableSetOf()) { it.id }
                state.value.copy(
                    loading = false,
                    occurrences = visibleOccurrences,
                    activeSchedule = schedule,
                    activePlan = plan,
                    availability = availability,
                    overrides = overrides,
                    locations = locations,
                    conflicts = conflicts(occurrences, availability, overrides, locations)
                        .filter { it.occurrenceId in visibleOccurrenceIds },
                )
            }.catch { error ->
                state.update { it.copy(loading = false, error = error.message ?: "CALENDAR_LOAD_FAILED") }
            }.collect(state)
        }
    }

    fun selectDate(date: LocalDate) {
        state.update { it.copy(selectedDate = date) }
        visibleRange.value = calendarQueryRange(state.value.mode, date)
    }
    fun setMode(mode: CalendarDisplayMode) {
        state.update { it.copy(mode = mode) }
        visibleRange.value = calendarQueryRange(mode, state.value.selectedDate)
    }
    fun navigatePeriod(delta: Long) {
        val current = state.value
        val next = when (current.mode) {
            CalendarDisplayMode.TODAY -> current.selectedDate.plusDays(delta)
            CalendarDisplayMode.WEEK -> current.selectedDate.plusWeeks(delta)
            CalendarDisplayMode.MONTH -> current.selectedDate.plusMonths(delta)
            CalendarDisplayMode.AGENDA -> current.selectedDate.plusDays(delta * 14)
        }
        selectDate(next)
    }
    fun clearError() = state.update { it.copy(error = null) }

    fun previewSchedule(
        startDate: LocalDate,
        timeZoneId: String,
        drafts: List<ScheduleRuleDraft>,
        onSuccess: () -> Unit = {},
    ) = operation(onSuccess) {
        val plan = requireNotNull(state.value.activePlan) { "Select an active plan first." }
        state.update {
            it.copy(
                pendingScheduleSetup = previewPlanSchedule(
                    plan = plan,
                    startDate = startDate,
                    timeZoneId = timeZoneId,
                    drafts = drafts,
                    availability = it.availability,
                    overrides = it.overrides,
                    locations = it.locations,
                ),
            )
        }
    }

    fun confirmScheduleSetup(onSuccess: () -> Unit = {}) = operation(onSuccess) {
        val plan = requireNotNull(state.value.activePlan) { "Select an active plan first." }
        val preview = requireNotNull(state.value.pendingScheduleSetup) { "No schedule setup is pending." }
        createPlanSchedule(plan, preview.startDate, preview.timeZoneId, preview.drafts)
        state.update { it.copy(pendingScheduleSetup = null) }
    }

    fun cancelScheduleSetup() {
        state.update { it.copy(pendingScheduleSetup = null) }
    }

    fun saveOccurrence(
        occurrence: ScheduledWorkoutOccurrence,
        date: LocalDate,
        time: LocalTime?,
        durationMinutes: Int,
        locationId: String?,
        scope: CalendarEditScope,
        onSuccess: () -> Unit = {},
    ) = when (scope) {
        CalendarEditScope.OCCURRENCE_ONLY -> operation(onSuccess) {
            moveOccurrence(occurrence, date, time, durationMinutes, locationId)
        }
        CalendarEditScope.THIS_AND_FUTURE -> operation {
                val schedule = requireNotNull(state.value.activeSchedule) { "No active schedule exists." }
                val planDayId = requireNotNull(occurrence.planDayId) { "An ad-hoc workout has no recurring rule." }
                val preview = updateScheduleRule.preview(
                    schedule,
                    planDayId,
                    date,
                    time,
                    durationMinutes,
                    locationId,
                )
                state.update { it.copy(pendingScheduleChange = preview) }
        }
    }

    fun confirmScheduleChange(onSuccess: () -> Unit = {}) = operation(onSuccess) {
        val preview = requireNotNull(state.value.pendingScheduleChange) { "No schedule change is pending." }
        updateScheduleRule.confirm(preview)
        state.update { it.copy(pendingScheduleChange = null) }
    }

    fun cancelScheduleChange() {
        state.update { it.copy(pendingScheduleChange = null) }
    }

    fun addAdHoc(
        title: String,
        date: LocalDate,
        time: LocalTime?,
        durationMinutes: Int,
        onSuccess: () -> Unit = {},
    ) = operation(onSuccess) {
        val plan = state.value.activePlan
        calendarRepository.saveOccurrence(
            ScheduledWorkoutOccurrence(
                id = ids.newUuid(),
                ownerProfileId = requireNotNull(plan?.ownerProfileId) { "Create an active plan first." },
                planId = plan.id,
                titleSnapshot = title,
                scheduledLocalDate = date,
                scheduledLocalStartTime = time,
                timeZoneId = zone.id,
                plannedDurationMinutes = durationMinutes,
                createdAtEpochMs = clock.nowEpochMs(),
                updatedAtEpochMs = clock.nowEpochMs(),
            ),
        )
    }

    fun copy(id: String) = operation { calendarRepository.copyOccurrence(id) }
    fun skip(id: String) = operation { changeStatus(id, ScheduledWorkoutStatus.SKIPPED) }
    fun cancel(id: String) = operation { changeStatus(id, ScheduledWorkoutStatus.CANCELLED) }

    fun markUnavailable(date: LocalDate) = operation {
        val owner = requireNotNull(state.value.activePlan?.ownerProfileId) { "Create an active plan first." }
        calendarRepository.saveOverride(
            ScheduleOverride(
                id = ids.newUuid(),
                ownerProfileId = owner,
                localDate = date,
                unavailable = true,
            ),
        )
    }

    fun saveAvailability(
        date: LocalDate,
        earliest: LocalTime?,
        latest: LocalTime?,
        maxDurationMinutes: Int?,
        locationId: String?,
        unavailable: Boolean,
        scope: AvailabilityEditScope,
        onSuccess: () -> Unit = {},
    ) = operation(onSuccess) {
        val owner = requireNotNull(state.value.activePlan?.ownerProfileId) { "Create an active plan first." }
        when (scope) {
            AvailabilityEditScope.WEEKDAY -> calendarRepository.saveAvailability(
                at.fitnessplatform.core.model.AvailabilityRule(
                    id = state.value.availability.firstOrNull {
                        it.ownerProfileId == owner && it.dayOfWeek == date.dayOfWeek
                    }?.id ?: ids.newUuid(),
                    ownerProfileId = owner,
                    dayOfWeek = date.dayOfWeek,
                    earliestLocalTime = earliest,
                    latestLocalTime = latest,
                    maxDurationMinutes = maxDurationMinutes,
                    preferredLocationId = locationId,
                    enabled = true,
                ),
            )
            AvailabilityEditScope.SELECTED_DATE -> calendarRepository.saveOverride(
                ScheduleOverride(
                    id = state.value.overrides.firstOrNull {
                        it.ownerProfileId == owner && it.localDate == date
                    }?.id ?: ids.newUuid(),
                    ownerProfileId = owner,
                    localDate = date,
                    unavailable = unavailable,
                    earliestLocalTime = earliest,
                    latestLocalTime = latest,
                    maxDurationMinutes = maxDurationMinutes,
                    locationId = locationId,
                ),
            )
        }
    }

    fun resetAvailability(date: LocalDate, scope: AvailabilityEditScope, onSuccess: () -> Unit = {}) =
        operation(onSuccess) {
            when (scope) {
                AvailabilityEditScope.WEEKDAY -> state.value.availability
                    .firstOrNull { it.dayOfWeek == date.dayOfWeek }
                    ?.let { calendarRepository.deleteAvailability(it.id) }
                AvailabilityEditScope.SELECTED_DATE -> state.value.overrides
                    .firstOrNull { it.localDate == date }
                    ?.let { calendarRepository.deleteOverride(it.id) }
            }
        }

    private fun operation(onSuccess: () -> Unit = {}, block: suspend () -> Unit) = viewModelScope.launch {
        if (state.value.saving) return@launch
        state.update { it.copy(saving = true, error = null) }
        runCatching { block() }
            .onSuccess { onSuccess() }
            .onFailure { error -> state.update { it.copy(error = error.message ?: "CALENDAR_OPERATION_FAILED") } }
        state.update { it.copy(saving = false) }
    }
}

internal fun calendarQueryRange(mode: CalendarDisplayMode, selectedDate: LocalDate): Pair<LocalDate, LocalDate> =
    when (mode) {
        CalendarDisplayMode.TODAY -> selectedDate to selectedDate
        CalendarDisplayMode.WEEK -> {
            val start = selectedDate.minusDays((selectedDate.dayOfWeek.value - 1).toLong())
            start to start.plusDays(6)
        }
        CalendarDisplayMode.MONTH -> selectedDate.withDayOfMonth(1).let { start ->
            start to start.withDayOfMonth(start.lengthOfMonth())
        }
        CalendarDisplayMode.AGENDA -> selectedDate to selectedDate.plusDays(55)
    }

internal fun calendarConflictQueryRange(
    visibleRange: Pair<LocalDate, LocalDate>,
): Pair<LocalDate, LocalDate> = visibleRange.first.minusDays(1) to visibleRange.second.plusDays(1)
