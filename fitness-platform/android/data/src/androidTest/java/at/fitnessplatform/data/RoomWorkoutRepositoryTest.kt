package at.fitnessplatform.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import at.fitnessplatform.core.database.AppDatabase
import at.fitnessplatform.core.database.toEntity
import at.fitnessplatform.core.model.Clock
import at.fitnessplatform.core.model.CustomExercise
import at.fitnessplatform.core.model.DomainEvent
import at.fitnessplatform.core.model.GuestProfile
import at.fitnessplatform.core.model.OutboxOperationType
import at.fitnessplatform.core.model.TrackingType
import at.fitnessplatform.core.model.UuidProvider
import at.fitnessplatform.core.model.Workout
import at.fitnessplatform.core.model.WorkoutStatus
import at.fitnessplatform.domain.DomainEventDispatcher
import at.fitnessplatform.domain.DomainEventHandler
import at.fitnessplatform.domain.SyncEnqueuer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomWorkoutRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: RoomWorkoutRepository
    private lateinit var events: RecordingEventDispatcher
    private lateinit var sync: RecordingSyncEnqueuer
    private var idSequence = 0
    private var now = 1_000L

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        database.guestProfileDao().insert(GuestProfile("profile", "Guest", 1).toEntity())
        events = RecordingEventDispatcher()
        sync = RecordingSyncEnqueuer()
        repository = RoomWorkoutRepository(
            database = database,
            profileDao = database.guestProfileDao(),
            exerciseDao = database.exerciseDao(),
            workoutDao = database.workoutDao(),
            outboxDao = database.outboxDao(),
            ids = object : UuidProvider {
                override fun newUuid(): String = "id-${idSequence++}"
            },
            clock = object : Clock {
                override fun nowEpochMs(): Long = now++
            },
            events = events,
            syncEnqueuer = sync,
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun validCreatePersistsOrderedLinksAndOneUpsertOutbox() = runTest {
        insertExercise("first")
        insertExercise("second")

        val workout = repository.create("Session", listOf("second", "first"), "notes")

        val stored = database.workoutDao().get(workout.id, "profile")
        assertEquals(listOf("second", "first"), stored?.exercises?.sortedBy { it.position }?.map { it.exerciseId })
        assertEquals(1, outboxCount(OutboxOperationType.UPSERT_WORKOUT))
        assertEquals(1, events.values.size)
        assertEquals(1, sync.enqueueCalls)
    }

    @Test
    fun softDeletedExerciseRejectsCreateWithoutAnyTrace() = runTest {
        insertExercise("deleted", deletedAt = 5)

        val failure = runCatching { repository.create("Session", listOf("deleted"), "") }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertNoWorkoutTrace()
    }

    @Test
    fun foreignOwnerExerciseRejectsCreateWithoutAnyTrace() = runTest {
        database.guestProfileDao().insert(GuestProfile("foreign", "Foreign", 2).toEntity())
        insertExercise("foreign-exercise", owner = "foreign")

        val failure = runCatching {
            repository.create("Session", listOf("foreign-exercise"), "")
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertNoWorkoutTrace()
    }

    @Test
    fun plannedStartCreatesOneOutboxAndInProgressRetryIsIdempotent() = runTest {
        val workout = repository.create("Session", emptyList(), "")

        val started = repository.start(workout.id)
        val repeated = repository.start(workout.id)

        assertEquals(WorkoutStatus.IN_PROGRESS, started.status)
        assertEquals(started, repeated)
        assertEquals(1, outboxCount(OutboxOperationType.START_WORKOUT))
        assertEquals(2, events.values.size)
        assertEquals(2, sync.enqueueCalls)
    }

    @Test fun pausedStartRejectsWithoutMutationOrOutbox() = runTest {
        assertStartRejected(WorkoutStatus.PAUSED)
    }

    @Test fun completedStartRejectsWithoutMutationOrOutbox() = runTest {
        assertStartRejected(WorkoutStatus.COMPLETED)
    }

    @Test fun cancelledStartRejectsWithoutMutationOrOutbox() = runTest {
        assertStartRejected(WorkoutStatus.CANCELLED)
    }

    @Test
    fun foreignOwnerWorkoutIsInvisibleAndCannotMutate() = runTest {
        database.guestProfileDao().insert(GuestProfile("foreign", "Foreign", 2).toEntity())
        val foreign = Workout(
            id = "foreign-workout",
            ownerProfileId = "foreign",
            title = "Foreign",
            createdAtEpochMs = 1,
            updatedAtEpochMs = 1,
        )
        database.workoutDao().insertWorkout(foreign.toEntity())

        assertTrue(repository.observeWorkouts().first().none { it.id == foreign.id })
        assertNull(repository.getWorkout(foreign.id))
        assertTrue(runCatching { repository.start(foreign.id) }.isFailure)
        assertTrue(runCatching { repository.complete(foreign.id) }.isFailure)
        assertEquals(WorkoutStatus.PLANNED.name, unscopedWorkoutStatus(foreign.id))
        assertEquals(0, database.outboxDao().pending().size)
        assertTrue(events.values.isEmpty())
    }

    private suspend fun assertStartRejected(status: WorkoutStatus) {
        val workout = repository.create("Session", emptyList(), "")
        val row = requireNotNull(database.workoutDao().get(workout.id, "profile"))
        database.workoutDao().updateWorkout(row.workout.copy(status = status.name))
        val eventsBefore = events.values.size
        val syncBefore = sync.enqueueCalls

        val failure = runCatching { repository.start(workout.id) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(status, repository.getWorkout(workout.id)?.status)
        assertEquals(0, outboxCount(OutboxOperationType.START_WORKOUT))
        assertEquals(eventsBefore, events.values.size)
        assertEquals(syncBefore, sync.enqueueCalls)
    }

    private suspend fun insertExercise(
        id: String,
        owner: String = "profile",
        deletedAt: Long? = null,
    ) {
        database.exerciseDao().insert(
            CustomExercise(
                id = id,
                ownerProfileId = owner,
                name = id,
                primaryMuscleGroup = "Legs",
                requiredEquipment = "None",
                trackingType = TrackingType.REPS,
                createdAtEpochMs = 1,
                updatedAtEpochMs = 1,
                deletedAtEpochMs = deletedAt,
            ).toEntity(),
        )
    }

    private suspend fun outboxCount(type: OutboxOperationType): Int =
        database.outboxDao().pending().count { it.operationType == type.name }

    private suspend fun assertNoWorkoutTrace() {
        assertTrue(repository.observeWorkouts().first().isEmpty())
        assertTrue(database.outboxDao().pending().isEmpty())
        assertTrue(events.values.isEmpty())
        assertEquals(0, sync.enqueueCalls)
    }

    private fun unscopedWorkoutStatus(id: String): String? {
        database.openHelper.readableDatabase.query("SELECT status FROM workouts WHERE id = ?", arrayOf(id)).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }
}

private class RecordingEventDispatcher : DomainEventDispatcher {
    val values = mutableListOf<DomainEvent>()

    override suspend fun publish(event: DomainEvent) {
        values += event
    }

    override fun <T : DomainEvent> register(type: Class<T>, handler: DomainEventHandler<T>) = Unit
}

private class RecordingSyncEnqueuer : SyncEnqueuer {
    var enqueueCalls = 0

    override fun enqueue() {
        enqueueCalls++
    }

    override fun cancel() = Unit
}
