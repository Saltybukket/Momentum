package at.fitnessplatform.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import at.fitnessplatform.core.database.AppDatabase
import at.fitnessplatform.core.database.ExerciseConflictEntity
import at.fitnessplatform.core.database.ExerciseConflictSnapshot
import at.fitnessplatform.core.database.toEntity
import at.fitnessplatform.core.model.*
import at.fitnessplatform.domain.DomainEventDispatcher
import at.fitnessplatform.domain.DomainEventHandler
import at.fitnessplatform.domain.SyncEnqueuer
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomExerciseRepositoryConflictTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: RoomExerciseRepository
    private val json = Json
    private val clock = FakeClock()
    private val ids = FakeUuidProvider()

    @Before fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomExerciseRepository(
            database = database,
            profileDao = database.guestProfileDao(),
            exerciseDao = database.exerciseDao(),
            conflictDao = database.exerciseConflictDao(),
            outboxDao = database.outboxDao(),
            ids = ids,
            clock = clock,
            events = FakeDomainEventDispatcher(),
            syncEnqueuer = FakeSyncEnqueuer(),
        )
    }

    @After fun tearDown() = database.close()

    @Test fun takeServerReplacesLocalExerciseAndCancelsOnlyUnacknowledgedOperations() = runTest {
        createOpenConflict()

        repository.resolveConflict("exercise", ExerciseConflictResolution.TAKE_SERVER)

        val stored = requireNotNull(database.exerciseDao().get("exercise"))
        assertEquals("Server squat", stored.name)
        assertEquals(SyncStatus.SYNCED.name, stored.syncStatus)
        assertEquals(0, database.outboxDao().pending().size)
        assertEquals(null, database.exerciseConflictDao().getOpenForExercise("exercise"))
    }

    @Test fun keepLocalQueuesNewOperationAndRetainsConflictUntilAcknowledged() = runTest {
        createOpenConflict()

        repository.resolveConflict("exercise", ExerciseConflictResolution.KEEP_LOCAL)

        val stored = requireNotNull(database.exerciseDao().get("exercise"))
        assertEquals("Local squat", stored.name)
        assertEquals(SyncStatus.PENDING.name, stored.syncStatus)
        assertEquals(1, database.outboxDao().pending().size)
        assertEquals(ConflictResolutionStatus.PENDING_CONFIRMATION.name, conflictResolutionStatus("exercise"))
    }

    @Test fun manualMergeQueuesMergedSnapshotWithRemoteRevision() = runTest {
        createOpenConflict()
        val merged = CustomExercise(
            id = "exercise", ownerProfileId = "profile", name = "Merged squat", description = "Merged",
            primaryMuscleGroup = "Legs", requiredEquipment = "None", trackingType = TrackingType.REPS,
            notes = "Merged notes", createdAtEpochMs = 1, updatedAtEpochMs = 1,
        )

        repository.resolveConflict("exercise", ExerciseConflictResolution.MERGE, merged)

        val stored = requireNotNull(database.exerciseDao().get("exercise"))
        assertEquals("Merged squat", stored.name)
        assertEquals(7L, stored.conflictVersion)
        assertNotNull(database.outboxDao().pending().single())
    }

    private suspend fun createOpenConflict() {
        database.guestProfileDao().insert(GuestProfile("profile", "Guest", 1).toEntity())
        val local = CustomExercise(
            id = "exercise", ownerProfileId = "profile", name = "Local squat", description = "Local",
            primaryMuscleGroup = "Legs", requiredEquipment = "None", trackingType = TrackingType.REPS,
            notes = "Local notes", createdAtEpochMs = 1, updatedAtEpochMs = 2,
            syncStatus = SyncStatus.CONFLICT, conflictVersion = 5,
        )
        database.exerciseDao().insert(local.toEntity())
        database.outboxDao().insert(
            at.fitnessplatform.core.database.OutboxEntity(
                id = "stale-operation", aggregateId = "exercise", operationType = OutboxOperationType.UPSERT_EXERCISE.name,
                payloadJson = "{}", createdAtEpochMs = 2, status = SyncStatus.CONFLICT.name, retryCount = 0, lastError = "Conflict",
            ),
        )
        val remote = ExerciseConflictSnapshot(
            id = "exercise", ownerProfileId = "profile", name = "Server squat", description = "Server",
            primaryMuscleGroup = "Legs", requiredEquipment = "Barbell", trackingType = TrackingType.REPS.name,
            notes = "Server notes", createdAtEpochMs = 1, updatedAtEpochMs = 3, revision = 7, deletedAtEpochMs = null,
        )
        val localSnapshot = ExerciseConflictSnapshot(
            id = local.id, ownerProfileId = local.ownerProfileId, name = local.name, description = local.description,
            primaryMuscleGroup = local.primaryMuscleGroup, requiredEquipment = local.requiredEquipment,
            trackingType = local.trackingType.name, notes = local.notes, createdAtEpochMs = local.createdAtEpochMs,
            updatedAtEpochMs = local.updatedAtEpochMs, revision = local.conflictVersion, deletedAtEpochMs = null,
        )
        database.exerciseConflictDao().upsert(
            ExerciseConflictEntity(
                id = "conflict", exerciseId = "exercise", conflictType = ExerciseConflictType.BOTH_MODIFIED.name,
                localRevision = 5, remoteRevision = 7, localSnapshotJson = json.encodeToString(localSnapshot),
                remoteSnapshotJson = json.encodeToString(remote), detectedAtEpochMs = 3,
                resolutionStatus = ConflictResolutionStatus.OPEN.name, resolvedAtEpochMs = null,
            ),
        )
    }

    private fun conflictResolutionStatus(exerciseId: String): String? =
        database.openHelper.readableDatabase
            .query(
                "SELECT resolutionStatus FROM exercise_conflicts WHERE exerciseId = ? LIMIT 1",
                arrayOf(exerciseId),
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
}

private class FakeClock : Clock { override fun nowEpochMs(): Long = 10 }
private class FakeUuidProvider : UuidProvider {
    private var index = 0
    override fun newUuid(): String = "operation-${++index}"
}
private class FakeDomainEventDispatcher : DomainEventDispatcher {
    override suspend fun publish(event: DomainEvent) = Unit
    override fun <T : DomainEvent> register(type: Class<T>, handler: DomainEventHandler<T>) = Unit
}
private class FakeSyncEnqueuer : SyncEnqueuer {
    override fun enqueue() = Unit
    override fun cancel() = Unit
}
