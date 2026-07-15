package at.fitnessplatform.domain

import at.fitnessplatform.core.model.SyncStatus
import at.fitnessplatform.core.model.Workout
import at.fitnessplatform.core.model.WorkoutExercise
import at.fitnessplatform.core.model.WorkoutStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepeatWorkoutUseCaseTest {
    @Test
    fun `completed source creates one distinct planned copy without changing source`() = runTest {
        val source = completedWorkout()
        val repository = RecordingWorkoutRepository(source)

        val repeated = RepeatWorkoutUseCase(repository)(source.id)

        assertNotEquals(source.id, repeated.id)
        assertEquals(WorkoutStatus.PLANNED, repeated.status)
        assertEquals(source.title, repeated.title)
        assertEquals("trimmed notes", repeated.notes)
        assertEquals(listOf("first", "second"), repeated.exercises.map { it.exerciseId })
        assertEquals(1, repository.createCalls)
        assertEquals(source, repository.source)
    }

    @Test
    fun `missing source is rejected without create`() = runTest {
        val repository = RecordingWorkoutRepository(null)

        val failure = runCatching { RepeatWorkoutUseCase(repository)("missing") }.exceptionOrNull()

        assertTrue(failure is ValidationException)
        assertEquals(0, repository.createCalls)
    }

    @Test
    fun `every non completed source state is rejected without create`() = runTest {
        listOf(
            WorkoutStatus.PLANNED,
            WorkoutStatus.IN_PROGRESS,
            WorkoutStatus.PAUSED,
            WorkoutStatus.CANCELLED,
        ).forEach { status ->
            val repository = RecordingWorkoutRepository(completedWorkout().copy(status = status))

            val failure = runCatching { RepeatWorkoutUseCase(repository)("source") }.exceptionOrNull()

            assertTrue("Expected validation failure for $status", failure is ValidationException)
            assertEquals(0, repository.createCalls)
        }
    }

    @Test
    fun `repository validation failure is propagated without retry`() = runTest {
        val expected = ValidationException("Exercise unavailable")
        val repository = RecordingWorkoutRepository(completedWorkout(), createFailure = expected)

        val failure = runCatching { RepeatWorkoutUseCase(repository)("source") }.exceptionOrNull()

        assertEquals(expected, failure)
        assertEquals(1, repository.createCalls)
    }

    private fun completedWorkout() = Workout(
        id = "source",
        ownerProfileId = "profile",
        title = "Completed session",
        status = WorkoutStatus.COMPLETED,
        notes = "  trimmed notes  ",
        exercises = listOf(
            WorkoutExercise("link-2", "source", "second", 1),
            WorkoutExercise("link-1", "source", "first", 0),
        ),
        createdAtEpochMs = 1,
        updatedAtEpochMs = 2,
        syncStatus = SyncStatus.SYNCED,
    )
}

private class RecordingWorkoutRepository(
    var source: Workout?,
    private val createFailure: Throwable? = null,
) : WorkoutRepository {
    var createCalls = 0

    override fun observeWorkouts(): Flow<List<Workout>> = flowOf(listOfNotNull(source))

    override suspend fun getWorkout(id: String): Workout? = source?.takeIf { it.id == id }

    override suspend fun create(title: String, exerciseIds: List<String>, notes: String): Workout {
        createCalls++
        createFailure?.let { throw it }
        return Workout(
            id = "repeated",
            ownerProfileId = source?.ownerProfileId.orEmpty(),
            title = title,
            notes = notes,
            exercises = exerciseIds.mapIndexed { index, exerciseId ->
                WorkoutExercise("new-$index", "repeated", exerciseId, index)
            },
            createdAtEpochMs = 3,
            updatedAtEpochMs = 3,
        )
    }

    override suspend fun start(id: String): Workout = error("Not used")

    override suspend fun complete(id: String): Workout = error("Not used")
}
