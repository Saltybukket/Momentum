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
import at.fitnessplatform.core.model.ExerciseResolutionStatus
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
import at.fitnessplatform.core.model.TrainingPlanGoal
import at.fitnessplatform.core.model.UuidProvider
import at.fitnessplatform.domain.PlanRemovalDecisionRequiredException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomTrainingPlanRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: RoomTrainingPlanRepository
    private var now = 100L
    private var sequence = 0

    @Before fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
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
    }

    @After fun tearDown() = database.close()

    @Test fun aggregateCreateUpdateCopyActivateArchiveAndDeleteAreOwnerScoped() = runTest {
        val created = repository.create(plan("plan-1", "First"))
        assertEquals(ExerciseResolutionStatus.RESOLVED, created.exercise().reference.resolutionStatus)
        assertEquals(listOf("plan-1"), repository.observePlans().first().map { it.id })

        val updated = repository.update(created.copy(name = "Updated", weeks = created.weeks.reversed()))
        assertEquals(1, updated.revision)
        assertEquals("Updated", repository.getPlan(created.id)?.name)

        val copied = repository.copy(created.id)
        assertNotEquals(created.id, copied.id)
        assertNotEquals(created.exercise().id, copied.exercise().id)
        assertEquals(created.id, copied.sourceTemplateId)

        repository.setActive(created.id)
        repository.setActive(copied.id)
        assertFalse(repository.getPlan(created.id)?.isActive ?: true)
        assertTrue(repository.getPlan(copied.id)?.isActive == true)
        repository.setArchived(copied.id, true)
        assertTrue(repository.getPlan(copied.id)?.isArchived == true)
        assertFalse(repository.getPlan(copied.id)?.isActive ?: true)

        repository.delete(created.id)
        assertEquals(null, repository.getPlan(created.id))
    }

    @Test fun failedReplacementRollsBackAndDeletedCustomReferencesRetainSnapshot() = runTest {
        val created = repository.create(plan("plan-1", "Original"))
        val duplicatePositions = created.copy(
            name = "Must not persist",
            weeks = created.weeks + created.weeks.single().copy(id = "week-2"),
        )
        assertTrue(runCatching { repository.update(duplicatePositions) }.isFailure)
        assertEquals("Original", repository.getPlan(created.id)?.name)
        assertEquals(1, database.trainingPlanDao().weekCount(created.id))

        val custom = requireNotNull(database.exerciseDao().get("custom"))
        database.exerciseDao().update(custom.copy(deletedAtEpochMs = 200))
        val resolved = repository.update(created.copy(name = "Snapshot survives"))
        assertEquals(ExerciseResolutionStatus.DELETED_CUSTOM, resolved.exercise().reference.resolutionStatus)
        assertEquals("Squat", resolved.exercise().reference.snapshot.name)
    }

    @Test fun adaptedCopyIsInsertedExactlyOnceOrNotAtAll() = runTest {
        val created = repository.create(plan("plan-1", "Original"))
        val before = repository.observePlans().first().size
        val adapted = repository.copy(created.id) { it.copy(name = "Adapted once") }
        assertEquals(before + 1, repository.observePlans().first().size)
        assertEquals("Adapted once", adapted.name)

        assertTrue(runCatching { repository.copy(created.id) { error("adaptation failed") } }.isFailure)
        assertEquals(before + 1, repository.observePlans().first().size)
    }

    @Test fun planScheduleSwitchAndArchiveLifecycleRollBackAsSingleTransactions() = runTest {
        val oldPlan = repository.create(plan("old-plan", "Old"))
        val newPlan = repository.create(plan("new-plan", "New", "new-"))
        repository.setActive(oldPlan.id)
        val calendar = RoomTrainingCalendarRepository(
            database,
            database.guestProfileDao(),
            database.trainingPlanDao(),
            database.calendarDao(),
            object : UuidProvider { override fun newUuid() = "calendar-${sequence++}" },
            object : Clock { override fun nowEpochMs() = now++ },
        )
        val coordinator = RoomTrainingPlanCalendarCoordinator(database, repository, calendar)
        val oldSchedule = scheduleFor(oldPlan, "old-schedule")
        calendar.saveAndMaterialize(oldSchedule, oldSchedule.startDate.plusDays(7))
        database.openHelper.writableDatabase.execSQL(
            """CREATE TRIGGER fail_plan_switch BEFORE UPDATE OF isActive ON training_plans
                WHEN NEW.id = 'new-plan' AND NEW.isActive = 1
                BEGIN SELECT RAISE(ABORT, 'injected plan activation failure'); END""",
        )

        val newSchedule = scheduleFor(newPlan, "new-schedule")
        assertTrue(
            runCatching {
                coordinator.activatePlanWithSchedule(
                    newPlan.id,
                    newSchedule,
                    newSchedule.startDate.plusDays(7),
                )
            }.isFailure,
        )
        assertEquals("old-schedule", calendar.observeActiveSchedule().first()?.id)
        assertEquals(null, database.calendarDao().getSchedule("new-schedule", "profile"))
        assertTrue(repository.getPlan(oldPlan.id)?.isActive == true)
        assertFalse(repository.getPlan(newPlan.id)?.isActive ?: true)
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_plan_switch")

        database.openHelper.writableDatabase.execSQL(
            """CREATE TRIGGER fail_plan_archive BEFORE UPDATE OF isArchived ON training_plans
                WHEN NEW.id = 'old-plan' AND NEW.isArchived = 1
                BEGIN SELECT RAISE(ABORT, 'injected archive failure'); END""",
        )
        assertTrue(runCatching { coordinator.setArchived(oldPlan.id, true) }.isFailure)
        assertEquals("old-schedule", calendar.observeActiveSchedule().first()?.id)
        assertFalse(repository.getPlan(oldPlan.id)?.isArchived ?: true)
        assertTrue(repository.getPlan(oldPlan.id)?.isActive == true)
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_plan_archive")
    }

    private fun scheduleFor(plan: TrainingPlan, id: String): PlanSchedule {
        val day = plan.weeks.single().days.single()
        return PlanSchedule(
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
                    day.id,
                    DayOfWeek.MONDAY,
                    LocalTime.of(8, 0),
                    60,
                    null,
                    0,
                ),
            ),
        )
    }

    @Test fun renamingScheduledPlanPreservesRulesAndOccurrencePlanDayIdentity() = runTest {
        val created = repository.create(plan("plan-1", "Original"))
        val calendar = RoomTrainingCalendarRepository(
            database,
            database.guestProfileDao(),
            database.trainingPlanDao(),
            database.calendarDao(),
            object : UuidProvider { override fun newUuid() = "calendar-${sequence++}" },
            object : Clock { override fun nowEpochMs() = now++ },
        )
        val schedule = calendar.saveSchedule(
            PlanSchedule(
                id = "schedule",
                ownerProfileId = "profile",
                planId = created.id,
                startDate = LocalDate.of(2026, 7, 13),
                timeZoneId = "Europe/Berlin",
                isActive = true,
                createdAtEpochMs = 0,
                updatedAtEpochMs = 0,
                rules = listOf(
                    PlanDayScheduleRule(
                        id = "rule",
                        scheduleId = "schedule",
                        planDayId = "day",
                        dayOfWeek = DayOfWeek.MONDAY,
                        defaultStartTime = LocalTime.of(18, 0),
                        defaultDurationMinutes = 60,
                        preferredLocationId = null,
                        position = 0,
                    ),
                ),
            ),
        )
        val occurrence = calendar.materialize(schedule.id, schedule.startDate).single()

        repository.update(created.copy(name = "Renamed"))

        assertEquals("day", database.calendarDao().getSchedule("schedule", "profile")?.rules?.single()?.planDayId)
        assertEquals("day", database.calendarDao().getOccurrence(occurrence.id, "profile")?.planDayId)
    }

    @Test fun scheduledPlanFieldsSetsAndOrderingUpdateWithoutReplacingChildren() = runTest {
        var current = repository.create(reorderablePlan())
        val (_, occurrenceId) = schedule(current)

        current = repository.update(current.copy(description = "Updated description"))
        current = repository.update(current.copy(goal = TrainingPlanGoal.MOBILITY))
        current = repository.update(
            current.copy(
                weeks = current.weeks.map { week ->
                    week.copy(
                        days = week.days.map { day ->
                            day.copy(
                                blocks = day.blocks.map { block ->
                                    block.copy(
                                        exercises = block.exercises.map { exercise ->
                                            exercise.copy(
                                                sets = exercise.sets.map { set ->
                                                    if (set.id == "set-a") set.copy(repsMin = 12) else set
                                                },
                                            )
                                        },
                                    )
                                },
                            )
                        },
                    )
                },
            ),
        )
        current = repository.update(
            current.copy(
                weeks = current.weeks.map { week ->
                    week.copy(
                        days = week.days.map { day ->
                            day.copy(
                                blocks = day.blocks.reversed().mapIndexed { blockPosition, block ->
                                    block.copy(
                                        position = blockPosition,
                                        exercises = block.exercises.reversed().mapIndexed { exercisePosition, exercise ->
                                            exercise.copy(position = exercisePosition)
                                        },
                                    )
                                },
                            )
                        },
                    )
                },
            ),
        )

        assertEquals("Updated description", current.description)
        assertEquals(TrainingPlanGoal.MOBILITY, current.goal)
        assertEquals(12, current.weeks.first().days.first().blocks.flatMap { it.exercises }
            .flatMap { it.sets }.single { it.id == "set-a" }.repsMin)
        assertEquals(listOf("block-b", "block-a"), current.weeks.first().days.first().blocks.map { it.id })
        assertEquals(listOf("exercise-b", "exercise-a"), current.weeks.first().days.first().blocks
            .single { it.id == "block-a" }.exercises.map { it.id })
        assertEquals("day", database.calendarDao().getSchedule("schedule", "profile")?.rules?.single()?.planDayId)
        assertEquals("day", database.calendarDao().getOccurrence(occurrenceId, "profile")?.planDayIdSnapshot)
    }

    @Test fun removingScheduledStructureRequiresDecisionAndRollsBackAggregate() = runTest {
        val created = repository.create(reorderablePlan())
        schedule(created)
        val withoutScheduledDay = created.copy(
            name = "Must roll back",
            weeks = created.weeks.map { week -> week.copy(days = week.days.filterNot { it.id == "day" }) },
        )

        val failure = runCatching { repository.update(withoutScheduledDay) }.exceptionOrNull()

        assertTrue(failure is PlanRemovalDecisionRequiredException)
        assertEquals(setOf("day"), (failure as PlanRemovalDecisionRequiredException).affectedPlanDayIds)
        assertEquals("Reorderable", repository.getPlan(created.id)?.name)
        assertEquals("day", database.calendarDao().getSchedule("schedule", "profile")?.rules?.single()?.planDayId)
    }

    @Test fun archivingAndSoftDeletingPlanPreserveScheduleAndOccurrenceHistory() = runTest {
        val created = repository.create(plan("plan-1", "Original"))
        val (_, occurrenceId) = schedule(created)

        repository.setArchived(created.id, true)
        assertEquals("day", database.calendarDao().getSchedule("schedule", "profile")?.rules?.single()?.planDayId)
        repository.delete(created.id)

        assertEquals(null, repository.getPlan(created.id))
        assertEquals("day", database.calendarDao().getSchedule("schedule", "profile")?.rules?.single()?.planDayId)
        assertEquals("day", database.calendarDao().getOccurrence(occurrenceId, "profile")?.planDayIdSnapshot)
    }

    @Test fun starterPlansAreIdempotentEditableAndKeepStableCatalogIdentity() = runTest {
        repository.seedStarterPlans()
        repository.seedStarterPlans()
        val starters = repository.observePlans().first()
        assertEquals(2, starters.size)
        assertTrue(starters.all { it.sourceTemplateId == null && !it.isActive })
        assertTrue(starters.flatMap { it.weeks }.flatMap { it.days }.flatMap { it.blocks }
            .flatMap { it.exercises }.all {
                it.reference.catalogSource == "momentum-self-authored-demo" &&
                    !it.reference.catalogExternalId.isNullOrBlank()
            })
        val edited = repository.update(starters.first().copy(name = "My own starter"))
        assertEquals("My own starter", edited.name)

        starters.forEach { repository.delete(it.id) }
        repository.seedStarterPlans()
        assertTrue(repository.observePlans().first().isEmpty())
    }

    private fun plan(id: String, name: String, nestedIdPrefix: String = "") = TrainingPlan(
        id = id,
        ownerProfileId = "profile",
        name = name,
        createdAtEpochMs = 0,
        updatedAtEpochMs = 0,
        weeks = listOf(
            PlanWeek("${nestedIdPrefix}week", 0, "Week", listOf(
                PlanDay("${nestedIdPrefix}day", 0, "Day", listOf(
                    PlanBlock(
                        "${nestedIdPrefix}block",
                        0,
                        PlanBlockType.MAIN,
                        "Main",
                        listOf(
                            PlanExercise(
                                "${nestedIdPrefix}exercise",
                                0,
                                ExerciseReference(
                                    ExerciseReferenceKind.CUSTOM,
                                    customExerciseId = "custom",
                                    snapshot = ExerciseSnapshot("Squat", TrackingType.REPS, setOf("none"), "legs"),
                                ),
                                sets = listOf(
                                    SetPrescription("${nestedIdPrefix}set", 0, repsMin = 8, restSeconds = 90),
                                ),
                            ),
                        ),
                    ),
                )),
            )),
        ),
    )

    private fun reorderablePlan() = TrainingPlan(
        id = "plan-1",
        ownerProfileId = "profile",
        name = "Reorderable",
        createdAtEpochMs = 0,
        updatedAtEpochMs = 0,
        weeks = listOf(
            PlanWeek(
                "week",
                0,
                "Week",
                listOf(
                    PlanDay(
                        "day",
                        0,
                        "Day",
                        listOf(
                            PlanBlock(
                                "block-a",
                                0,
                                PlanBlockType.MAIN,
                                "Main",
                                listOf(
                                    planExercise("exercise-a", 0, "set-a"),
                                    planExercise("exercise-b", 1, "set-b"),
                                ),
                            ),
                            PlanBlock(
                                "block-b",
                                1,
                                PlanBlockType.OPTIONAL,
                                "Accessory",
                                listOf(planExercise("exercise-c", 0, "set-c")),
                            ),
                        ),
                    ),
                    PlanDay("spare-day", 1, "Spare day", emptyList()),
                ),
            ),
        ),
    )

    private fun planExercise(id: String, position: Int, setId: String) = PlanExercise(
        id = id,
        position = position,
        reference = ExerciseReference(
            ExerciseReferenceKind.CUSTOM,
            customExerciseId = "custom",
            snapshot = ExerciseSnapshot("Squat", TrackingType.REPS, setOf("none"), "legs"),
        ),
        sets = listOf(SetPrescription(setId, 0, repsMin = 8, restSeconds = 90)),
    )

    private suspend fun schedule(plan: TrainingPlan): Pair<PlanSchedule, String> {
        val calendar = RoomTrainingCalendarRepository(
            database,
            database.guestProfileDao(),
            database.trainingPlanDao(),
            database.calendarDao(),
            object : UuidProvider { override fun newUuid() = "calendar-${sequence++}" },
            object : Clock { override fun nowEpochMs() = now++ },
        )
        val saved = calendar.saveSchedule(
            PlanSchedule(
                id = "schedule",
                ownerProfileId = "profile",
                planId = plan.id,
                startDate = LocalDate.of(2026, 7, 13),
                timeZoneId = "Europe/Berlin",
                isActive = true,
                createdAtEpochMs = 0,
                updatedAtEpochMs = 0,
                rules = listOf(
                    PlanDayScheduleRule(
                        id = "rule",
                        scheduleId = "schedule",
                        planDayId = "day",
                        dayOfWeek = DayOfWeek.MONDAY,
                        defaultStartTime = LocalTime.of(18, 0),
                        defaultDurationMinutes = 60,
                        preferredLocationId = null,
                        position = 0,
                    ),
                ),
            ),
        )
        return saved to calendar.materialize(saved.id, saved.startDate).single().id
    }

    private fun TrainingPlan.exercise() = weeks.single().days.single().blocks.single().exercises.single()
}
