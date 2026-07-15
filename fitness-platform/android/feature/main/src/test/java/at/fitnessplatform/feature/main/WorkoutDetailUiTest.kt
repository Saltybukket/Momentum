package at.fitnessplatform.feature.main

import at.fitnessplatform.core.model.CustomExercise
import at.fitnessplatform.core.model.TrackingType
import at.fitnessplatform.core.model.Workout
import at.fitnessplatform.core.model.WorkoutExercise
import at.fitnessplatform.core.model.WorkoutStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutDetailUiTest {
    @Test
    fun `exercise resolution is ordered and marks missing references`() {
        val workout = completedWorkout()

        val resolved = resolveWorkoutExercises(workout, listOf(exercise("available")))

        assertEquals(listOf("available", "missing"), resolved.map { it.link.exerciseId })
        assertEquals("Available", resolved.first().exercise?.name)
        assertNull(resolved.last().exercise)
    }

    @Test
    fun `repeat requires completed source and every exercise`() {
        val workout = completedWorkout()
        val available = listOf(exercise("available"), exercise("missing"))

        val allowed = workoutDetailState(
            workout.id,
            PlatformUiState(isLoading = false, workouts = listOf(workout), exercises = available),
        ) as WorkoutDetailUiState.Content
        val missing = workoutDetailState(
            workout.id,
            PlatformUiState(isLoading = false, workouts = listOf(workout), exercises = available.take(1)),
        ) as WorkoutDetailUiState.Content
        val planned = workoutDetailState(
            workout.id,
            PlatformUiState(
                isLoading = false,
                workouts = listOf(workout.copy(status = WorkoutStatus.PLANNED)),
                exercises = available,
            ),
        ) as WorkoutDetailUiState.Content

        assertTrue(allowed.repeatAllowed)
        assertFalse(missing.repeatAllowed)
        assertFalse(planned.repeatAllowed)
    }

    @Test
    fun `detail route belongs to workouts and uses Up navigation`() {
        val route = workoutDetailRoute("workout-42")

        assertEquals("workout-detail/workout-42", route)
        assertEquals("workouts", rootRouteFor(route))
        assertTrue(showsUpNavigation(route))
    }

    @Test
    fun `loading and unknown IDs remain stable states`() {
        assertEquals(
            WorkoutDetailUiState.Loading,
            workoutDetailState("unknown", PlatformUiState()),
        )
        assertEquals(
            WorkoutDetailUiState.NotFound,
            workoutDetailState("unknown", PlatformUiState(isLoading = false)),
        )
    }

    private fun completedWorkout() = Workout(
        id = "workout",
        ownerProfileId = "profile",
        title = "Completed",
        status = WorkoutStatus.COMPLETED,
        exercises = listOf(
            WorkoutExercise("second-link", "workout", "missing", 1),
            WorkoutExercise("first-link", "workout", "available", 0),
        ),
        createdAtEpochMs = 1,
        updatedAtEpochMs = 2,
    )

    private fun exercise(id: String) = CustomExercise(
        id = id,
        ownerProfileId = "profile",
        name = if (id == "available") "Available" else "Second",
        primaryMuscleGroup = "Legs",
        requiredEquipment = "None",
        trackingType = TrackingType.REPS,
        createdAtEpochMs = 1,
        updatedAtEpochMs = 1,
    )
}
