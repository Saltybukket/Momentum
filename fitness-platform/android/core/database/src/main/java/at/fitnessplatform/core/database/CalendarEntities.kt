package at.fitnessplatform.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "plan_schedules",
    foreignKeys = [
        ForeignKey(entity = GuestProfileEntity::class, parentColumns = ["id"], childColumns = ["ownerProfileId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TrainingPlanEntity::class, parentColumns = ["id"], childColumns = ["planId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("ownerProfileId"), Index("planId"), Index(value = ["activeSlot"], unique = true)],
)
data class PlanScheduleEntity(
    @PrimaryKey val id: String,
    val ownerProfileId: String,
    val planId: String,
    val startDate: String,
    val timeZoneId: String,
    val isActive: Boolean,
    val activeSlot: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val revision: Long,
)

@Entity(
    tableName = "plan_day_schedule_rules",
    primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(entity = PlanScheduleEntity::class, parentColumns = ["id"], childColumns = ["scheduleId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PlanDayEntity::class, parentColumns = ["id"], childColumns = ["planDayId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = TrainingLocationEntity::class, parentColumns = ["id"], childColumns = ["preferredLocationId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index("scheduleId"), Index("planDayId"), Index("preferredLocationId"),
        Index(value = ["scheduleId", "planDayId"], unique = true),
        Index(value = ["scheduleId", "position"], unique = true),
    ],
)
data class PlanDayScheduleRuleEntity(
    val id: String,
    val scheduleId: String,
    val planDayId: String,
    val dayOfWeek: Int,
    val defaultStartTime: String?,
    val defaultDurationMinutes: Int,
    val preferredLocationId: String?,
    val position: Int,
)

@Entity(
    tableName = "scheduled_workout_occurrences",
    foreignKeys = [
        ForeignKey(entity = GuestProfileEntity::class, parentColumns = ["id"], childColumns = ["ownerProfileId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PlanScheduleEntity::class, parentColumns = ["id"], childColumns = ["scheduleId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = TrainingPlanEntity::class, parentColumns = ["id"], childColumns = ["planId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = PlanDayEntity::class, parentColumns = ["id"], childColumns = ["planDayId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = TrainingLocationEntity::class, parentColumns = ["id"], childColumns = ["trainingLocationId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index("ownerProfileId"), Index("scheduleId"), Index("planId"), Index("planDayId"), Index("trainingLocationId"),
        Index(value = ["ownerProfileId", "scheduledLocalDate"]),
        Index(value = ["scheduleId", "planDayIdSnapshot", "scheduledLocalDate"], unique = true),
    ],
)
data class ScheduledWorkoutOccurrenceEntity(
    @PrimaryKey val id: String,
    val ownerProfileId: String,
    val scheduleId: String?,
    val planId: String?,
    val planDayId: String?,
    val planDayIdSnapshot: String?,
    val planRevisionSnapshot: Long,
    val planWeekIndexSnapshot: Int?,
    val requiredEquipmentSnapshotJson: String,
    val hasUnavailableExerciseSnapshot: Boolean,
    val titleSnapshot: String,
    val scheduledLocalDate: String,
    val scheduledLocalStartTime: String?,
    val timeZoneId: String,
    val plannedDurationMinutes: Int,
    val trainingLocationId: String?,
    val status: String,
    val originalScheduledDate: String?,
    val originalScheduledStartTime: String?,
    val movedFromOccurrenceId: String?,
    val originType: String,
    val isDetachedOverride: Boolean,
    val sourceOccurrenceId: String?,
    val notes: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val revision: Long,
    val deletedAtEpochMs: Long?,
)

@Entity(
    tableName = "availability_rules",
    foreignKeys = [
        ForeignKey(entity = GuestProfileEntity::class, parentColumns = ["id"], childColumns = ["ownerProfileId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TrainingLocationEntity::class, parentColumns = ["id"], childColumns = ["preferredLocationId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("ownerProfileId"), Index("preferredLocationId"), Index(value = ["ownerProfileId", "dayOfWeek"], unique = true)],
)
data class AvailabilityRuleEntity(
    @PrimaryKey val id: String,
    val ownerProfileId: String,
    val dayOfWeek: Int,
    val earliestLocalTime: String?,
    val latestLocalTime: String?,
    val maxDurationMinutes: Int?,
    val preferredLocationId: String?,
    val enabled: Boolean,
)

@Entity(
    tableName = "schedule_overrides",
    foreignKeys = [
        ForeignKey(entity = GuestProfileEntity::class, parentColumns = ["id"], childColumns = ["ownerProfileId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TrainingLocationEntity::class, parentColumns = ["id"], childColumns = ["locationId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("ownerProfileId"), Index("locationId"), Index(value = ["ownerProfileId", "localDate"], unique = true)],
)
data class ScheduleOverrideEntity(
    @PrimaryKey val id: String,
    val ownerProfileId: String,
    val localDate: String,
    val unavailable: Boolean,
    val earliestLocalTime: String?,
    val latestLocalTime: String?,
    val maxDurationMinutes: Int?,
    val locationId: String?,
    val note: String?,
)
