package at.fitnessplatform.data

import androidx.room.withTransaction
import at.fitnessplatform.core.database.AppDatabase
import at.fitnessplatform.core.database.CalendarDao
import at.fitnessplatform.core.database.GuestProfileDao
import at.fitnessplatform.core.database.TrainingPlanDao
import at.fitnessplatform.core.database.toEntity
import at.fitnessplatform.core.database.toModel
import at.fitnessplatform.core.model.AvailabilityRule
import at.fitnessplatform.core.model.Clock
import at.fitnessplatform.core.model.PlanSchedule
import at.fitnessplatform.core.model.ScheduleOverride
import at.fitnessplatform.core.model.ScheduledWorkoutOccurrence
import at.fitnessplatform.core.model.ScheduledWorkoutStatus
import at.fitnessplatform.core.model.UuidProvider
import at.fitnessplatform.domain.TrainingCalendarRepository
import at.fitnessplatform.domain.materializeSchedule
import at.fitnessplatform.domain.validateAvailability
import at.fitnessplatform.domain.validateOccurrence
import at.fitnessplatform.domain.validateOverride
import at.fitnessplatform.domain.validateSchedule
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class RoomTrainingCalendarRepository @Inject constructor(
    private val database: AppDatabase,
    private val profileDao: GuestProfileDao,
    private val planDao: TrainingPlanDao,
    private val calendarDao: CalendarDao,
    private val ids: UuidProvider,
    private val clock: Clock,
) : TrainingCalendarRepository {
    override fun observeOccurrences(
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<ScheduledWorkoutOccurrence>> = profileDao.observe().flatMapLatest { profile ->
        if (profile == null) flowOf(emptyList())
        else calendarDao.observeOccurrences(profile.id, from.toString(), to.toString())
            .map { rows -> rows.map { it.toModel() } }
    }

    override fun observeActiveSchedule(): Flow<PlanSchedule?> = profileDao.observe().flatMapLatest { profile ->
        if (profile == null) flowOf(null)
        else calendarDao.observeActiveSchedule(profile.id).map { it?.toModel() }
    }

    override fun observeAvailability(): Flow<List<AvailabilityRule>> = profileDao.observe().flatMapLatest { profile ->
        if (profile == null) flowOf(emptyList())
        else calendarDao.observeAvailability(profile.id).map { rows -> rows.map { it.toModel() } }
    }

    override fun observeOverrides(): Flow<List<ScheduleOverride>> = profileDao.observe().flatMapLatest { profile ->
        if (profile == null) flowOf(emptyList())
        else calendarDao.observeOverrides(profile.id).map { rows -> rows.map { it.toModel() } }
    }

    override suspend fun saveSchedule(schedule: PlanSchedule): PlanSchedule {
        validateSchedule(schedule)
        val owner = requireNotNull(profileDao.get()) { "Create a guest profile before scheduling a plan." }
        require(schedule.ownerProfileId == owner.id) { "Schedules must belong to the current profile." }
        val plan = requireNotNull(planDao.get(schedule.planId, owner.id)?.toModel()) { "Training plan does not exist." }
        val planDayIds = plan.weeks.flatMap { it.days }.mapTo(mutableSetOf()) { it.id }
        require(schedule.rules.all { it.planDayId in planDayIds }) { "Schedule references an unknown plan day." }
        val now = clock.nowEpochMs()
        return database.withTransaction {
            val current = calendarDao.getSchedule(schedule.id, owner.id)?.toModel()
            val saved = schedule.copy(
                createdAtEpochMs = current?.createdAtEpochMs ?: now,
                updatedAtEpochMs = now,
                revision = current?.revision?.plus(1) ?: 0,
            )
            calendarDao.saveSchedule(saved.toEntity(), saved.rules.map { it.toEntity() })
            saved
        }
    }

    override suspend fun materialize(
        scheduleId: String,
        through: LocalDate,
    ): List<ScheduledWorkoutOccurrence> = database.withTransaction {
        materializeInTransaction(scheduleId, through)
    }

    override suspend fun replaceFuturePlanned(
        scheduleId: String,
        from: LocalDate,
        through: LocalDate,
    ): List<ScheduledWorkoutOccurrence> = database.withTransaction {
        val owner = requireNotNull(profileDao.get()) { "Guest profile does not exist." }
        calendarDao.deleteFuturePlanningRows(owner.id, scheduleId, from.toString(), through.toString())
        materializeInTransaction(scheduleId, through)
    }

    override suspend fun saveOccurrence(
        occurrence: ScheduledWorkoutOccurrence,
    ): ScheduledWorkoutOccurrence {
        validateOccurrence(occurrence)
        val owner = requireNotNull(profileDao.get()) { "Guest profile does not exist." }
        require(occurrence.ownerProfileId == owner.id) { "Occurrences must belong to the current profile." }
        return database.withTransaction {
            val current = calendarDao.getOccurrence(occurrence.id, owner.id)?.toModel()
            check(
                current?.status !in setOf(
                    ScheduledWorkoutStatus.IN_PROGRESS,
                    ScheduledWorkoutStatus.COMPLETED,
                    ScheduledWorkoutStatus.SKIPPED,
                    ScheduledWorkoutStatus.CANCELLED,
                ),
            ) {
                "Terminal occurrences cannot be changed."
            }
            val saved = occurrence.copy(
                createdAtEpochMs = current?.createdAtEpochMs ?: clock.nowEpochMs(),
                updatedAtEpochMs = clock.nowEpochMs(),
                revision = current?.revision?.plus(1) ?: 0,
            )
            if (current == null) {
                check(calendarDao.insertOccurrences(listOf(saved.toEntity())).single() != -1L) {
                    "Occurrence already exists."
                }
            } else {
                check(calendarDao.updateOccurrence(saved.toEntity()) == 1)
            }
            saved
        }
    }

    override suspend fun copyOccurrence(id: String): ScheduledWorkoutOccurrence {
        val owner = requireNotNull(profileDao.get()) { "Guest profile does not exist." }
        val source = requireNotNull(calendarDao.getOccurrence(id, owner.id)?.toModel()) { "Occurrence does not exist." }
        check(source.status !in setOf(ScheduledWorkoutStatus.IN_PROGRESS, ScheduledWorkoutStatus.COMPLETED)) {
            "Running or completed occurrences cannot be copied."
        }
        val now = clock.nowEpochMs()
        return saveOccurrence(
            source.copy(
                id = ids.newUuid(),
                scheduleId = null,
                status = ScheduledWorkoutStatus.PLANNED,
                movedFromOccurrenceId = source.id,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
                revision = 0,
            ),
        )
    }

    override suspend fun changeStatus(id: String, status: ScheduledWorkoutStatus) {
        val owner = requireNotNull(profileDao.get()) { "Guest profile does not exist." }
        check(calendarDao.changeStatus(id, owner.id, status.name, clock.nowEpochMs()) == 1) {
            "Occurrence is terminal or does not exist."
        }
    }

    override suspend fun saveAvailability(rule: AvailabilityRule) {
        validateAvailability(rule)
        val owner = requireNotNull(profileDao.get()) { "Guest profile does not exist." }
        require(rule.ownerProfileId == owner.id) { "Availability must belong to the current profile." }
        calendarDao.upsertAvailability(rule.toEntity())
    }

    override suspend fun saveOverride(override: ScheduleOverride) {
        validateOverride(override)
        val owner = requireNotNull(profileDao.get()) { "Guest profile does not exist." }
        require(override.ownerProfileId == owner.id) { "Overrides must belong to the current profile." }
        calendarDao.upsertOverride(override.toEntity())
    }

    private suspend fun materializeInTransaction(
        scheduleId: String,
        through: LocalDate,
    ): List<ScheduledWorkoutOccurrence> {
        val owner = requireNotNull(profileDao.get()) { "Guest profile does not exist." }
        val schedule = requireNotNull(calendarDao.getSchedule(scheduleId, owner.id)?.toModel()) {
            "Schedule does not exist."
        }
        val plan = requireNotNull(planDao.get(schedule.planId, owner.id)?.toModel()) { "Training plan does not exist." }
        val rows = materializeSchedule(schedule, plan, through, clock.nowEpochMs(), ::stableOccurrenceId)
        if (rows.isNotEmpty()) calendarDao.insertOccurrences(rows.map { it.toEntity() })
        return calendarDao.getScheduleOccurrences(
            owner.id,
            scheduleId,
            schedule.startDate.toString(),
            through.toString(),
        ).map { it.toModel() }
    }

    private fun stableOccurrenceId(ruleId: String, date: LocalDate): String = UUID.nameUUIDFromBytes(
        "calendar:$ruleId:$date".toByteArray(StandardCharsets.UTF_8),
    ).toString()
}
