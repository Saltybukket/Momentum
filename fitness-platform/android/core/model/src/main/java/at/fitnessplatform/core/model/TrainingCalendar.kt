package at.fitnessplatform.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

data class PlanSchedule(
    val id: String,
    val ownerProfileId: String,
    val planId: String,
    val startDate: LocalDate,
    val timeZoneId: String,
    val isActive: Boolean,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val revision: Long = 0,
    val rules: List<PlanDayScheduleRule> = emptyList(),
)

data class PlanDayScheduleRule(
    val id: String,
    val scheduleId: String,
    val planDayId: String,
    val dayOfWeek: DayOfWeek,
    val defaultStartTime: LocalTime?,
    val defaultDurationMinutes: Int,
    val preferredLocationId: String?,
    val position: Int,
)

enum class ScheduledWorkoutStatus { PLANNED, IN_PROGRESS, COMPLETED, SKIPPED, MOVED, CANCELLED, CONFLICT }

data class ScheduledWorkoutOccurrence(
    val id: String,
    val ownerProfileId: String,
    val scheduleId: String? = null,
    val planId: String? = null,
    val planDayId: String? = null,
    val titleSnapshot: String,
    val scheduledLocalDate: LocalDate,
    val scheduledLocalStartTime: LocalTime? = null,
    val timeZoneId: String,
    val plannedDurationMinutes: Int,
    val trainingLocationId: String? = null,
    val status: ScheduledWorkoutStatus = ScheduledWorkoutStatus.PLANNED,
    val originalScheduledDate: LocalDate? = null,
    val movedFromOccurrenceId: String? = null,
    val notes: String = "",
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val revision: Long = 0,
    val deletedAtEpochMs: Long? = null,
)

data class AvailabilityRule(
    val id: String,
    val ownerProfileId: String,
    val dayOfWeek: DayOfWeek,
    val earliestLocalTime: LocalTime? = null,
    val latestLocalTime: LocalTime? = null,
    val maxDurationMinutes: Int? = null,
    val preferredLocationId: String? = null,
    val enabled: Boolean = true,
)

data class ScheduleOverride(
    val id: String,
    val ownerProfileId: String,
    val localDate: LocalDate,
    val unavailable: Boolean,
    val earliestLocalTime: LocalTime? = null,
    val latestLocalTime: LocalTime? = null,
    val maxDurationMinutes: Int? = null,
    val locationId: String? = null,
    val note: String? = null,
)

enum class CalendarConflictType {
    OUTSIDE_AVAILABILITY,
    DURATION_EXCEEDED,
    LOCATION_REQUIRED,
    MISSING_EQUIPMENT,
    OVERLAP,
    UNAVAILABLE_EXERCISE,
}

data class CalendarConflict(
    val occurrenceId: String,
    val type: CalendarConflictType,
    val messageKey: String,
)
