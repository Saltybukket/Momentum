package at.fitnessplatform.feature.main

import at.fitnessplatform.core.model.*
import at.fitnessplatform.core.testing.MainDispatcherRule
import at.fitnessplatform.domain.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
            syncPreferences = PlatformFakeSyncPreferencesRepository(),
            createProfile = CreateGuestProfileUseCase(profiles),
            updateProfile = UpdateGuestProfileUseCase(profiles),
            createExercise = CreateExerciseUseCase(exercises),
            updateExercise = UpdateExerciseUseCase(exercises),
            deleteExercise = DeleteExerciseUseCase(exercises),
            resolveExerciseConflict = ResolveExerciseConflictUseCase(exercises),
            createWorkout = CreateWorkoutUseCase(workouts),
            startWorkout = StartWorkoutUseCase(workouts),
            completeWorkout = CompleteWorkoutUseCase(workouts),
            repeatWorkout = RepeatWorkoutUseCase(workouts),
        )
        val values = mutableListOf<PlatformUiState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { values += it } }
        advanceUntilIdle()
        assertEquals(null, values.last().profile)
        assertEquals(null, values.last().activeWorkout)
        assertEquals(emptyList<Workout>(), values.last().recentWorkouts)
        viewModel.createGuest("Tester")
        advanceUntilIdle()
        assertEquals("Tester", values.last().profile?.displayName)
        viewModel.saveExercise(null, "", "", "Legs", "None", TrackingType.REPS, "") {}
        advanceUntilIdle()
        assertNotNull(values.last().errorMessage)
        assertFalse(values.last().operationInProgress)
        job.cancel()
    }

    @Test fun `dashboard exposes disabled and blocked private sync without inventing data`() = runTest {
        val profiles = FakeProfileRepository(GuestProfile("p", "Offline Guest", 1))
        val exercises = FakeExerciseRepository()
        val workouts = FakeWorkoutRepository()
        val sync = PlatformFakeSyncPreferencesRepository(
            enabled = false,
            pending = 0,
            credential = GuestCredentialStatus.RECOVERY_REJECTED,
        )
        val viewModel = createViewModel(profiles, exercises, workouts, sync)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.syncEnabled)
        assertEquals(GuestCredentialStatus.RECOVERY_REJECTED, viewModel.uiState.value.credentialStatus)
        assertEquals(0, viewModel.uiState.value.pendingSyncCount)
        assertEquals(emptyList<Workout>(), viewModel.uiState.value.workouts)
        job.cancel()
    }

    @Test fun `root navigation adapts only at the wide layout breakpoint`() {
        assertFalse(usesNavigationRail(839.dp))
        assertTrue(usesNavigationRail(840.dp))
    }

    @Test fun `dashboard derives active recent conflict and sync state from real flows`() = runTest {
        val profiles = FakeProfileRepository(GuestProfile("p", "Momentum Guest", 1))
        val exercises = FakeExerciseRepository().apply { addConflict() }
        val workouts = FakeWorkoutRepository().apply {
            add(Workout("active", "p", "Active", status = WorkoutStatus.IN_PROGRESS, createdAtEpochMs = 3, updatedAtEpochMs = 3))
            add(Workout("done", "p", "Done", status = WorkoutStatus.COMPLETED, createdAtEpochMs = 2, updatedAtEpochMs = 2))
        }
        val sync = PlatformFakeSyncPreferencesRepository(enabled = true, pending = 2)
        val viewModel = createViewModel(profiles, exercises, workouts, sync)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("active", state.activeWorkout?.id)
        assertEquals(listOf("done"), state.recentWorkouts.map { it.id })
        assertEquals(1, state.conflicts.size)
        assertEquals(true, state.syncEnabled)
        assertEquals(2, state.pendingSyncCount)
        job.cancel()
    }

    @Test fun `repeat success navigates once and bridges delayed Room emission`() = runTest {
        val workouts = FakeWorkoutRepository().apply {
            add(completedWorkout())
            publishCreates = false
        }
        val viewModel = createViewModel(
            FakeProfileRepository(GuestProfile("p", "Guest", 1)),
            FakeExerciseRepository(),
            workouts,
            PlatformFakeSyncPreferencesRepository(),
        )
        val states = mutableListOf<PlatformUiState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { states += it }
        }
        val navigated = mutableListOf<String>()

        viewModel.repeatWorkout("completed", navigated::add)
        advanceUntilIdle()

        assertEquals(listOf("created-1"), navigated)
        assertEquals(1, workouts.createCalls)
        assertTrue(states.last().workouts.any { it.id == "created-1" })
        assertFalse(states.last().operationInProgress)
        job.cancel()
    }

    @Test fun `repeat failure exposes error and never navigates`() = runTest {
        val workouts = FakeWorkoutRepository().apply {
            add(completedWorkout())
            createFailure = ValidationException("Exercise unavailable")
        }
        val viewModel = createViewModel(
            FakeProfileRepository(GuestProfile("p", "Guest", 1)),
            FakeExerciseRepository(),
            workouts,
            PlatformFakeSyncPreferencesRepository(),
        )
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        val navigated = mutableListOf<String>()

        viewModel.repeatWorkout("completed", navigated::add)
        advanceUntilIdle()

        assertTrue(navigated.isEmpty())
        assertEquals("Exercise unavailable", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.operationInProgress)
        job.cancel()
    }

    @Test fun `two immediate repeats reserve one operation and Busy resets after suspension`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val workouts = FakeWorkoutRepository().apply {
            add(completedWorkout())
            createGate = gate
        }
        val viewModel = createViewModel(
            FakeProfileRepository(GuestProfile("p", "Guest", 1)),
            FakeExerciseRepository(),
            workouts,
            PlatformFakeSyncPreferencesRepository(),
        )
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()
        val navigated = mutableListOf<String>()

        viewModel.repeatWorkout("completed", navigated::add)
        viewModel.repeatWorkout("completed", navigated::add)
        runCurrent()

        assertTrue(viewModel.uiState.value.operationInProgress)
        assertEquals(1, workouts.createCalls)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("created-1"), navigated)
        assertFalse(viewModel.uiState.value.operationInProgress)
        job.cancel()
    }

    @Test fun `cancelled operation resets Busy without navigation or error`() = runTest {
        val workouts = FakeWorkoutRepository().apply {
            add(completedWorkout())
            createFailure = CancellationException("cancelled")
        }
        val viewModel = createViewModel(
            FakeProfileRepository(GuestProfile("p", "Guest", 1)),
            FakeExerciseRepository(),
            workouts,
            PlatformFakeSyncPreferencesRepository(),
        )
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        val navigated = mutableListOf<String>()

        viewModel.repeatWorkout("completed", navigated::add)
        advanceUntilIdle()

        assertTrue(navigated.isEmpty())
        assertFalse(viewModel.uiState.value.operationInProgress)
        assertEquals(null, viewModel.uiState.value.errorMessage)
        job.cancel()
    }

    @Test fun `paused remains active while completed remains recent`() = runTest {
        val workouts = FakeWorkoutRepository().apply {
            add(Workout("paused", "p", "Paused", status = WorkoutStatus.PAUSED, createdAtEpochMs = 2, updatedAtEpochMs = 2))
            add(completedWorkout())
        }
        val viewModel = createViewModel(
            FakeProfileRepository(GuestProfile("p", "Guest", 1)),
            FakeExerciseRepository(),
            workouts,
            PlatformFakeSyncPreferencesRepository(),
        )
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        assertEquals("paused", viewModel.uiState.value.activeWorkout?.id)
        assertEquals(listOf("completed"), viewModel.uiState.value.recentWorkouts.map { it.id })
        job.cancel()
    }

    private fun createViewModel(
        profiles: FakeProfileRepository,
        exercises: FakeExerciseRepository,
        workouts: FakeWorkoutRepository,
        sync: PlatformFakeSyncPreferencesRepository,
    ) = PlatformViewModel(
        observeProfile = ObserveProfileUseCase(profiles),
        observeExercises = ObserveExercisesUseCase(exercises),
        observeWorkouts = ObserveWorkoutsUseCase(workouts),
        observeConflicts = ObserveExerciseConflictsUseCase(exercises),
        syncPreferences = sync,
        createProfile = CreateGuestProfileUseCase(profiles),
        updateProfile = UpdateGuestProfileUseCase(profiles),
        createExercise = CreateExerciseUseCase(exercises),
        updateExercise = UpdateExerciseUseCase(exercises),
        deleteExercise = DeleteExerciseUseCase(exercises),
        resolveExerciseConflict = ResolveExerciseConflictUseCase(exercises),
        createWorkout = CreateWorkoutUseCase(workouts),
        startWorkout = StartWorkoutUseCase(workouts),
        completeWorkout = CompleteWorkoutUseCase(workouts),
        repeatWorkout = RepeatWorkoutUseCase(workouts),
    )

    private fun completedWorkout() = Workout(
        "completed",
        "p",
        "Completed",
        status = WorkoutStatus.COMPLETED,
        exercises = listOf(WorkoutExercise("link", "completed", "exercise", 0)),
        createdAtEpochMs = 1,
        updatedAtEpochMs = 1,
    )
}

private class FakeProfileRepository(initial: GuestProfile? = null) : GuestProfileRepository {
    private val state = MutableStateFlow(initial)
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
    fun addConflict() {
        val local = CustomExercise("e", "p", "Local", "", "Legs", "None", TrackingType.REPS, "", 1, 1)
        conflicts.value = listOf(
            ExerciseConflict(
                id = "c",
                exerciseId = "e",
                type = ExerciseConflictType.BOTH_MODIFIED,
                localRevision = 1,
                remoteRevision = 2,
                localSnapshot = local,
                remoteSnapshot = local.copy(name = "Remote"),
                detectedAtEpochMs = 2,
                resolutionStatus = ConflictResolutionStatus.OPEN,
            ),
        )
    }
}
private class FakeWorkoutRepository : WorkoutRepository {
    private val state = MutableStateFlow<List<Workout>>(emptyList())
    var createCalls = 0
    var createFailure: Throwable? = null
    var createGate: CompletableDeferred<Unit>? = null
    var publishCreates = true
    override fun observeWorkouts(): Flow<List<Workout>> = state
    override suspend fun getWorkout(id: String) = state.value.firstOrNull { it.id == id }
    override suspend fun create(title: String, exerciseIds: List<String>, notes: String): Workout {
        createCalls++
        createGate?.await()
        createFailure?.let { throw it }
        val id = "created-$createCalls"
        return Workout(
            id,
            "p",
            title,
            notes = notes,
            exercises = exerciseIds.mapIndexed { index, exerciseId ->
                WorkoutExercise("$id-link-$index", id, exerciseId, index)
            },
            createdAtEpochMs = 1,
            updatedAtEpochMs = 1,
        ).also { if (publishCreates) state.value += it }
    }
    override suspend fun start(id: String) = requireNotNull(getWorkout(id)).copy(status = WorkoutStatus.IN_PROGRESS).also { update(it) }
    override suspend fun complete(id: String) = requireNotNull(getWorkout(id)).copy(status = WorkoutStatus.COMPLETED).also { update(it) }
    private fun update(workout: Workout) { state.value = state.value.map { if (it.id == workout.id) workout else it } }
    fun add(workout: Workout) { state.value += workout }
}

private class PlatformFakeSyncPreferencesRepository(
    enabled: Boolean = false,
    pending: Int = 0,
    credential: GuestCredentialStatus = GuestCredentialStatus.READY,
) : SyncPreferencesRepository {
    private val enabledState = MutableStateFlow(enabled)
    private val pendingState = MutableStateFlow(pending)
    private val credentialState = MutableStateFlow(credential)
    override fun observeEnabled() = enabledState
    override fun observePendingCount() = pendingState
    override fun observeCredentialState() = credentialState
    override suspend fun setEnabled(enabled: Boolean) { enabledState.value = enabled }
    override suspend fun resetCredentialsForNewIdentity() { credentialState.value = GuestCredentialStatus.READY }
}
