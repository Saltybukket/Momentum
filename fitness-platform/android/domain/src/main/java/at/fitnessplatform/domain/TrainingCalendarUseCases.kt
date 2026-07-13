package at.fitnessplatform.domain

import at.fitnessplatform.core.model.AvailabilityRule
import at.fitnessplatform.core.model.CalendarConflict
import at.fitnessplatform.core.model.CalendarConflictType
import at.fitnessplatform.core.model.PlanSchedule
import at.fitnessplatform.core.model.ScheduleOverride
import at.fitnessplatform.core.model.ScheduledWorkoutOccurrence
import at.fitnessplatform.core.model.ScheduledWorkoutStatus
import at.fitnessplatform.core.model.TrainingPlan
import at.fitnessplatform.core.model.Clock
import at.fitnessplatform.core.model.PlanDayScheduleRule
import at.fitnessplatform.core.model.UuidProvider
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

const val MATERIALIZATION_HORIZON_DAYS = 56L

interface TrainingCalendarRepository {
    fun observeOccurrences(from: LocalDate, to: LocalDate): Flow<List<ScheduledWorkoutOccurrence>>
    fun observeActiveSchedule(): Flow<PlanSchedule?>
    fun observeAvailability(): Flow<List<AvailabilityRule>>
    fun observeOverrides(): Flow<List<ScheduleOverride>>
    suspend fun saveSchedule(schedule: PlanSchedule): PlanSchedule
    suspend fun materialize(scheduleId: String, through: LocalDate): List<ScheduledWorkoutOccurrence>
    suspend fun replaceFuturePlanned(
        scheduleId: String,
        from: LocalDate,
        through: LocalDate,
    ): List<ScheduledWorkoutOccurrence>
    suspend fun saveOccurrence(occurrence: ScheduledWorkoutOccurrence): ScheduledWorkoutOccurrence
    suspend fun copyOccurrence(id: String): ScheduledWorkoutOccurrence
    suspend fun changeStatus(id: String, status: ScheduledWorkoutStatus)
    suspend fun saveAvailability(rule: AvailabilityRule)
    suspend fun saveOverride(override: ScheduleOverride)
}

fun validateSchedule(schedule: PlanSchedule) {
    ensureCalendar(schedule.ownerProfileId.isNotBlank()) { "Schedule owner is required." }
    ensureCalendar(schedule.planId.isNotBlank()) { "Schedule plan is required." }
    validZone(schedule.timeZoneId, "Schedule")
    ensureCalendar(schedule.rules.map { it.position }.distinct().size == schedule.rules.size) {
        "Schedule-rule positions must be unique."
    }
    ensureCalendar(schedule.rules.map { it.planDayId }.distinct().size == schedule.rules.size) {
        "A plan day can have only one recurring schedule rule."
    }
    schedule.rules.forEach { rule ->
        ensureCalendar(rule.scheduleId == schedule.id) { "Schedule rules must belong to their schedule." }
        ensureCalendar(rule.planDayId.isNotBlank()) { "Schedule rules require a plan day." }
        ensureCalendar(rule.defaultDurationMinutes in 1..1_440) {
            "Rule duration must be between 1 and 1440 minutes."
        }
        ensureCalendar(rule.position >= 0) { "Rule position cannot be negative." }
    }
}

fun validateOccurrence(occurrence: ScheduledWorkoutOccurrence) {
    ensureCalendar(occurrence.ownerProfileId.isNotBlank()) { "Occurrence owner is required." }
    ensureCalendar(occurrence.titleSnapshot.isNotBlank() && occurrence.titleSnapshot.length <= 100) {
        "Occurrence title must contain 1 to 100 characters."
    }
    ensureCalendar(occurrence.plannedDurationMinutes in 1..1_440) {
        "Occurrence duration must be between 1 and 1440 minutes."
    }
    validZone(occurrence.timeZoneId, "Occurrence")
    ensureCalendar(occurrence.notes.length <= 2_000) { "Occurrence notes are too long." }
}

fun validateAvailability(rule: AvailabilityRule) {
    ensureCalendar(rule.ownerProfileId.isNotBlank()) { "Availability owner is required." }
    ensureCalendar(rule.maxDurationMinutes?.let { it in 1..1_440 } != false) {
        "Availability duration must be between 1 and 1440 minutes."
    }
    val earliest = rule.earliestLocalTime
    val latest = rule.latestLocalTime
    ensureCalendar(earliest == null || latest == null || earliest < latest) {
        "Availability end must be after its start."
    }
}

fun validateOverride(override: ScheduleOverride) {
    ensureCalendar(override.ownerProfileId.isNotBlank()) { "Override owner is required." }
    ensureCalendar(override.maxDurationMinutes?.let { it in 1..1_440 } != false) {
        "Override duration must be between 1 and 1440 minutes."
    }
    val earliest = override.earliestLocalTime
    val latest = override.latestLocalTime
    ensureCalendar(earliest == null || latest == null || earliest < latest) {
        "Override end must be after its start."
    }
}

/** Builds civil-time occurrences; conversion to an instant is intentionally deferred to execution. */
fun materializeSchedule(
    schedule: PlanSchedule,
    plan: TrainingPlan,
    through: LocalDate,
    nowEpochMs: Long,
    idFor: (ruleId: String, localDate: LocalDate) -> String,
): List<ScheduledWorkoutOccurrence> {
    validateSchedule(schedule)
    ensureCalendar(plan.id == schedule.planId && plan.ownerProfileId == schedule.ownerProfileId) {
        "Schedule and plan must share identity and owner."
    }
    if (through < schedule.startDate) return emptyList()
    val days = plan.weeks.flatMap { it.days }.associateBy { it.id }
    return generateSequence(schedule.startDate) { current -> current.plusDays(1) }
        .takeWhile { it <= through }
        .flatMap { date ->
            schedule.rules.asSequence()
                .filter { it.dayOfWeek == date.dayOfWeek }
                .sortedBy { it.position }
                .map { rule ->
                    val day = requireNotNull(days[rule.planDayId]) { "Schedule rule references an unknown plan day." }
                    ScheduledWorkoutOccurrence(
                        id = idFor(rule.id, date),
                        ownerProfileId = schedule.ownerProfileId,
                        scheduleId = schedule.id,
                        planId = plan.id,
                        planDayId = day.id,
                        titleSnapshot = day.title,
                        scheduledLocalDate = date,
                        scheduledLocalStartTime = rule.defaultStartTime,
                        timeZoneId = schedule.timeZoneId,
                        plannedDurationMinutes = rule.defaultDurationMinutes,
                        trainingLocationId = rule.preferredLocationId,
                        createdAtEpochMs = nowEpochMs,
                        updatedAtEpochMs = nowEpochMs,
                    )
                }
        }.toList()
}

@Suppress("CyclomaticComplexMethod")
fun detectCalendarConflicts(
    occurrences: List<ScheduledWorkoutOccurrence>,
    availability: List<AvailabilityRule>,
    overrides: List<ScheduleOverride>,
    requiredEquipmentByPlanDay: Map<String, Set<String>> = emptyMap(),
    equipmentByLocation: Map<String, Set<String>> = emptyMap(),
    unavailablePlanDays: Set<String> = emptySet(),
): List<CalendarConflict> = buildList {
    val planningRows = occurrences.filter { it.status in setOf(ScheduledWorkoutStatus.PLANNED, ScheduledWorkoutStatus.CONFLICT) }
    planningRows.forEach { occurrence ->
        val override = overrides.firstOrNull { it.localDate == occurrence.scheduledLocalDate }
        val recurring = availability.firstOrNull {
            it.enabled && it.dayOfWeek == occurrence.scheduledLocalDate.dayOfWeek
        }
        val earliest = override?.earliestLocalTime ?: recurring?.earliestLocalTime
        val latest = override?.latestLocalTime ?: recurring?.latestLocalTime
        val maximum = override?.maxDurationMinutes ?: recurring?.maxDurationMinutes
        if (override?.unavailable == true || outsideWindow(occurrence, earliest, latest)) {
            add(CalendarConflict(occurrence.id, CalendarConflictType.OUTSIDE_AVAILABILITY, "calendar_outside_availability"))
        }
        if (maximum != null && occurrence.plannedDurationMinutes > maximum) {
            add(CalendarConflict(occurrence.id, CalendarConflictType.DURATION_EXCEEDED, "calendar_duration_exceeded"))
        }
        val requiredLocation = override?.locationId ?: recurring?.preferredLocationId
        if (requiredLocation != null && occurrence.trainingLocationId != requiredLocation) {
            add(CalendarConflict(occurrence.id, CalendarConflictType.LOCATION_REQUIRED, "calendar_location_required"))
        }
        val requiredEquipment = occurrence.planDayId?.let(requiredEquipmentByPlanDay::get).orEmpty()
            .filterNotTo(mutableSetOf()) { it == at.fitnessplatform.core.model.EquipmentDefinitions.NONE }
        val availableEquipment = occurrence.trainingLocationId?.let(equipmentByLocation::get)
        if (requiredEquipment.isNotEmpty() && availableEquipment != null && !availableEquipment.containsAll(requiredEquipment)) {
            add(CalendarConflict(occurrence.id, CalendarConflictType.MISSING_EQUIPMENT, "calendar_missing_equipment"))
        }
        if (occurrence.planDayId in unavailablePlanDays) {
            add(CalendarConflict(occurrence.id, CalendarConflictType.UNAVAILABLE_EXERCISE, "calendar_unavailable_exercise"))
        }
    }
    planningRows.groupBy { it.scheduledLocalDate }.values.forEach { sameDay ->
        sameDay.filter { it.scheduledLocalStartTime != null }
            .sortedBy { it.scheduledLocalStartTime }
            .zipWithNext()
            .forEach { (first, second) ->
                val end = first.scheduledLocalStartTime!!.plusMinutes(first.plannedDurationMinutes.toLong())
                if (end > second.scheduledLocalStartTime) {
                    add(CalendarConflict(second.id, CalendarConflictType.OVERLAP, "calendar_overlap"))
                }
            }
    }
}

private fun outsideWindow(
    occurrence: ScheduledWorkoutOccurrence,
    earliest: LocalTime?,
    latest: LocalTime?,
): Boolean {
    val start = occurrence.scheduledLocalStartTime ?: return false
    val end = start.plusMinutes(occurrence.plannedDurationMinutes.toLong())
    return earliest?.let { start < it } == true || latest?.let { end > it } == true
}

class MaterializeScheduleUseCase(private val repository: TrainingCalendarRepository) {
    suspend operator fun invoke(schedule: PlanSchedule): List<ScheduledWorkoutOccurrence> {
        validateSchedule(schedule)
        val saved = repository.saveSchedule(schedule)
        return repository.materialize(saved.id, saved.startDate.plusDays(MATERIALIZATION_HORIZON_DAYS - 1))
    }
}

class ReplaceFutureScheduleUseCase(private val repository: TrainingCalendarRepository) {
    suspend operator fun invoke(schedule: PlanSchedule, from: LocalDate): List<ScheduledWorkoutOccurrence> {
        validateSchedule(schedule)
        val saved = repository.saveSchedule(schedule)
        val through = from.plusDays(MATERIALIZATION_HORIZON_DAYS - 1)
        return repository.replaceFuturePlanned(saved.id, from, through)
    }
}

class MoveOccurrenceUseCase(private val repository: TrainingCalendarRepository) {
    suspend operator fun invoke(
        occurrence: ScheduledWorkoutOccurrence,
        date: LocalDate,
        time: LocalTime?,
        durationMinutes: Int,
        locationId: String?,
    ): ScheduledWorkoutOccurrence {
        ensureCalendar(
            occurrence.status !in setOf(
                ScheduledWorkoutStatus.IN_PROGRESS,
                ScheduledWorkoutStatus.COMPLETED,
                ScheduledWorkoutStatus.SKIPPED,
                ScheduledWorkoutStatus.CANCELLED,
            ),
        ) {
            "Terminal occurrences cannot be moved."
        }
        val moved = occurrence.copy(
            scheduledLocalDate = date,
            scheduledLocalStartTime = time,
            plannedDurationMinutes = durationMinutes,
            trainingLocationId = locationId,
            originalScheduledDate = occurrence.originalScheduledDate ?: occurrence.scheduledLocalDate,
            revision = occurrence.revision + 1,
        )
        validateOccurrence(moved)
        return repository.saveOccurrence(moved)
    }
}

class ChangeOccurrenceStatusUseCase(private val repository: TrainingCalendarRepository) {
    suspend operator fun invoke(id: String, status: ScheduledWorkoutStatus) {
        ensureCalendar(status in setOf(ScheduledWorkoutStatus.SKIPPED, ScheduledWorkoutStatus.CANCELLED)) {
            "Only skip and cancel are available before workout execution."
        }
        repository.changeStatus(id, status)
    }
}

class DetectCalendarConflictsUseCase {
    operator fun invoke(
        occurrences: List<ScheduledWorkoutOccurrence>,
        availability: List<AvailabilityRule>,
        overrides: List<ScheduleOverride>,
        plan: TrainingPlan? = null,
        locations: List<at.fitnessplatform.core.model.TrainingLocation> = emptyList(),
    ): List<CalendarConflict> {
        val requirements = plan?.weeks.orEmpty().flatMap { it.days }.associate { day ->
            day.id to day.blocks.flatMap { it.exercises }
                .flatMapTo(mutableSetOf()) { it.reference.snapshot.equipment }
        }
        val unavailableDays = plan?.weeks.orEmpty().flatMap { it.days }.filter { day ->
            day.blocks.flatMap { it.exercises }.any {
                it.reference.resolutionStatus != at.fitnessplatform.core.model.ExerciseResolutionStatus.RESOLVED
            }
        }.mapTo(mutableSetOf()) { it.id }
        return detectCalendarConflicts(
            occurrences,
            availability,
            overrides,
            requirements,
            locations.associate { it.id to it.availableEquipment },
            unavailableDays,
        )
    }
}

data class ScheduleRuleDraft(
    val planDayId: String,
    val dayOfWeek: java.time.DayOfWeek,
    val startTime: LocalTime?,
    val durationMinutes: Int,
    val locationId: String?,
)

class CreatePlanScheduleUseCase(
    private val repository: TrainingCalendarRepository,
    private val ids: UuidProvider,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        plan: TrainingPlan,
        startDate: LocalDate,
        timeZoneId: String,
        drafts: List<ScheduleRuleDraft>,
    ): List<ScheduledWorkoutOccurrence> {
        val scheduleId = ids.newUuid()
        val now = clock.nowEpochMs()
        val schedule = PlanSchedule(
            id = scheduleId,
            ownerProfileId = plan.ownerProfileId,
            planId = plan.id,
            startDate = startDate,
            timeZoneId = timeZoneId,
            isActive = true,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            rules = drafts.mapIndexed { index, draft ->
                PlanDayScheduleRule(
                    id = ids.newUuid(),
                    scheduleId = scheduleId,
                    planDayId = draft.planDayId,
                    dayOfWeek = draft.dayOfWeek,
                    defaultStartTime = draft.startTime,
                    defaultDurationMinutes = draft.durationMinutes,
                    preferredLocationId = draft.locationId,
                    position = index,
                )
            },
        )
        validateSchedule(schedule)
        val saved = repository.saveSchedule(schedule)
        return repository.materialize(saved.id, startDate.plusDays(MATERIALIZATION_HORIZON_DAYS - 1))
    }
}

class UpdateScheduleRuleUseCase(
    private val repository: TrainingCalendarRepository,
) {
    suspend operator fun invoke(
        schedule: PlanSchedule,
        planDayId: String,
        effectiveDate: LocalDate,
        time: LocalTime?,
        durationMinutes: Int,
        locationId: String?,
    ): List<ScheduledWorkoutOccurrence> {
        val updated = schedule.copy(
            rules = schedule.rules.map { rule ->
                if (rule.planDayId != planDayId) rule else rule.copy(
                    dayOfWeek = effectiveDate.dayOfWeek,
                    defaultStartTime = time,
                    defaultDurationMinutes = durationMinutes,
                    preferredLocationId = locationId,
                )
            },
        )
        validateSchedule(updated)
        val saved = repository.saveSchedule(updated)
        val through = effectiveDate.plusDays(MATERIALIZATION_HORIZON_DAYS - 1)
        return repository.replaceFuturePlanned(saved.id, effectiveDate, through)
    }
}

private fun validZone(value: String, label: String) {
    runCatching { ZoneId.of(value) }
        .getOrElse { throw ValidationException("$label requires a valid IANA time zone.") }
}

private inline fun ensureCalendar(condition: Boolean, message: () -> String) {
    if (!condition) throw ValidationException(message())
}
