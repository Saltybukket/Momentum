package at.fitnessplatform.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import at.fitnessplatform.core.model.*
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {
    @get:Rule val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private val name = "database-restart-test.db"

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(name)
        database = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
    }

    @After fun tearDown() { database.close(); context.deleteDatabase(name) }

    @Test fun guestProfileSurvivesDatabaseRestart() = runTest {
        database.guestProfileDao().insert(GuestProfile("p1", "Guest", 1).toEntity())
        database.close()
        database = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
        assertEquals("Guest", database.guestProfileDao().get()?.displayName)
    }

    @Test fun exerciseCanBeCreatedEditedAndSoftDeleted() = runTest {
        database.guestProfileDao().insert(GuestProfile("p1", "Guest", 1).toEntity())
        val original = CustomExercise("e1", "p1", "Squat", "", "Legs", "None", TrackingType.REPS, "", 1, 1)
        database.exerciseDao().insert(original.toEntity())
        database.exerciseDao().update(original.copy(name = "Air squat", updatedAtEpochMs = 2).toEntity())
        assertEquals("Air squat", database.exerciseDao().get("e1")?.name)
        database.exerciseDao().update(original.copy(deletedAtEpochMs = 3).toEntity())
        assertEquals(0, database.exerciseDao().observeActive().first().size)
    }

    @Test fun workoutAndOutboxAreStored() = runTest {
        database.guestProfileDao().insert(GuestProfile("p1", "Guest", 1).toEntity())
        database.exerciseDao().insert(CustomExercise("e1", "p1", "Squat", "", "Legs", "None", TrackingType.REPS, "", 1, 1).toEntity())
        val workout = Workout("w1", "p1", "Session", exercises = listOf(WorkoutExercise("we1", "w1", "e1", 0)), createdAtEpochMs = 1, updatedAtEpochMs = 1)
        database.workoutDao().insertWorkout(workout.toEntity())
        database.workoutDao().insertExercises(workout.exercises.map { it.toEntity() })
        database.outboxDao().insert(OutboxEntity("o1", "w1", OutboxOperationType.UPSERT_WORKOUT.name, "{}", 1, SyncStatus.PENDING.name, 0, null))
        assertNotNull(database.workoutDao().get("w1"))
        assertEquals(1, database.outboxDao().pending().size)
    }

    @Test fun exerciseConflictSnapshotsPersistAcrossRestart() = runTest {
        database.guestProfileDao().insert(GuestProfile("p1", "Guest", 1).toEntity())
        database.exerciseDao().insert(CustomExercise("e1", "p1", "Squat", "", "Legs", "None", TrackingType.REPS, "", 1, 1).toEntity())
        database.exerciseConflictDao().upsert(
            ExerciseConflictEntity(
                id = "c1", exerciseId = "e1", conflictType = ExerciseConflictType.BOTH_MODIFIED.name,
                localRevision = 1, remoteRevision = 2, localSnapshotJson = "{}", remoteSnapshotJson = "{}",
                detectedAtEpochMs = 2, resolutionStatus = ConflictResolutionStatus.OPEN.name, resolvedAtEpochMs = null,
            ),
        )
        database.close()
        database = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
        val conflict = database.exerciseConflictDao().getOpenForExercise("e1")
        assertNotNull(conflict)
        assertEquals(2, conflict?.remoteRevision)
    }

    @Test fun catalogRelationsAndCombinedFiltersPersistOffline() = runTest {
        val exercise = CatalogExerciseEntity("catalog-1", "squat", "demo", "self-authored", "CC0-1.0", "https://creativecommons.org/publicdomain/zero/1.0/", "1", "PUBLISHED", true, "Squat", "Description", "REPS")
        database.withTransaction {
            database.catalogDao().insertMuscles(listOf(CatalogMuscleEntity("legs", "Legs")))
            database.catalogDao().insertEquipment(listOf(CatalogEquipmentEntity("bodyweight", "Bodyweight")))
            database.catalogDao().insertExercises(listOf(exercise))
            database.catalogDao().insertExerciseMuscles(listOf(CatalogExerciseMuscleEntity(exercise.id, "legs", "PRIMARY")))
            database.catalogDao().insertExerciseEquipment(listOf(CatalogExerciseEquipmentEntity(exercise.id, "bodyweight")))
        }
        assertEquals(1, database.catalogDao().observe("", "legs", "bodyweight").first().size)
        assertEquals(0, database.catalogDao().observe("", "unknown", null).first().size)
        database.close()
        database = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
        assertEquals("Squat", database.catalogDao().observe("squ", null, null).first().single().exercise.name)
        assertEquals(0, database.exerciseDao().observeActive().first().size)
    }

    @Test fun migrationTwoToThreeCreatesCatalogSchema() {
        val migrationName = "catalog-migration.db"
        migrationHelper.createDatabase(migrationName, 2).close()
        migrationHelper.runMigrationsAndValidate(migrationName, 3, true, MIGRATION_2_3).close()
        context.deleteDatabase(migrationName)
    }

    @Test fun migrationThreeToFourAddsOutboxLeaseColumns() {
        val migrationName = "outbox-lease-migration.db"
        migrationHelper.createDatabase(migrationName, 3).close()
        migrationHelper.runMigrationsAndValidate(migrationName, 4, true, MIGRATION_3_4).close()
        context.deleteDatabase(migrationName)
    }

    @Test fun outboxClaimsAreExclusiveAndStaleClaimsAreReclaimed() = runTest {
        val row = OutboxEntity("lease-1", "aggregate", "UPSERT_PROFILE", "{}", 1, "PENDING", 0, null)
        database.outboxDao().insert(row)
        val first = database.outboxDao().claimBatch("worker-a", 100, 200)
        val activeSecond = database.outboxDao().claimBatch("worker-b", 150, 250)
        val reclaimed = database.outboxDao().claimBatch("worker-b", 201, 301)
        assertEquals(listOf("lease-1"), first.map { it.id })
        assertEquals(0, activeSecond.size)
        assertEquals(listOf("lease-1"), reclaimed.map { it.id })
        assertEquals("worker-b", reclaimed.single().claimOwner)
    }
}
