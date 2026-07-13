package at.fitnessplatform.core.database

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class PlanScheduleWithRules(
    @Embedded val schedule: PlanScheduleEntity,
    @Relation(parentColumn = "id", entityColumn = "scheduleId")
    val rules: List<PlanDayScheduleRuleEntity>,
)

@Dao
interface CalendarDao {
    @Transaction
    @Query(
        "SELECT * FROM plan_schedules WHERE ownerProfileId = :ownerProfileId " +
            "AND activeSlot = :ownerProfileId LIMIT 1",
    )
    fun observeActiveSchedule(ownerProfileId: String): Flow<PlanScheduleWithRules?>

    @Transaction
    @Query("SELECT * FROM plan_schedules WHERE id = :id AND ownerProfileId = :ownerProfileId LIMIT 1")
    suspend fun getSchedule(id: String, ownerProfileId: String): PlanScheduleWithRules?

    @Query(
        "SELECT * FROM scheduled_workout_occurrences WHERE ownerProfileId = :ownerProfileId " +
            "AND deletedAtEpochMs IS NULL AND scheduledLocalDate BETWEEN :from AND :to " +
            "ORDER BY scheduledLocalDate, scheduledLocalStartTime, createdAtEpochMs, id",
    )
    fun observeOccurrences(
        ownerProfileId: String,
        from: String,
        to: String,
    ): Flow<List<ScheduledWorkoutOccurrenceEntity>>

    @Query(
        "SELECT * FROM scheduled_workout_occurrences WHERE ownerProfileId = :ownerProfileId " +
            "AND id = :id AND deletedAtEpochMs IS NULL LIMIT 1",
    )
    suspend fun getOccurrence(id: String, ownerProfileId: String): ScheduledWorkoutOccurrenceEntity?

    @Query(
        "SELECT * FROM scheduled_workout_occurrences WHERE ownerProfileId = :ownerProfileId " +
            "AND scheduleId = :scheduleId AND deletedAtEpochMs IS NULL " +
            "AND scheduledLocalDate BETWEEN :from AND :to",
    )
    suspend fun getScheduleOccurrences(
        ownerProfileId: String,
        scheduleId: String,
        from: String,
        to: String,
    ): List<ScheduledWorkoutOccurrenceEntity>

    @Query("SELECT * FROM availability_rules WHERE ownerProfileId = :ownerProfileId ORDER BY dayOfWeek")
    fun observeAvailability(ownerProfileId: String): Flow<List<AvailabilityRuleEntity>>

    @Query("SELECT * FROM schedule_overrides WHERE ownerProfileId = :ownerProfileId ORDER BY localDate")
    fun observeOverrides(ownerProfileId: String): Flow<List<ScheduleOverrideEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSchedule(schedule: PlanScheduleEntity)

    @Update
    suspend fun updateSchedule(schedule: PlanScheduleEntity): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRules(rules: List<PlanDayScheduleRuleEntity>)

    @Query("DELETE FROM plan_day_schedule_rules WHERE scheduleId = :scheduleId")
    suspend fun deleteRules(scheduleId: String)

    @Query(
        "UPDATE plan_schedules SET isActive = 0, activeSlot = NULL, updatedAtEpochMs = :now, " +
            "revision = revision + 1 WHERE ownerProfileId = :ownerProfileId " +
            "AND activeSlot = :ownerProfileId AND id != :nextId",
    )
    suspend fun clearActive(ownerProfileId: String, nextId: String, now: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOccurrences(rows: List<ScheduledWorkoutOccurrenceEntity>): List<Long>

    @Update
    suspend fun updateOccurrence(row: ScheduledWorkoutOccurrenceEntity): Int

    @Query(
        "UPDATE scheduled_workout_occurrences SET status = :status, updatedAtEpochMs = :now, " +
            "revision = revision + 1 WHERE id = :id AND ownerProfileId = :ownerProfileId " +
            "AND deletedAtEpochMs IS NULL " +
            "AND status NOT IN ('IN_PROGRESS', 'COMPLETED', 'SKIPPED', 'CANCELLED')",
    )
    suspend fun changeStatus(id: String, ownerProfileId: String, status: String, now: Long): Int

    @Query(
        "DELETE FROM scheduled_workout_occurrences WHERE ownerProfileId = :ownerProfileId " +
            "AND scheduleId = :scheduleId AND scheduledLocalDate BETWEEN :from AND :to " +
            "AND status IN ('PLANNED', 'CONFLICT')",
    )
    suspend fun deleteFuturePlanningRows(ownerProfileId: String, scheduleId: String, from: String, to: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAvailability(rule: AvailabilityRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOverride(override: ScheduleOverrideEntity)

    @Transaction
    suspend fun saveSchedule(schedule: PlanScheduleEntity, rules: List<PlanDayScheduleRuleEntity>) {
        if (schedule.isActive) clearActive(schedule.ownerProfileId, schedule.id, schedule.updatedAtEpochMs)
        if (getSchedule(schedule.id, schedule.ownerProfileId) == null) insertSchedule(schedule)
        else check(updateSchedule(schedule) == 1)
        deleteRules(schedule.id)
        if (rules.isNotEmpty()) insertRules(rules)
    }
}
