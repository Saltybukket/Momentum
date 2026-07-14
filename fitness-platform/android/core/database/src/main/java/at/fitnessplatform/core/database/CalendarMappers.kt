package at.fitnessplatform.core.database

import at.fitnessplatform.core.model.AvailabilityRule
import at.fitnessplatform.core.model.PlanDayScheduleRule
import at.fitnessplatform.core.model.PlanSchedule
import at.fitnessplatform.core.model.OccurrenceOrigin
import at.fitnessplatform.core.model.ScheduleOverride
import at.fitnessplatform.core.model.ScheduledWorkoutOccurrence
import at.fitnessplatform.core.model.ScheduledWorkoutStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.serialization.json.Json

private val calendarSnapshotJson = Json { ignoreUnknownKeys = false }

fun PlanScheduleWithRules.toModel() = PlanSchedule(
    id = schedule.id,
    ownerProfileId = schedule.ownerProfileId,
    planId = schedule.planId,
    startDate = LocalDate.parse(schedule.startDate),
    timeZoneId = schedule.timeZoneId,
    isActive = schedule.isActive,
    createdAtEpochMs = schedule.createdAtEpochMs,
    updatedAtEpochMs = schedule.updatedAtEpochMs,
    revision = schedule.revision,
    rules = rules.sortedBy { it.position }.map(PlanDayScheduleRuleEntity::toModel),
)

fun PlanSchedule.toEntity() = PlanScheduleEntity(
    id = id,
    ownerProfileId = ownerProfileId,
    planId = planId,
    startDate = startDate.toString(),
    timeZoneId = timeZoneId,
    isActive = isActive,
    activeSlot = ownerProfileId.takeIf { isActive },
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
    revision = revision,
)

fun PlanDayScheduleRule.toEntity() = PlanDayScheduleRuleEntity(
    id,
    scheduleId,
    planDayId,
    dayOfWeek.value,
    defaultStartTime?.toString(),
    defaultDurationMinutes,
    preferredLocationId,
    position,
)

private fun PlanDayScheduleRuleEntity.toModel() = PlanDayScheduleRule(
    id,
    scheduleId,
    planDayId,
    DayOfWeek.of(dayOfWeek),
    defaultStartTime?.let(LocalTime::parse),
    defaultDurationMinutes,
    preferredLocationId,
    position,
)

fun ScheduledWorkoutOccurrenceEntity.toModel() = ScheduledWorkoutOccurrence(
    id = id,
    ownerProfileId = ownerProfileId,
    scheduleId = scheduleId,
    planId = planId,
    planDayId = planDayId,
    planDayIdSnapshot = planDayIdSnapshot,
    planRevisionSnapshot = planRevisionSnapshot,
    planWeekIndexSnapshot = planWeekIndexSnapshot,
    requiredEquipmentSnapshot = calendarSnapshotJson.decodeFromString<List<String>>(
        requiredEquipmentSnapshotJson,
    ).toSortedSet(),
    hasUnavailableExerciseSnapshot = hasUnavailableExerciseSnapshot,
    titleSnapshot = titleSnapshot,
    scheduledLocalDate = LocalDate.parse(scheduledLocalDate),
    scheduledLocalStartTime = scheduledLocalStartTime?.let(LocalTime::parse),
    timeZoneId = timeZoneId,
    plannedDurationMinutes = plannedDurationMinutes,
    trainingLocationId = trainingLocationId,
    status = ScheduledWorkoutStatus.valueOf(status),
    originalScheduledDate = originalScheduledDate?.let(LocalDate::parse),
    originalScheduledStartTime = originalScheduledStartTime?.let(LocalTime::parse),
    movedFromOccurrenceId = movedFromOccurrenceId,
    origin = OccurrenceOrigin.valueOf(originType),
    isDetachedOverride = isDetachedOverride,
    sourceOccurrenceId = sourceOccurrenceId,
    notes = notes,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
    revision = revision,
    deletedAtEpochMs = deletedAtEpochMs,
)

fun ScheduledWorkoutOccurrence.toEntity() = ScheduledWorkoutOccurrenceEntity(
    id,
    ownerProfileId,
    scheduleId,
    planId,
    planDayId,
    planDayIdSnapshot,
    planRevisionSnapshot,
    planWeekIndexSnapshot,
    calendarSnapshotJson.encodeToString(requiredEquipmentSnapshot.toSortedSet().toList()),
    hasUnavailableExerciseSnapshot,
    titleSnapshot,
    scheduledLocalDate.toString(),
    scheduledLocalStartTime?.toString(),
    timeZoneId,
    plannedDurationMinutes,
    trainingLocationId,
    status.name,
    originalScheduledDate?.toString(),
    originalScheduledStartTime?.toString(),
    movedFromOccurrenceId,
    origin.name,
    isDetachedOverride,
    sourceOccurrenceId,
    notes,
    createdAtEpochMs,
    updatedAtEpochMs,
    revision,
    deletedAtEpochMs,
)

fun AvailabilityRuleEntity.toModel() = AvailabilityRule(
    id,
    ownerProfileId,
    DayOfWeek.of(dayOfWeek),
    earliestLocalTime?.let(LocalTime::parse),
    latestLocalTime?.let(LocalTime::parse),
    maxDurationMinutes,
    preferredLocationId,
    enabled,
)

fun AvailabilityRule.toEntity() = AvailabilityRuleEntity(
    id,
    ownerProfileId,
    dayOfWeek.value,
    earliestLocalTime?.toString(),
    latestLocalTime?.toString(),
    maxDurationMinutes,
    preferredLocationId,
    enabled,
)

fun ScheduleOverrideEntity.toModel() = ScheduleOverride(
    id,
    ownerProfileId,
    LocalDate.parse(localDate),
    unavailable,
    earliestLocalTime?.let(LocalTime::parse),
    latestLocalTime?.let(LocalTime::parse),
    maxDurationMinutes,
    locationId,
    note,
)

fun ScheduleOverride.toEntity() = ScheduleOverrideEntity(
    id,
    ownerProfileId,
    localDate.toString(),
    unavailable,
    earliestLocalTime?.toString(),
    latestLocalTime?.toString(),
    maxDurationMinutes,
    locationId,
    note,
)
