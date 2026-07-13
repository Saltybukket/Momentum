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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test fun migrationFourToFiveResetsRoomSyncCursor() = runTest {
        val migrationName = "sync-state-migration.db"
        migrationHelper.createDatabase(migrationName, 4).close()
        val migrated = migrationHelper.runMigrationsAndValidate(
            migrationName,
            5,
            true,
            MIGRATION_4_5,
        )
        migrated.query("SELECT exerciseCursor FROM sync_state WHERE singletonId = 1").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(0L, cursor.getLong(0))
        }
        migrated.close()
        context.deleteDatabase(migrationName)
    }

    @Test fun migrationFiveToSixPreservesExistingTablesAndAddsLocations() {
        val migrationName = "training-location-migration.db"
        val original = migrationHelper.createDatabase(migrationName, 5)
        original.execSQL(
            "INSERT INTO guest_profile VALUES ('profile', 'Guest', 1, 'METRIC', 'COMPLETED', 'LOCAL_ONLY', NULL, NULL)",
        )
        original.execSQL(
            "INSERT INTO custom_exercises VALUES ('private', 'profile', 'Squat', '', 'legs', 'none', 'REPS', '', 1, 1, 'LOCAL_ONLY', NULL, NULL, NULL)",
        )
        original.execSQL(
            "INSERT INTO workouts VALUES ('workout', 'profile', 'Session', 'PLANNED', NULL, NULL, '', 1, 1, 'LOCAL_ONLY', NULL, NULL)",
        )
        original.execSQL(
            "INSERT INTO catalog_muscles (slug, name) VALUES ('legs', 'Legs')",
        )
        original.execSQL(
            "INSERT INTO catalog_exercises VALUES ('catalog', 'squat', 'demo', 'synthetic', 'CC0-1.0', 'https://example.test/license', '1', 'PUBLISHED', 1, 'Squat', '', 'REPS')",
        )
        original.close()
        val migrated = migrationHelper.runMigrationsAndValidate(migrationName, 6, true, MIGRATION_5_6)
        migrated.query("SELECT name FROM catalog_muscles WHERE slug = 'legs'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("Legs", cursor.getString(0))
        }
        listOf("guest_profile", "custom_exercises", "workouts", "catalog_exercises").forEach { table ->
            migrated.query("SELECT COUNT(*) FROM $table").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        }
        migrated.close()
        context.deleteDatabase(migrationName)
    }

    @Test fun trainingLocationsSwitchReplaceEquipmentAndDeleteActiveAtomically() = runTest {
        val dao = database.trainingLocationDao()
        dao.insert(TrainingLocationEntity("home", "Home", "HOME", true, 1, 1, 1, 0, null))
        dao.insert(TrainingLocationEntity("gym", "Gym", "FITNESS_CENTER", false, null, 2, 2, 0, null))
        dao.replaceEquipment("home", setOf("mat", "dumbbells"))
        dao.replaceEquipment("home", setOf("open-floor"))
        assertEquals(1, dao.equipmentCount("home"))
        assertEquals("home", dao.active()?.location?.id)

        dao.setActive("gym", 3)
        assertEquals("gym", dao.active()?.location?.id)
        dao.softDelete("gym", 4)
        assertEquals("home", dao.active()?.location?.id)
        assertEquals(1, dao.countActiveRows())
    }

    @Test fun trainingLocationEquipmentCascadesAndDuplicateRelationsAreRejected() = runTest {
        val dao = database.trainingLocationDao()
        dao.insert(TrainingLocationEntity("location", "Park", "OUTDOOR", true, 1, 1, 1, 0, null))
        dao.insertEquipment(listOf(TrainingLocationEquipmentEntity("location", "open-floor")))
        assertTrue(runCatching {
            dao.insertEquipment(listOf(TrainingLocationEquipmentEntity("location", "open-floor")))
        }.isFailure)
        database.openHelper.writableDatabase.execSQL("DELETE FROM training_locations WHERE id = 'location'")
        assertEquals(0, dao.equipmentCount("location"))
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

    @Test fun releaseClaimsOnlyReturnsCurrentOwnersRowsToPending() = runTest {
        database.outboxDao().insert(
            OutboxEntity("own", "a", "UPSERT_PROFILE", "{}", 1, "PENDING", 2, "old"),
        )
        database.outboxDao().insert(
            OutboxEntity("foreign", "b", "UPSERT_PROFILE", "{}", 2, "PENDING", 3, "old"),
        )
        database.outboxDao().claimBatch("worker-a", 10, 100, limit = 1)
        database.outboxDao().claimBatch("worker-b", 10, 100, limit = 1)

        assertEquals(1, database.outboxDao().releaseClaims("worker-a"))

        val own = database.outboxDao().pending().single { it.id == "own" }
        assertEquals("PENDING", own.status)
        assertEquals(2, own.retryCount)
        assertNull(own.claimOwner)
        assertEquals("worker-b", database.outboxDao().claimedBy("worker-b").single().claimOwner)
    }

    @Test fun staleOwnerCannotFinalizeReclaimedRows() = runTest {
        listOf("success", "failure", "conflict").forEachIndexed { index, id ->
            database.outboxDao().insert(
                OutboxEntity(id, id, "UPSERT_PROFILE", "{}", index.toLong(), "PENDING", 2, "old"),
            )
        }
        database.outboxDao().claimBatch("worker-a", 10, 100)
        database.outboxDao().claimBatch("worker-b", 101, 200)

        assertEquals(0, database.outboxDao().markSynced(listOf("success"), "worker-a"))
        assertEquals(0, database.outboxDao().markFailed(listOf("failure"), "worker-a", "stale"))
        assertEquals(0, database.outboxDao().markConflict(listOf("conflict"), "worker-a"))

        val reclaimed = database.outboxDao().claimedBy("worker-b").associateBy { it.id }
        assertEquals(setOf("success", "failure", "conflict"), reclaimed.keys)
        reclaimed.values.forEach { row ->
            assertEquals("SYNCING", row.status)
            assertEquals("worker-b", row.claimOwner)
            assertEquals(2, row.retryCount)
            assertEquals("old", row.lastError)
        }

        assertEquals(1, database.outboxDao().markSynced(listOf("success"), "worker-b"))
        assertEquals(1, database.outboxDao().markFailed(listOf("failure"), "worker-b", "network"))
        assertEquals(1, database.outboxDao().markConflict(listOf("conflict"), "worker-b"))
    }

    @Test fun syncCursorCommitsWithPageAndRollsBackWithPage() = runTest {
        database.guestProfileDao().insert(GuestProfile("p1", "Guest", 1).toEntity())
        runCatching {
            database.withTransaction {
                database.exerciseDao().insert(
                    CustomExercise(
                        "rolled-back",
                        "p1",
                        "Remote",
                        "",
                        "Legs",
                        "None",
                        TrackingType.REPS,
                        "",
                        1,
                        1,
                        syncStatus = SyncStatus.SYNCED,
                    ).toEntity(),
                )
                database.syncStateDao().put(SyncStateEntity(exerciseCursor = 9, updatedAtEpochMs = 2))
                error("simulated process boundary")
            }
        }
        assertNull(database.exerciseDao().get("rolled-back"))
        assertEquals(0L, database.syncStateDao().exerciseCursor())

        database.withTransaction {
            database.exerciseDao().insert(
                CustomExercise(
                    "committed",
                    "p1",
                    "Remote",
                    "",
                    "Legs",
                    "None",
                    TrackingType.REPS,
                    "",
                    1,
                    1,
                    syncStatus = SyncStatus.SYNCED,
                ).toEntity(),
            )
            database.syncStateDao().put(SyncStateEntity(exerciseCursor = 9, updatedAtEpochMs = 2))
        }
        assertNotNull(database.exerciseDao().get("committed"))
        assertEquals(9L, database.syncStateDao().exerciseCursor())
    }
}
