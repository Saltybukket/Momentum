package at.fitnessplatform.feature.main

import at.fitnessplatform.core.model.CustomExercise
import at.fitnessplatform.core.model.TrackingType
import at.fitnessplatform.core.model.Workout
import at.fitnessplatform.core.model.WorkoutExercise
import at.fitnessplatform.core.model.WorkoutStatus
import at.fitnessplatform.core.designsystem.MomentumStatusVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutDetailUiTest {
    @Test
    fun `exercise resolution from state finds and marks missing references`() {
        val workout = completedWorkout()
        val exercises = listOf(exercise("available"))

        val map = exercises.associateBy { it.id }
        val resolved = workout.exercises.map { it.exerciseId to map[it.exerciseId] }

        assertEquals(2, resolved.size)
        assertEquals("available", resolved[1].first)
        assertNotNull(resolved[1].second)
        assertEquals("missing", resolved[0].first)
        assertNull(resolved[0].second)
    }

    @Test
    fun `detail route belongs to workouts and uses Up navigation`() {
        val route = "workout-detail/workout-42"

        assertEquals("workouts", rootRouteFor(route))
        assertTrue(showsUpNavigation(route))
    }

    @Test
    fun `workout status maps correctly`() {
        assertEquals(MomentumStatusVariant.COMPLETED, workoutStatusVariant(WorkoutStatus.COMPLETED))
        assertEquals(MomentumStatusVariant.PLANNED, workoutStatusVariant(WorkoutStatus.PLANNED))
        assertEquals(MomentumStatusVariant.ACTIVE, workoutStatusVariant(WorkoutStatus.IN_PROGRESS))
        assertEquals(MomentumStatusVariant.PAUSED, workoutStatusVariant(WorkoutStatus.PAUSED))
        assertEquals(MomentumStatusVariant.CANCELLED, workoutStatusVariant(WorkoutStatus.CANCELLED))
    }

    @Test
    fun `active workout finds in progress or paused and recent excludes both`() {
        val state = PlatformUiState(
            isLoading = false,
            workouts = listOf(
                completedWorkout().copy(id = "w1", status = WorkoutStatus.COMPLETED),
                completedWorkout().copy(id = "w2", status = WorkoutStatus.PLANNED),
                completedWorkout().copy(id = "w3", status = WorkoutStatus.IN_PROGRESS),
                completedWorkout().copy(id = "w4", status = WorkoutStatus.PAUSED),
            ),
        )

        assertEquals("w3", state.activeWorkout?.id)
        assertEquals(1, state.recentWorkouts.size)
        assertEquals(setOf("w1"), state.recentWorkouts.map { it.id }.toSet())
    }

    private fun completedWorkout() = Workout(
        id = "workout",
        ownerProfileId = "profile",
        title = "Completed",
        status = WorkoutStatus.COMPLETED,
        exercises = listOf(
            WorkoutExercise("we1", "workout", "missing", 1),
            WorkoutExercise("we2", "workout", "available", 0),
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
