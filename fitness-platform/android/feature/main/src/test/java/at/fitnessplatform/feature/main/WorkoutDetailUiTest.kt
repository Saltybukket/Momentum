package at.fitnessplatform.feature.main

import at.fitnessplatform.core.designsystem.MomentumStatusVariant
import at.fitnessplatform.core.model.CustomExercise
import at.fitnessplatform.core.model.TrackingType
import at.fitnessplatform.core.model.Workout
import at.fitnessplatform.core.model.WorkoutExercise
import at.fitnessplatform.core.model.WorkoutStatus
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutDetailUiTest {
    @Test
    fun `production resolver sorts links and marks missing references`() {
        val resolved = resolveWorkoutExercises(completedWorkout(), listOf(exercise("available")))

        assertEquals(listOf("available", "missing"), resolved.map { it.link.exerciseId })
        assertEquals("Available", resolved.first().exercise?.name)
        assertNull(resolved.last().exercise)
    }

    @Test
    fun `detail remains loading before platform state is ready`() {
        assertEquals(
            WorkoutDetailUiState.Loading,
            workoutDetailState("workout", PlatformUiState(isLoading = true)),
        )
    }

    @Test
    fun `unknown workout becomes not found only after loading`() {
        assertEquals(
            WorkoutDetailUiState.NotFound,
            workoutDetailState("unknown", PlatformUiState(isLoading = false)),
        )
    }

    @Test
    fun `repeat requires completed workout and every exercise resolved`() {
        val completed = completedWorkout()
        val resolvedState = PlatformUiState(
            isLoading = false,
            workouts = listOf(completed),
            exercises = listOf(exercise("available"), exercise("missing")),
        )
        val missingState = resolvedState.copy(exercises = listOf(exercise("available")))

        assertTrue((workoutDetailState(completed.id, resolvedState) as WorkoutDetailUiState.Content).repeatAllowed)
        assertFalse((workoutDetailState(completed.id, missingState) as WorkoutDetailUiState.Content).repeatAllowed)
        listOf(
            WorkoutStatus.PLANNED,
            WorkoutStatus.IN_PROGRESS,
            WorkoutStatus.PAUSED,
            WorkoutStatus.CANCELLED,
        ).forEach { status ->
            val state = resolvedState.copy(workouts = listOf(completed.copy(status = status)))
            assertFalse((workoutDetailState(completed.id, state) as WorkoutDetailUiState.Content).repeatAllowed)
        }
    }

    @Test
    fun `detail route validates route-safe IDs`() {
        assertEquals("workout-detail/workout-42", workoutDetailRoute("workout-42"))
        assertEquals("workouts", rootRouteFor(workoutDetailRoute("workout-42")))
        assertTrue(showsUpNavigation(workoutDetailRoute("workout-42")))
        assertThrows(IllegalArgumentException::class.java) { workoutDetailRoute("") }
        assertThrows(IllegalArgumentException::class.java) { workoutDetailRoute("folder/workout") }
    }

    @Test
    fun `date formatting is deterministic for fixed locale and zone`() {
        assertEquals(
            "01.01.1970, 01:00:00",
            formatWorkoutDateTime(0, Locale.GERMANY, ZoneId.of("Europe/Berlin")),
        )
    }

    @Test
    fun `every workout status maps to its visual status`() {
        assertEquals(
            mapOf(
                WorkoutStatus.PLANNED to MomentumStatusVariant.PLANNED,
                WorkoutStatus.IN_PROGRESS to MomentumStatusVariant.ACTIVE,
                WorkoutStatus.PAUSED to MomentumStatusVariant.PAUSED,
                WorkoutStatus.COMPLETED to MomentumStatusVariant.COMPLETED,
                WorkoutStatus.CANCELLED to MomentumStatusVariant.CANCELLED,
            ),
            WorkoutStatus.entries.associateWith(::workoutStatusVariant),
        )
    }

    @Test
    fun `dashboard recent contains completed workouts only`() {
        val workouts = WorkoutStatus.entries.mapIndexed { index, status ->
            completedWorkout().copy(id = "w$index", status = status)
        }
        val state = PlatformUiState(isLoading = false, workouts = workouts)

        assertEquals(listOf(WorkoutStatus.COMPLETED), state.recentWorkouts.map { it.status })
    }

    @Test
    fun `next planned workout uses earliest creation time deterministically`() {
        val state = PlatformUiState(
            isLoading = false,
            workouts = listOf(
                completedWorkout().copy(id = "later", status = WorkoutStatus.PLANNED, createdAtEpochMs = 20),
                completedWorkout().copy(id = "completed", status = WorkoutStatus.COMPLETED, createdAtEpochMs = 1),
                completedWorkout().copy(id = "earlier", status = WorkoutStatus.PLANNED, createdAtEpochMs = 10),
            ),
        )

        assertEquals("earlier", state.nextPlannedWorkout?.id)
    }

    @Test
    fun `planned home hero explicitly opens rather than starts workout`() {
        assertEquals(R.string.home_view_workout, homeHeroActionLabel(false, true))
        assertEquals(R.string.home_continue_workout, homeHeroActionLabel(true, true))
        assertEquals(R.string.home_start_workout, homeHeroActionLabel(false, false))
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
