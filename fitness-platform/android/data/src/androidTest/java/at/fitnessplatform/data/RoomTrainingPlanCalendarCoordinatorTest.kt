package at.fitnessplatform.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import at.fitnessplatform.core.database.AppDatabase
import at.fitnessplatform.core.database.toEntity
import at.fitnessplatform.core.model.Clock
import at.fitnessplatform.core.model.CustomExercise
import at.fitnessplatform.core.model.ExerciseReference
import at.fitnessplatform.core.model.ExerciseReferenceKind
import at.fitnessplatform.core.model.ExerciseSnapshot
import at.fitnessplatform.core.model.GuestProfile
import at.fitnessplatform.core.model.PlanBlock
import at.fitnessplatform.core.model.PlanBlockType
import at.fitnessplatform.core.model.PlanDay
import at.fitnessplatform.core.model.PlanDayScheduleRule
import at.fitnessplatform.core.model.PlanExercise
import at.fitnessplatform.core.model.PlanSchedule
import at.fitnessplatform.core.model.PlanWeek
import at.fitnessplatform.core.model.SetPrescription
import at.fitnessplatform.core.model.TrackingType
import at.fitnessplatform.core.model.TrainingPlan
import at.fitnessplatform.core.model.UuidProvider
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomTrainingPlanCalendarCoordinatorTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: RoomTrainingPlanRepository
    private lateinit var calendar: RoomTrainingCalendarRepository
    private lateinit var coordinator: RoomTrainingPlanCalendarCoordinator
    private var now = 100L
    private var sequence = 0

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        database.guestProfileDao().insert(GuestProfile("profile", "Guest", 1).toEntity())
        database.exerciseDao().insert(
            CustomExercise(
                "custom", "profile", "Squat", "", "legs", "none", TrackingType.REPS, "", 1, 1,
            ).toEntity(),
        )
        repository = RoomTrainingPlanRepository(
            database,
            database.guestProfileDao(),
            database.exerciseDao(),
            database.catalogDao(),
            database.trainingPlanDao(),
            object : UuidProvider { override fun newUuid() = "copy-${sequence++}" },
            object : Clock { override fun nowEpochMs() = now++ },
        )
        calendar = RoomTrainingCalendarRepository(
            database,
            database.guestProfileDao(),
            database.trainingPlanDao(),
            database.calendarDao(),
            object : UuidProvider { override fun newUuid() = "calendar-${sequence++}" },
            object : Clock { override fun nowEpochMs() = now++ },
        )
        coordinator = RoomTrainingPlanCalendarCoordinator(database, repository, calendar)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun activatePlanWithScheduleValidatesAndCommits() = runTest {
        val plan = repository.create(plan("plan-1", "Valid"))
        val schedule = activeScheduleFor(plan, "schedule")

        val rows = coordinator.activatePlanWithSchedule(plan.id, schedule, schedule.startDate.plusDays(7))

        assertTrue(rows.isNotEmpty())
        assertTrue(repository.getPlan(plan.id)?.isActive == true)
        assertEquals("schedule", database.calendarDao().getActiveSchedule("profile")?.schedule?.id)
        assertEquals("profile", database.calendarDao().getSchedule("schedule", "profile")?.schedule?.ownerProfileId)
    }

    @Test
    fun rejectsPlanIdMismatchAndLeavesNoTrace() = runTest {
        val plan = repository.create(plan("plan-1", "Original"))
        repository.setActive(plan.id)
        val schedule = activeScheduleFor(plan, "active-schedule")
        calendar.saveAndMaterialize(schedule, schedule.startDate.plusDays(7))
        val activeBefore = database.calendarDao().getActiveSchedule("profile")
        val planActiveBefore = repository.getPlan(plan.id)?.isActive
        val occurrencesBefore = database.calendarDao().getScheduleOccurrences(
            "profile", "active-schedule", schedule.startDate.toString(),
            schedule.startDate.plusDays(7).toString(),
        )

        val mismatched = activeScheduleFor(plan, "mismatched-schedule").copy(planId = "wrong-plan")
        val failure = runCatching {
            coordinator.activatePlanWithSchedule(plan.id, mismatched, mismatched.startDate.plusDays(7))
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(activeBefore?.schedule?.id, database.calendarDao().getActiveSchedule("profile")?.schedule?.id)
        assertEquals(null, database.calendarDao().getSchedule("mismatched-schedule", "profile"))
        assertEquals(
            occurrencesBefore.size,
            database.calendarDao().getScheduleOccurrences(
                "profile", "active-schedule", schedule.startDate.toString(),
                schedule.startDate.plusDays(7).toString(),
            ).size,
        )
        assertTrue(repository.getPlan(plan.id)?.isActive == planActiveBefore)
    }

    @Test
    fun rejectsInactiveScheduleAndLeavesNoTrace() = runTest {
        val plan = repository.create(plan("plan-1", "Original"))
        repository.setActive(plan.id)
        val activeSchedule = activeScheduleFor(plan, "active-schedule")
        calendar.saveAndMaterialize(activeSchedule, activeSchedule.startDate.plusDays(7))
        val activeBefore = database.calendarDao().getActiveSchedule("profile")

        val inactive = activeSchedule.copy(id = "inactive-schedule", isActive = false)
        val failure = runCatching {
            coordinator.activatePlanWithSchedule(plan.id, inactive, inactive.startDate.plusDays(7))
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(activeBefore?.schedule?.id, database.calendarDao().getActiveSchedule("profile")?.schedule?.id)
        assertEquals(null, database.calendarDao().getSchedule("inactive-schedule", "profile"))
        assertEquals(
            0,
            database.calendarDao().getScheduleOccurrences(
                "profile", "inactive-schedule", inactive.startDate.toString(),
                inactive.startDate.plusDays(7).toString(),
            ).size,
        )
    }

    @Test
    fun rejectsInvalidThroughDateAndLeavesNoTrace() = runTest {
        val plan = repository.create(plan("plan-1", "Original"))
        repository.setActive(plan.id)
        val schedule = activeScheduleFor(plan, "schedule")
        val activeBefore = database.calendarDao().getActiveSchedule("profile")
        val planActiveBefore = repository.getPlan(plan.id)?.isActive

        val failure = runCatching {
            coordinator.activatePlanWithSchedule(
                plan.id, schedule, schedule.startDate.minusDays(1),
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(activeBefore?.schedule?.id, database.calendarDao().getActiveSchedule("profile")?.schedule?.id)
        assertEquals(null, database.calendarDao().getSchedule("schedule", "profile"))
        assertTrue(repository.getPlan(plan.id)?.isActive == planActiveBefore)
    }

    @Test
    fun rejectsOwnerMismatchAndLeavesNoTrace() = runTest {
        val plan = repository.create(plan("plan-1", "Owned"))
        repository.setActive(plan.id)
        val schedule = activeScheduleFor(plan, "schedule")
        calendar.saveAndMaterialize(schedule, schedule.startDate.plusDays(7))
        val activeBefore = database.calendarDao().getActiveSchedule("profile")
        val planActiveBefore = repository.getPlan(plan.id)?.isActive

        val foreignSchedule = schedule.copy(
            id = "foreign-schedule",
            ownerProfileId = "other-profile",
        )
        val failure = runCatching {
            coordinator.activatePlanWithSchedule(plan.id, foreignSchedule, foreignSchedule.startDate.plusDays(7))
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(activeBefore?.schedule?.id, database.calendarDao().getActiveSchedule("profile")?.schedule?.id)
        assertEquals(null, database.calendarDao().getSchedule("foreign-schedule", "profile"))

        val unscopedCount = unscopedScheduleCount("foreign-schedule")
        assertEquals(0, unscopedCount)

        assertEquals(
            0,
            database.calendarDao().getScheduleOccurrences(
                "profile", "foreign-schedule", foreignSchedule.startDate.toString(),
                foreignSchedule.startDate.plusDays(7).toString(),
            ).size,
        )
        assertTrue(repository.getPlan(plan.id)?.isActive == planActiveBefore)
    }

    @Test
    fun rejectsUnknownPlanAndLeavesNoTrace() = runTest {
        val plan = repository.create(plan("plan-1", "Original"))
        repository.setActive(plan.id)
        val schedule = activeScheduleFor(plan, "schedule")
        calendar.saveAndMaterialize(schedule, schedule.startDate.plusDays(7))
        val activeBefore = database.calendarDao().getActiveSchedule("profile")
        val planActiveBefore = repository.getPlan(plan.id)?.isActive
        val occurrencesBefore = database.calendarDao().getScheduleOccurrences(
            "profile", "schedule", schedule.startDate.toString(),
            schedule.startDate.plusDays(7).toString(),
        )

        val ghostSchedule = activeScheduleFor(plan, "ghost-schedule").copy(planId = "nonexistent-plan")
        val failure = runCatching {
            coordinator.activatePlanWithSchedule("nonexistent-plan", ghostSchedule, ghostSchedule.startDate.plusDays(7))
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(activeBefore?.schedule?.id, database.calendarDao().getActiveSchedule("profile")?.schedule?.id)
        assertEquals(null, database.calendarDao().getSchedule("ghost-schedule", "profile"))

        val unscopedCount = unscopedScheduleCount("ghost-schedule")
        assertEquals(0, unscopedCount)

        assertEquals(
            occurrencesBefore.size,
            database.calendarDao().getScheduleOccurrences(
                "profile", "schedule", schedule.startDate.toString(),
                schedule.startDate.plusDays(7).toString(),
            ).size,
        )
        assertTrue(repository.getPlan(plan.id)?.isActive == planActiveBefore)
    }

    private fun unscopedScheduleCount(scheduleId: String): Int {
        return database.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM plan_schedules WHERE id = ?", arrayOf(scheduleId))
            .use { cursor ->
                check(cursor.moveToFirst())
                cursor.getInt(0)
            }
    }

    private fun plan(id: String, name: String) = TrainingPlan(
        id = id,
        ownerProfileId = "profile",
        name = name,
        createdAtEpochMs = 0,
        updatedAtEpochMs = 0,
        weeks = listOf(
            PlanWeek("week", 0, "Week", listOf(
                PlanDay("day", 0, "Day", listOf(
                    PlanBlock(
                        "block",
                        0,
                        PlanBlockType.MAIN,
                        "Main",
                        listOf(
                            PlanExercise(
                                "exercise",
                                0,
                                ExerciseReference(
                                    ExerciseReferenceKind.CUSTOM,
                                    customExerciseId = "custom",
                                    snapshot = ExerciseSnapshot(
                                        "Squat", TrackingType.REPS, setOf("none"), "legs",
                                    ),
                                ),
                                sets = listOf(SetPrescription("set", 0, repsMin = 8, restSeconds = 90)),
                            ),
                        ),
                    ),
                )),
            )),
        ),
    )

    private fun activeScheduleFor(plan: TrainingPlan, id: String) = PlanSchedule(
        id = id,
        ownerProfileId = plan.ownerProfileId,
        planId = plan.id,
        startDate = LocalDate.of(2026, 7, 13),
        timeZoneId = "Europe/Berlin",
        isActive = true,
        createdAtEpochMs = 1,
        updatedAtEpochMs = 1,
        rules = listOf(
            PlanDayScheduleRule(
                "$id-rule",
                id,
                plan.weeks.single().days.single().id,
                DayOfWeek.MONDAY,
                LocalTime.of(8, 0),
                60,
                null,
                0,
            ),
        ),
    )
}
