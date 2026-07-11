package at.fitnessplatform.feature.main

import at.fitnessplatform.core.model.*
import at.fitnessplatform.core.testing.MainDispatcherRule
import at.fitnessplatform.domain.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlatformViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test fun `view model exposes success and validation error states`() = runTest {
        val profiles = FakeProfileRepository()
        val exercises = FakeExerciseRepository()
        val workouts = FakeWorkoutRepository()
        val viewModel = PlatformViewModel(
            observeProfile = ObserveProfileUseCase(profiles),
            observeExercises = ObserveExercisesUseCase(exercises),
            observeWorkouts = ObserveWorkoutsUseCase(workouts),
            observeConflicts = ObserveExerciseConflictsUseCase(exercises),
            createProfile = CreateGuestProfileUseCase(profiles),
            updateProfile = UpdateGuestProfileUseCase(profiles),
            createExercise = CreateExerciseUseCase(exercises),
            updateExercise = UpdateExerciseUseCase(exercises),
            deleteExercise = DeleteExerciseUseCase(exercises),
            resolveExerciseConflict = ResolveExerciseConflictUseCase(exercises),
            createWorkout = CreateWorkoutUseCase(workouts),
            startWorkout = StartWorkoutUseCase(workouts),
            completeWorkout = CompleteWorkoutUseCase(workouts),
        )
        val values = mutableListOf<PlatformUiState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { values += it } }
        viewModel.createGuest("Tester")
        advanceUntilIdle()
        assertEquals("Tester", values.last().profile?.displayName)
        viewModel.saveExercise(null, "", "", "Legs", "None", TrackingType.REPS, "") {}
        advanceUntilIdle()
        assertNotNull(values.last().errorMessage)
        assertFalse(values.last().operationInProgress)
        job.cancel()
    }
}

private class FakeProfileRepository : GuestProfileRepository {
    private val state = MutableStateFlow<GuestProfile?>(null)
    override fun observeProfile(): Flow<GuestProfile?> = state
    override suspend fun getProfile() = state.value
    override suspend fun create(displayName: String) = GuestProfile("p", displayName, 1).also { state.value = it }
    override suspend fun update(profile: GuestProfile) = profile.also { state.value = it }
}
private class FakeExerciseRepository : ExerciseRepository {
    private val state = MutableStateFlow<List<CustomExercise>>(emptyList())
    private val conflicts = MutableStateFlow<List<ExerciseConflict>>(emptyList())
    override fun observeExercises(): Flow<List<CustomExercise>> = state
    override fun observeExerciseConflicts(): Flow<List<ExerciseConflict>> = conflicts
    override suspend fun getExercise(id: String) = state.value.firstOrNull { it.id == id }
    override suspend fun create(name: String, description: String, primaryMuscleGroup: String, requiredEquipment: String, trackingType: TrackingType, notes: String) =
        CustomExercise("e", "p", name, description, primaryMuscleGroup, requiredEquipment, trackingType, notes, 1, 1).also { state.value += it }
    override suspend fun update(exercise: CustomExercise) = exercise.also { updated -> state.value = state.value.map { if (it.id == updated.id) updated else it } }
    override suspend fun delete(id: String) { state.value = state.value.filterNot { it.id == id } }
    override suspend fun resolveConflict(exerciseId: String, resolution: ExerciseConflictResolution, mergedExercise: CustomExercise?) = Unit
}
private class FakeWorkoutRepository : WorkoutRepository {
    private val state = MutableStateFlow<List<Workout>>(emptyList())
    override fun observeWorkouts(): Flow<List<Workout>> = state
    override suspend fun getWorkout(id: String) = state.value.firstOrNull { it.id == id }
    override suspend fun create(title: String, exerciseIds: List<String>, notes: String) = Workout("w", "p", title, notes = notes, createdAtEpochMs = 1, updatedAtEpochMs = 1).also { state.value += it }
    override suspend fun start(id: String) = requireNotNull(getWorkout(id)).copy(status = WorkoutStatus.IN_PROGRESS).also { update(it) }
    override suspend fun complete(id: String) = requireNotNull(getWorkout(id)).copy(status = WorkoutStatus.COMPLETED).also { update(it) }
    private fun update(workout: Workout) { state.value = state.value.map { if (it.id == workout.id) workout else it } }
}
