package at.fitnessplatform.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.fitnessplatform.core.model.CalendarConflict
import at.fitnessplatform.core.model.Clock
import at.fitnessplatform.core.model.PlanSchedule
import at.fitnessplatform.core.model.ScheduleOverride
import at.fitnessplatform.core.model.ScheduledWorkoutOccurrence
import at.fitnessplatform.core.model.ScheduledWorkoutStatus
import at.fitnessplatform.core.model.TrainingPlan
import at.fitnessplatform.core.model.UuidProvider
import at.fitnessplatform.domain.ChangeOccurrenceStatusUseCase
import at.fitnessplatform.domain.CreatePlanScheduleUseCase
import at.fitnessplatform.domain.DetectCalendarConflictsUseCase
import at.fitnessplatform.domain.MoveOccurrenceUseCase
import at.fitnessplatform.domain.ObserveActiveTrainingPlanUseCase
import at.fitnessplatform.domain.ScheduleRuleDraft
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val conflicts: List<CalendarConflict> = emptyList(),
    val error: String? = null,
) {
    val selectedOccurrences: List<ScheduledWorkoutOccurrence>
        get() = occurrences.filter { it.scheduledLocalDate == selectedDate }
}

@HiltViewModel
class TrainingCalendarViewModel @Inject constructor(
    repository: TrainingCalendarRepository,
    observeActivePlan: ObserveActiveTrainingPlanUseCase,
    private val createSchedule: CreatePlanScheduleUseCase,
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

    init {
        viewModelScope.launch {
            val constraints = combine(
                repository.observeAvailability(),
                repository.observeOverrides(),
                locationRepository.observeLocations(),
            ) { availability, overrides, locations -> Triple(availability, overrides, locations) }
            combine(
                repository.observeOccurrences(today.minusDays(31), today.plusDays(90)),
                repository.observeActiveSchedule(),
                observeActivePlan(),
                constraints,
            ) { occurrences, schedule, plan, constraintRows ->
                val (availability, overrides, locations) = constraintRows
                state.value.copy(
                    loading = false,
                    occurrences = occurrences,
                    activeSchedule = schedule,
                    activePlan = plan,
                    conflicts = conflicts(occurrences, availability, overrides, plan, locations),
                )
            }.catch { error ->
                state.update { it.copy(loading = false, error = error.message ?: "CALENDAR_LOAD_FAILED") }
            }.collect(state)
        }
    }

    fun selectDate(date: LocalDate) = state.update { it.copy(selectedDate = date) }
    fun setMode(mode: CalendarDisplayMode) = state.update { it.copy(mode = mode) }
    fun clearError() = state.update { it.copy(error = null) }

    fun createDefaultSchedule(onSuccess: () -> Unit = {}) = operation(onSuccess) {
        val plan = requireNotNull(state.value.activePlan) { "Select an active plan first." }
        val drafts = plan.weeks.flatMap { it.days }.mapIndexed { index, day ->
            ScheduleRuleDraft(
                planDayId = day.id,
                dayOfWeek = today.plusDays((day.relativeDayIndex + index * 2L) % 7).dayOfWeek,
                startTime = LocalTime.of(18, 0),
                durationMinutes = day.estimatedDurationMinutes ?: 60,
                locationId = null,
            )
        }
        createSchedule(plan, today, zone.id, drafts)
    }

    fun saveOccurrence(
        occurrence: ScheduledWorkoutOccurrence,
        date: LocalDate,
        time: LocalTime?,
        durationMinutes: Int,
        locationId: String?,
        scope: CalendarEditScope,
        onSuccess: () -> Unit = {},
    ) = operation(onSuccess) {
        when (scope) {
            CalendarEditScope.OCCURRENCE_ONLY -> moveOccurrence(
                occurrence,
                date,
                time,
                durationMinutes,
                locationId,
            )
            CalendarEditScope.THIS_AND_FUTURE -> {
                val schedule = requireNotNull(state.value.activeSchedule) { "No active schedule exists." }
                val planDayId = requireNotNull(occurrence.planDayId) { "An ad-hoc workout has no recurring rule." }
                updateScheduleRule(schedule, planDayId, date, time, durationMinutes, locationId)
            }
        }
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
                    id = ids.newUuid(),
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
                    id = ids.newUuid(),
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

    private fun operation(onSuccess: () -> Unit = {}, block: suspend () -> Unit) = viewModelScope.launch {
        if (state.value.saving) return@launch
        state.update { it.copy(saving = true, error = null) }
        runCatching { block() }
            .onSuccess { onSuccess() }
            .onFailure { error -> state.update { it.copy(error = error.message ?: "CALENDAR_OPERATION_FAILED") } }
        state.update { it.copy(saving = false) }
    }
}
