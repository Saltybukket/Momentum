package at.fitnessplatform.domain

import at.fitnessplatform.core.model.ExerciseReference
import at.fitnessplatform.core.model.ExerciseReferenceKind
import at.fitnessplatform.core.model.ExerciseSnapshot
import at.fitnessplatform.core.model.PlanBlock
import at.fitnessplatform.core.model.PlanBlockType
import at.fitnessplatform.core.model.PlanDay
import at.fitnessplatform.core.model.PlanExercise
import at.fitnessplatform.core.model.PlanSetType
import at.fitnessplatform.core.model.PlanWeek
import at.fitnessplatform.core.model.SetPrescription
import at.fitnessplatform.core.model.TempoPrescription
import at.fitnessplatform.core.model.TrackingType
import at.fitnessplatform.core.model.TrainingPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingPlanUseCasesTest {
    @Test fun `validator enforces aggregate limits references and tracking matrix`() {
        validateTrainingPlan(plan())
        val tooMany = plan().copy(
            weeks = listOf(
                PlanWeek("week", 0, "Week", listOf(
                    PlanDay("day", 0, "Day", listOf(
                        PlanBlock("a", 0, PlanBlockType.MAIN, "A", List(16) { exercise("a-$it", it) }),
                        PlanBlock("b", 1, PlanBlockType.OPTIONAL, "B", List(15) { exercise("b-$it", it) }),
                    )),
                )),
            ),
        )
        assertTrue(runCatching { validateTrainingPlan(tooMany) }.exceptionOrNull() is ValidationException)

        val invalidTracking = plan().replaceSet(SetPrescription("set", 0, durationSeconds = 30))
        assertTrue(runCatching { validateTrainingPlan(invalidTracking) }.exceptionOrNull() is ValidationException)
        val invalidCatalog = plan().replaceReference(
            ExerciseReference(
                kind = ExerciseReferenceKind.CATALOG,
                catalogSource = "demo",
                catalogExternalId = null,
                snapshot = ExerciseSnapshot("Squat", TrackingType.REPS, setOf("none")),
            ),
        )
        assertTrue(runCatching { validateTrainingPlan(invalidCatalog) }.exceptionOrNull() is ValidationException)
        listOf(
            SetPrescription("bad", 0, repsMin = 8, repsMax = 7),
            SetPrescription("bad", 0, repsMin = 8, targetRpe = 11.0),
            SetPrescription("bad", 0, repsMin = 8, targetRpe = 8.0, targetRir = 2),
            SetPrescription("bad", 0, repsMin = 8, tempo = TempoPrescription("10", "0", "X", "0")),
        ).forEach { invalid ->
            assertTrue(runCatching { validateTrainingPlan(plan().replaceSet(invalid)) }.exceptionOrNull() is ValidationException)
        }
        val duplicateWeekIndex = plan().let { base ->
            base.copy(weeks = base.weeks + base.weeks.single().copy(id = "week-2", position = 1))
        }
        assertTrue(runCatching { validateTrainingPlan(duplicateWeekIndex) }.exceptionOrNull() is ValidationException)
        val invalidDistanceTargets = plan()
            .replaceTrackingType(TrackingType.DISTANCE_DURATION)
            .replaceSet(SetPrescription("bad", 0, repsMin = 8, distanceMeters = 1_000.0))
        assertTrue(runCatching { validateTrainingPlan(invalidDistanceTargets) }.exceptionOrNull() is ValidationException)
        val timeSetWithoutDuration = plan().replaceSet(
            SetPrescription("bad", 0, PlanSetType.TIME, repsMin = 8),
        )
        assertTrue(runCatching { validateTrainingPlan(timeSetWithoutDuration) }.exceptionOrNull() is ValidationException)
        assertTrue(
            runCatching { validateTrainingPlan(plan().copy(name = "Unsafe\u202e")) }.exceptionOrNull() is ValidationException,
        )
    }

    @Test fun `save normalizes copy delegates and snapshot freezes plan revision`() = runTest {
        val repository = FakeTrainingPlanRepository()
        val saved = SaveTrainingPlanUseCase(repository)(plan().copy(name = "  Strength   base  ", description = "  Base  "))
        assertEquals("Strength base", saved.name)
        assertEquals("Base", saved.description)

        val copied = CopyTrainingPlanUseCase(repository)(saved.id)
        assertNotEquals(saved.id, copied.id)
        assertEquals(saved.id, copied.sourceTemplateId)

        val snapshot = saved.snapshot(
            dayId = "day",
            plannedDurationMinutes = 55,
            trainingLocationId = "home",
            scheduledStartEpochMs = 10_000,
            timeZoneId = "Europe/Berlin",
        )
        assertEquals(saved.revision, snapshot.planRevision)
        assertEquals(listOf("exercise"), snapshot.exercises.map { it.planExerciseId })
        assertEquals(8, snapshot.exercises.single().prescriptions.single().repsMin)
        assertEquals(55, snapshot.plannedDurationMinutes)
        assertEquals("home", snapshot.trainingLocationId)
        assertEquals(10_000L, snapshot.scheduledStartEpochMs)
        assertEquals("Europe/Berlin", snapshot.timeZoneId)
    }

    private fun plan() = TrainingPlan(
        id = "plan",
        ownerProfileId = "profile",
        name = "Plan",
        createdAtEpochMs = 1,
        updatedAtEpochMs = 1,
        weeks = listOf(
            PlanWeek("week", 0, "Week", listOf(
                PlanDay("day", 0, "Day", listOf(
                    PlanBlock("block", 0, PlanBlockType.MAIN, "Main", listOf(exercise("exercise", 0))),
                )),
            )),
        ),
    )

    private fun exercise(id: String, position: Int) = PlanExercise(
        id,
        position,
        ExerciseReference(
            kind = ExerciseReferenceKind.CUSTOM,
            customExerciseId = "custom",
            snapshot = ExerciseSnapshot("Squat", TrackingType.REPS, setOf("none"), "legs"),
        ),
        sets = listOf(
            SetPrescription(
                "set-$id",
                0,
                repsMin = 8,
                repsMax = 10,
                restSeconds = 90,
                tempo = TempoPrescription("3", "1", "X", "1"),
            ),
        ),
    )

    private fun TrainingPlan.replaceSet(set: SetPrescription) = copy(
        weeks = weeks.map { week ->
            week.copy(days = week.days.map { day ->
                day.copy(blocks = day.blocks.map { block ->
                    block.copy(exercises = block.exercises.map { it.copy(sets = listOf(set)) })
                })
            })
        },
    )

    private fun TrainingPlan.replaceReference(reference: ExerciseReference) = copy(
        weeks = weeks.map { week ->
            week.copy(days = week.days.map { day ->
                day.copy(blocks = day.blocks.map { block ->
                    block.copy(exercises = block.exercises.map { it.copy(reference = reference) })
                })
            })
        },
    )

    private fun TrainingPlan.replaceTrackingType(trackingType: TrackingType) = replaceReference(
        weeks.single().days.single().blocks.single().exercises.single().reference.copy(
            snapshot = weeks.single().days.single().blocks.single().exercises.single().reference.snapshot.copy(
                trackingType = trackingType,
            ),
        ),
    )
}

private class FakeTrainingPlanRepository : TrainingPlanRepository {
    private val plans = MutableStateFlow<List<TrainingPlan>>(emptyList())
    override fun observePlans(): Flow<List<TrainingPlan>> = plans
    override fun observeActivePlan(): Flow<TrainingPlan?> = MutableStateFlow(plans.value.firstOrNull { it.isActive })
    override fun observePlan(id: String): Flow<TrainingPlan?> = MutableStateFlow(plans.value.firstOrNull { it.id == id })
    override suspend fun getPlan(id: String) = plans.value.firstOrNull { it.id == id }
    override suspend fun create(plan: TrainingPlan) = plan.also { plans.value += it }
    override suspend fun update(plan: TrainingPlan) = plan.also { updated ->
        plans.value = plans.value.map { if (it.id == updated.id) updated else it }
    }
    override suspend fun copy(id: String, transform: (TrainingPlan) -> TrainingPlan): TrainingPlan {
        val source = requireNotNull(getPlan(id))
        return transform(source.copy(id = "$id-copy", sourceTemplateId = source.sourceTemplateId ?: id))
            .also { plans.value += it }
    }
    override suspend fun setActive(id: String) = Unit
    override suspend fun setArchived(id: String, archived: Boolean) = Unit
    override suspend fun delete(id: String) = Unit
    override suspend fun seedStarterPlans() = Unit
}
