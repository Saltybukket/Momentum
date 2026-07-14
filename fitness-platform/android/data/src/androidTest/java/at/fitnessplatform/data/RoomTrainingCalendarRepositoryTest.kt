package at.fitnessplatform.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import at.fitnessplatform.core.database.*
import at.fitnessplatform.core.model.*
import at.fitnessplatform.domain.MoveOccurrenceUseCase
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomTrainingCalendarRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: RoomTrainingCalendarRepository
    private var nextId = 0
    private var now = 100L

    @Before fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        database.guestProfileDao().insert(GuestProfile("profile", "Guest", 1).toEntity())
        database.trainingPlanDao().insertPlan(
            TrainingPlanEntity("plan", "profile", "Plan", "", "STRENGTH", true, "profile", false, null, 1, 1, 0, null),
        )
        database.trainingPlanDao().insertWeeks(listOf(PlanWeekEntity("week", "plan", 0, "Week", 0)))
        database.trainingPlanDao().insertDays(
            listOf(
                PlanDayEntity("day-a", "week", 0, "Morning", 0, 60, ""),
                PlanDayEntity("day-b", "week", 1, "Evening", 1, 60, ""),
            ),
        )
        repository = RoomTrainingCalendarRepository(
            database,
            database.guestProfileDao(),
            database.trainingPlanDao(),
            database.calendarDao(),
            object : UuidProvider { override fun newUuid() = "id-${nextId++}" },
            object : Clock { override fun nowEpochMs() = now++ },
        )
    }

    @After fun tearDown() = database.close()

    @Test fun materializationIsAtomicIdempotentAndAllowsTwoSessionsPerDay() = runTest {
        val schedule = repository.saveSchedule(schedule())
        val first = repository.materialize(schedule.id, LocalDate.of(2026, 7, 20))
        val second = repository.materialize(schedule.id, LocalDate.of(2026, 7, 20))
        assertEquals(first.map { it.id }, second.map { it.id })
        assertEquals(4, second.size)
        assertEquals(2, second.groupBy { it.scheduledLocalDate }.values.first().size)
    }

    @Test fun explicitFutureReplacementKeepsCompletedRowsAndCopyIsAdHoc() = runTest {
        val schedule = repository.saveSchedule(schedule())
        val rows = repository.materialize(schedule.id, LocalDate.of(2026, 7, 20))
        database.calendarDao().changeStatus(rows.first().id, "profile", ScheduledWorkoutStatus.COMPLETED.name, 200)

        val replaced = repository.replaceFuturePlanned(
            schedule.id,
            LocalDate.of(2026, 7, 13),
            LocalDate.of(2026, 7, 20),
        )
        assertTrue(replaced.any { it.id == rows.first().id && it.status == ScheduledWorkoutStatus.COMPLETED })
        val copied = repository.copyOccurrence(replaced.first { it.status == ScheduledWorkoutStatus.PLANNED }.id)
        assertEquals(null, copied.scheduleId)
        assertEquals(ScheduledWorkoutStatus.PLANNED, copied.status)
        assertTrue(repository.observeOccurrences(LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 20)).first().size > replaced.size)
    }

    @Test fun rollingHorizonRecoversGapsAndPreservesMovedCopiedAdHocAndHistory() = runTest {
        val schedule = repository.saveSchedule(schedule())
        val from = LocalDate.of(2026, 7, 13)
        val initial = repository.ensureHorizon(schedule.id, from, from.plusDays(7))
        val movedSource = initial.first()
        val moved = MoveOccurrenceUseCase(repository)(
            movedSource,
            movedSource.scheduledLocalDate.plusDays(1),
            movedSource.scheduledLocalStartTime,
            movedSource.plannedDurationMinutes,
            null,
        )
        val copied = repository.copyOccurrence(initial.last().id)
        val adHoc = repository.saveOccurrence(
            ScheduledWorkoutOccurrence(
                id = "ad-hoc",
                ownerProfileId = "profile",
                titleSnapshot = "Ad hoc",
                scheduledLocalDate = from.plusDays(2),
                timeZoneId = "Europe/Berlin",
                plannedDurationMinutes = 30,
                createdAtEpochMs = 1,
                updatedAtEpochMs = 1,
            ),
        )
        database.calendarDao().changeStatus(initial[1].id, "profile", ScheduledWorkoutStatus.COMPLETED.name, 200)

        val repeated = repository.ensureHorizon(schedule.id, from, from.plusDays(7))
        assertEquals(6, repeated.size)
        database.openHelper.writableDatabase.execSQL(
            "DELETE FROM scheduled_workout_occurrences WHERE id = '${initial[2].id}'",
        )
        val recovered = repository.ensureHorizon(schedule.id, from, from.plusDays(14))
        assertTrue(recovered.any { it.id == initial[2].id })

        val replaced = repository.replaceFuturePlanned(schedule.id, from, from.plusDays(14))
        assertTrue(replaced.any { it.id == moved.id && it.origin == OccurrenceOrigin.MOVED_ONCE })
        assertTrue(replaced.any { it.id == copied.id && it.origin == OccurrenceOrigin.COPIED })
        assertTrue(replaced.any { it.id == adHoc.id && it.origin == OccurrenceOrigin.AD_HOC })
        assertTrue(replaced.any { it.id == initial[1].id && it.status == ScheduledWorkoutStatus.COMPLETED })
        assertEquals(replaced.map { it.id }.toSet().size, replaced.size)
    }

    private fun schedule() = PlanSchedule(
        "schedule",
        "profile",
        "plan",
        LocalDate.of(2026, 7, 13),
        "Europe/Berlin",
        true,
        0,
        0,
        rules = listOf(
            PlanDayScheduleRule("rule-a", "schedule", "day-a", DayOfWeek.MONDAY, LocalTime.of(8, 0), 60, null, 0),
            PlanDayScheduleRule("rule-b", "schedule", "day-b", DayOfWeek.MONDAY, LocalTime.of(18, 0), 60, null, 1),
        ),
    )
}
