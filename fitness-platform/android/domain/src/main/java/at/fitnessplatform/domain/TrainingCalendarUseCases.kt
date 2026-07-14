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
import at.fitnessplatform.core.model.OccurrenceOrigin
import at.fitnessplatform.core.model.UuidProvider
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.text.Normalizer
import kotlinx.coroutines.flow.Flow

const val MATERIALIZATION_HORIZON_DAYS = 56L
private val disallowedCalendarText =
    Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F-\\u009F\\u202A-\\u202E\\u2066-\\u2069]")
private val horizontalWhitespace = Regex("[\\t ]+")

enum class CalendarAction { EDIT, MOVE, COPY, SKIP, CANCEL, VIEW }

fun allowedCalendarActions(status: ScheduledWorkoutStatus): Set<CalendarAction> = when (status) {
    ScheduledWorkoutStatus.PLANNED, ScheduledWorkoutStatus.CONFLICT, ScheduledWorkoutStatus.MOVED -> setOf(
        CalendarAction.EDIT,
        CalendarAction.MOVE,
        CalendarAction.COPY,
        CalendarAction.SKIP,
        CalendarAction.CANCEL,
    )
    ScheduledWorkoutStatus.IN_PROGRESS -> emptySet()
    ScheduledWorkoutStatus.COMPLETED,
    ScheduledWorkoutStatus.SKIPPED,
    ScheduledWorkoutStatus.CANCELLED,
    -> setOf(CalendarAction.VIEW, CalendarAction.COPY)
}

interface TrainingCalendarRepository {
    fun observeOccurrences(from: LocalDate, to: LocalDate): Flow<List<ScheduledWorkoutOccurrence>>
    fun observeActiveSchedule(): Flow<PlanSchedule?>
    fun observeAvailability(): Flow<List<AvailabilityRule>>
    fun observeOverrides(): Flow<List<ScheduleOverride>>
    suspend fun saveSchedule(schedule: PlanSchedule): PlanSchedule
    suspend fun materialize(scheduleId: String, through: LocalDate): List<ScheduledWorkoutOccurrence>
    suspend fun ensureHorizon(
        scheduleId: String,
        from: LocalDate,
        through: LocalDate,
    ): List<ScheduledWorkoutOccurrence>
    suspend fun generatedOccurrences(
        scheduleId: String,
        from: LocalDate,
        through: LocalDate,
    ): List<ScheduledWorkoutOccurrence>
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
    suspend fun deleteAvailability(id: String)
    suspend fun deleteOverride(id: String)
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
    ensureCalendar(occurrence.titleSnapshot == canonicalCalendarText(occurrence.titleSnapshot, singleLine = true)) {
        "Occurrence title must use canonical safe text."
    }
    ensureCalendar(occurrence.notes == canonicalCalendarText(occurrence.notes, singleLine = false)) {
        "Occurrence notes must use canonical safe text."
    }
}

fun canonicalCalendarText(value: String, singleLine: Boolean): String {
    require(!disallowedCalendarText.containsMatchIn(value)) { "Calendar text contains unsafe characters." }
    val normalized = Normalizer.normalize(value, Normalizer.Form.NFC).replace("\r\n", "\n").replace('\r', '\n')
    val lines = normalized.lines().map { horizontalWhitespace.replace(it.trim(), " ") }
    return if (singleLine) lines.joinToString(" ").trim() else lines.joinToString("\n").trim()
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
): List<ScheduledWorkoutOccurrence> = materializeScheduleRange(
    schedule = schedule,
    plan = plan,
    from = schedule.startDate,
    through = through,
    nowEpochMs = nowEpochMs,
    idFor = idFor,
)

fun materializeScheduleRange(
    schedule: PlanSchedule,
    plan: TrainingPlan,
    from: LocalDate,
    through: LocalDate,
    nowEpochMs: Long,
    idFor: (ruleId: String, localDate: LocalDate) -> String,
): List<ScheduledWorkoutOccurrence> {
    validateSchedule(schedule)
    ensureCalendar(plan.id == schedule.planId && plan.ownerProfileId == schedule.ownerProfileId) {
        "Schedule and plan must share identity and owner."
    }
    if (through < schedule.startDate) return emptyList()
    val weeks = plan.weeks.sortedBy { it.weekIndex }
    ensureCalendar(weeks.isNotEmpty()) { "A scheduled plan requires at least one week." }
    val days = weeks.flatMap { week -> week.days.map { day -> day.id to (week to day) } }.toMap()
    schedule.rules.forEach { rule ->
        ensureCalendar(rule.planDayId in days) { "Schedule rule references an unknown plan day." }
    }
    val firstDate = maxOf(schedule.startDate, from)
    return generateSequence(firstDate) { current -> current.plusDays(1) }
        .takeWhile { it <= through }
        .flatMap { date ->
            val cycleWeek = (java.time.temporal.ChronoUnit.DAYS.between(schedule.startDate, date) / 7)
                .mod(weeks.size.toLong()).toInt()
            val activeWeek = weeks[cycleWeek]
            schedule.rules.asSequence()
                .filter { it.dayOfWeek == date.dayOfWeek }
                .filter { rule -> days[rule.planDayId]?.first?.id == activeWeek.id }
                .sortedBy { it.position }
                .map { rule ->
                    val (week, day) = requireNotNull(days[rule.planDayId]) {
                        "Schedule rule references an unknown plan day."
                    }
                    val exercises = day.blocks.flatMap { it.exercises }
                    ScheduledWorkoutOccurrence(
                        id = idFor(rule.id, date),
                        ownerProfileId = schedule.ownerProfileId,
                        scheduleId = schedule.id,
                        planId = plan.id,
                        planDayId = day.id,
                        planDayIdSnapshot = day.id,
                        planRevisionSnapshot = plan.revision,
                        planWeekIndexSnapshot = week.weekIndex,
                        requiredEquipmentSnapshot = exercises.flatMapTo(mutableSetOf()) {
                            it.reference.snapshot.equipment
                        },
                        hasUnavailableExerciseSnapshot = exercises.any {
                            it.reference.resolutionStatus !=
                                at.fitnessplatform.core.model.ExerciseResolutionStatus.RESOLVED
                        },
                        titleSnapshot = day.title,
                        scheduledLocalDate = date,
                        scheduledLocalStartTime = rule.defaultStartTime,
                        timeZoneId = schedule.timeZoneId,
                        plannedDurationMinutes = rule.defaultDurationMinutes,
                        trainingLocationId = rule.preferredLocationId,
                        origin = OccurrenceOrigin.GENERATED,
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
    equipmentByLocation: Map<String, Set<String>> = emptyMap(),
): List<CalendarConflict> = buildList {
    val planningRows = occurrences.filter { it.status in setOf(ScheduledWorkoutStatus.PLANNED, ScheduledWorkoutStatus.CONFLICT) }
    planningRows.forEach { occurrence ->
        val override = overrides.firstOrNull {
            it.ownerProfileId == occurrence.ownerProfileId && it.localDate == occurrence.scheduledLocalDate
        }
        val recurring = availability.firstOrNull {
            it.ownerProfileId == occurrence.ownerProfileId &&
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
        val requiredEquipment = occurrence.requiredEquipmentSnapshot
            .filterNotTo(mutableSetOf()) { it == at.fitnessplatform.core.model.EquipmentDefinitions.NONE }
        val locationId = occurrence.trainingLocationId
        if (requiredEquipment.isNotEmpty() && locationId == null) {
            add(CalendarConflict(occurrence.id, CalendarConflictType.LOCATION_REQUIRED, "calendar_location_required"))
        } else if (
            requiredEquipment.isNotEmpty() &&
            !equipmentByLocation[locationId].orEmpty().containsAll(requiredEquipment)
        ) {
            add(CalendarConflict(occurrence.id, CalendarConflictType.MISSING_EQUIPMENT, "calendar_missing_equipment"))
        }
        if (occurrence.hasUnavailableExerciseSnapshot) {
            add(CalendarConflict(occurrence.id, CalendarConflictType.UNAVAILABLE_EXERCISE, "calendar_unavailable_exercise"))
        }
        if (occurrence.toInstantInterval() == null && occurrence.scheduledLocalStartTime != null) {
            add(CalendarConflict(occurrence.id, CalendarConflictType.INVALID_LOCAL_TIME, "calendar_invalid_local_time"))
        }
    }
    val intervals = planningRows.mapNotNull { occurrence ->
        occurrence.toInstantInterval()?.let { occurrence to it }
    }
    intervals.forEachIndexed { index, (firstOccurrence, first) ->
        intervals.drop(index + 1).forEach { (secondOccurrence, second) ->
            if (
                firstOccurrence.ownerProfileId == secondOccurrence.ownerProfileId &&
                first.first < second.second && second.first < first.second
            ) {
                add(CalendarConflict(firstOccurrence.id, CalendarConflictType.OVERLAP, "calendar_overlap"))
                add(CalendarConflict(secondOccurrence.id, CalendarConflictType.OVERLAP, "calendar_overlap"))
            }
        }
    }
}.distinctBy { it.occurrenceId to it.type }

private fun outsideWindow(
    occurrence: ScheduledWorkoutOccurrence,
    earliest: LocalTime?,
    latest: LocalTime?,
): Boolean {
    val start = occurrence.scheduledLocalStartTime ?: return false
    val startDateTime = occurrence.scheduledLocalDate.atTime(start)
    val endDateTime = startDateTime.plusMinutes(occurrence.plannedDurationMinutes.toLong())
    return earliest?.let { startDateTime < occurrence.scheduledLocalDate.atTime(it) } == true ||
        latest?.let { endDateTime > occurrence.scheduledLocalDate.atTime(it) } == true
}

/** DST gaps are invalid; overlaps deterministically use the earlier valid offset. */
private fun ScheduledWorkoutOccurrence.toInstantInterval(): Pair<java.time.Instant, java.time.Instant>? {
    val time = scheduledLocalStartTime
    val zone = ZoneId.of(timeZoneId)
    val local = time?.let { LocalDateTime.of(scheduledLocalDate, it) }
    val offset = local?.let(zone.rules::getValidOffsets)?.firstOrNull()
    return if (local == null || offset == null) {
        null
    } else {
        val start = ZonedDateTime.ofLocal(local, zone, offset).toInstant()
        start to start.plusSeconds(plannedDurationMinutes * 60L)
    }
}

class MaterializeScheduleUseCase(private val repository: TrainingCalendarRepository) {
    suspend operator fun invoke(schedule: PlanSchedule): List<ScheduledWorkoutOccurrence> {
        validateSchedule(schedule)
        val saved = repository.saveSchedule(schedule)
        return repository.materialize(saved.id, saved.startDate.plusDays(MATERIALIZATION_HORIZON_DAYS - 1))
    }
}

class EnsureCalendarHorizonUseCase(private val repository: TrainingCalendarRepository) {
    suspend operator fun invoke(scheduleId: String, today: LocalDate): List<ScheduledWorkoutOccurrence> =
        repository.ensureHorizon(
            scheduleId = scheduleId,
            from = today,
            through = today.plusDays(MATERIALIZATION_HORIZON_DAYS - 1),
        )
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
            originalScheduledStartTime = occurrence.originalScheduledStartTime ?: occurrence.scheduledLocalStartTime,
            origin = OccurrenceOrigin.MOVED_ONCE,
            isDetachedOverride = true,
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
        locations: List<at.fitnessplatform.core.model.TrainingLocation> = emptyList(),
    ): List<CalendarConflict> {
        return detectCalendarConflicts(
            occurrences,
            availability,
            overrides,
            locations.associate { it.id to it.availableEquipment },
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

data class ScheduleSetupPreview(
    val startDate: LocalDate,
    val timeZoneId: String,
    val drafts: List<ScheduleRuleDraft>,
    val occurrences: List<ScheduledWorkoutOccurrence>,
    val conflicts: List<CalendarConflict>,
)

class PreviewPlanScheduleUseCase(private val clock: Clock) {
    operator fun invoke(
        plan: TrainingPlan,
        startDate: LocalDate,
        timeZoneId: String,
        drafts: List<ScheduleRuleDraft>,
        availability: List<AvailabilityRule>,
        overrides: List<ScheduleOverride>,
        locations: List<at.fitnessplatform.core.model.TrainingLocation>,
    ): ScheduleSetupPreview {
        val schedule = buildPlanSchedule(
            plan = plan,
            startDate = startDate,
            timeZoneId = timeZoneId,
            drafts = drafts,
            scheduleId = "schedule-preview",
            nowEpochMs = clock.nowEpochMs(),
            ruleId = { index -> "schedule-preview-rule-$index" },
        )
        val occurrences = materializeScheduleRange(
            schedule = schedule,
            plan = plan,
            from = startDate,
            through = startDate.plusDays(MATERIALIZATION_HORIZON_DAYS - 1),
            nowEpochMs = clock.nowEpochMs(),
            idFor = { ruleId, date -> "schedule-preview-occurrence-$ruleId-$date" },
        )
        return ScheduleSetupPreview(
            startDate = startDate,
            timeZoneId = timeZoneId,
            drafts = drafts,
            occurrences = occurrences,
            conflicts = detectCalendarConflicts(
                occurrences,
                availability,
                overrides,
                locations.associate { it.id to it.availableEquipment },
            ),
        )
    }
}

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
        val schedule = buildPlanSchedule(
            plan = plan,
            startDate = startDate,
            timeZoneId = timeZoneId,
            drafts = drafts,
            scheduleId = scheduleId,
            nowEpochMs = now,
            ruleId = { ids.newUuid() },
        )
        val saved = repository.saveSchedule(schedule)
        return repository.materialize(saved.id, startDate.plusDays(MATERIALIZATION_HORIZON_DAYS - 1))
    }
}

private fun buildPlanSchedule(
    plan: TrainingPlan,
    startDate: LocalDate,
    timeZoneId: String,
    drafts: List<ScheduleRuleDraft>,
    scheduleId: String,
    nowEpochMs: Long,
    ruleId: (Int) -> String,
): PlanSchedule {
    val planDayIds = plan.weeks.flatMap { it.days }.mapTo(linkedSetOf()) { it.id }
    ensureCalendar(planDayIds.isNotEmpty()) { "A schedule requires at least one plan day." }
    ensureCalendar(drafts.map { it.planDayId }.toSet() == planDayIds) {
        "Schedule setup must explicitly configure every plan day exactly once."
    }
    ensureCalendar(drafts.size == planDayIds.size) { "Schedule setup contains duplicate plan-day rules." }
    return PlanSchedule(
        id = scheduleId,
        ownerProfileId = plan.ownerProfileId,
        planId = plan.id,
        startDate = startDate,
        timeZoneId = timeZoneId,
        isActive = true,
        createdAtEpochMs = nowEpochMs,
        updatedAtEpochMs = nowEpochMs,
        rules = drafts.mapIndexed { index, draft ->
            PlanDayScheduleRule(
                id = ruleId(index),
                scheduleId = scheduleId,
                planDayId = draft.planDayId,
                dayOfWeek = draft.dayOfWeek,
                defaultStartTime = draft.startTime,
                defaultDurationMinutes = draft.durationMinutes,
                preferredLocationId = draft.locationId,
                position = index,
            )
        },
    ).also(::validateSchedule)
}

class UpdateScheduleRuleUseCase(
    private val repository: TrainingCalendarRepository,
) {
    suspend fun preview(
        schedule: PlanSchedule,
        planDayId: String,
        effectiveDate: LocalDate,
        time: LocalTime?,
        durationMinutes: Int,
        locationId: String?,
    ): FutureScheduleChangePreview {
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
        val through = effectiveDate.plusDays(MATERIALIZATION_HORIZON_DAYS - 1)
        return FutureScheduleChangePreview(
            updatedSchedule = updated,
            effectiveDate = effectiveDate,
            through = through,
            affectedOccurrenceIds = repository.generatedOccurrences(
                schedule.id,
                effectiveDate,
                through,
            ).map { it.id },
        )
    }

    suspend fun confirm(preview: FutureScheduleChangePreview): List<ScheduledWorkoutOccurrence> {
        validateSchedule(preview.updatedSchedule)
        val saved = repository.saveSchedule(preview.updatedSchedule)
        return repository.replaceFuturePlanned(saved.id, preview.effectiveDate, preview.through)
    }
}

data class FutureScheduleChangePreview(
    val updatedSchedule: PlanSchedule,
    val effectiveDate: LocalDate,
    val through: LocalDate,
    val affectedOccurrenceIds: List<String>,
)

private fun validZone(value: String, label: String) {
    runCatching { ZoneId.of(value) }
        .getOrElse { throw ValidationException("$label requires a valid IANA time zone.") }
}

private inline fun ensureCalendar(condition: Boolean, message: () -> String) {
    if (!condition) throw ValidationException(message())
}
