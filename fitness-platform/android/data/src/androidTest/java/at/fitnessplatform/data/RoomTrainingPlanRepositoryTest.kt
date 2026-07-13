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
import at.fitnessplatform.core.model.PlanExercise
import at.fitnessplatform.core.model.PlanWeek
import at.fitnessplatform.core.model.SetPrescription
import at.fitnessplatform.core.model.TrackingType
import at.fitnessplatform.core.model.TrainingPlan
import at.fitnessplatform.core.model.UuidProvider
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
                                    snapshot = ExerciseSnapshot("Squat", TrackingType.REPS, "none", "legs"),
                                ),
                                sets = listOf(SetPrescription("set", 0, repsMin = 8, restSeconds = 90)),
                            ),
                        ),
                    ),
                )),
            )),
        ),
    )

    private fun TrainingPlan.exercise() = weeks.single().days.single().blocks.single().exercises.single()
}
