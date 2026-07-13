package at.fitnessplatform.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.fitnessplatform.core.model.CustomExercise
import at.fitnessplatform.core.model.ExerciseConflict
import at.fitnessplatform.core.model.ExerciseConflictResolution
import at.fitnessplatform.core.model.GuestProfile
import at.fitnessplatform.core.model.TrackingType
import at.fitnessplatform.core.model.Workout
import at.fitnessplatform.core.model.WorkoutStatus
import at.fitnessplatform.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlatformUiState(
    val isLoading: Boolean = true,
    val profile: GuestProfile? = null,
    val exercises: List<CustomExercise> = emptyList(),
    val conflicts: List<ExerciseConflict> = emptyList(),
    val workouts: List<Workout> = emptyList(),
    val syncEnabled: Boolean = false,
    val pendingSyncCount: Int = 0,
    val credentialStatus: GuestCredentialStatus = GuestCredentialStatus.READY,
    val operationInProgress: Boolean = false,
    val errorMessage: String? = null,
) {
    val activeWorkout: Workout?
        get() = workouts.firstOrNull { it.status == WorkoutStatus.IN_PROGRESS }

    val recentWorkouts: List<Workout>
        get() = workouts.filter { it.status != WorkoutStatus.IN_PROGRESS }.take(3)
}

@HiltViewModel
@Suppress("LongParameterList")
class PlatformViewModel @Inject constructor(
    observeProfile: ObserveProfileUseCase,
    observeExercises: ObserveExercisesUseCase,
    observeConflicts: ObserveExerciseConflictsUseCase,
    observeWorkouts: ObserveWorkoutsUseCase,
    syncPreferences: SyncPreferencesRepository,
    private val createProfile: CreateGuestProfileUseCase,
    private val updateProfile: UpdateGuestProfileUseCase,
    private val createExercise: CreateExerciseUseCase,
    private val updateExercise: UpdateExerciseUseCase,
    private val deleteExercise: DeleteExerciseUseCase,
    private val resolveExerciseConflict: ResolveExerciseConflictUseCase,
    private val createWorkout: CreateWorkoutUseCase,
    private val startWorkout: StartWorkoutUseCase,
    private val completeWorkout: CompleteWorkoutUseCase,
) : ViewModel() {
    private data class PlatformData(
        val profile: GuestProfile?,
        val exercises: List<CustomExercise>,
        val workouts: List<Workout>,
        val conflicts: List<ExerciseConflict>,
    )

    private data class SyncSummary(
        val enabled: Boolean,
        val pendingCount: Int,
        val credentials: GuestCredentialStatus,
    )

    private val operationInProgress = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    private val platformData = combine(
        observeProfile(), observeExercises(), observeWorkouts(), observeConflicts(),
    ) { profile, exercises, workouts, conflicts ->
        PlatformData(profile, exercises, workouts, conflicts)
    }

    private val syncSummary = combine(
        syncPreferences.observeEnabled(),
        syncPreferences.observePendingCount(),
        syncPreferences.observeCredentialState(),
    ) { enabled, pending, credentials -> SyncSummary(enabled, pending, credentials) }

    val uiState = combine(
        platformData,
        syncSummary,
        operationInProgress,
        errorMessage,
    ) { data, sync, busy, error ->
        PlatformUiState(
            isLoading = false,
            profile = data.profile,
            exercises = data.exercises,
            conflicts = data.conflicts,
            workouts = data.workouts,
            syncEnabled = sync.enabled,
            pendingSyncCount = sync.pendingCount,
            credentialStatus = sync.credentials,
            operationInProgress = busy,
            errorMessage = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlatformUiState())

    fun clearError() { errorMessage.value = null }

    fun createGuest(displayName: String) = runOperation { createProfile(displayName) }

    fun renameGuest(displayName: String) = runOperation {
        val profile = uiState.value.profile ?: error("No guest profile exists.")
        updateProfile(profile.copy(displayName = displayName))
    }

    fun saveExercise(
        id: String?,
        name: String,
        description: String,
        muscle: String,
        equipment: String,
        trackingType: TrackingType,
        notes: String,
        onSaved: () -> Unit,
    ) = runOperation(onSuccess = onSaved) {
        val existing = id?.let { selectedId -> uiState.value.exercises.firstOrNull { it.id == selectedId } }
        if (existing == null) {
            createExercise(name, description, muscle, equipment, trackingType, notes)
        } else {
            updateExercise(existing.copy(
                name = name,
                description = description,
                primaryMuscleGroup = muscle,
                requiredEquipment = equipment,
                trackingType = trackingType,
                notes = notes,
            ))
        }
    }

    fun deleteExercise(id: String) = runOperation { deleteExercise.invoke(id) }

    fun resolveConflict(exerciseId: String, resolution: ExerciseConflictResolution, merged: CustomExercise? = null) =
        runOperation { resolveExerciseConflict(exerciseId, resolution, merged) }

    fun createWorkout(title: String, exerciseIds: List<String>) = runOperation {
        createWorkout(title, exerciseIds, "")
    }

    fun startWorkout(id: String) = runOperation { startWorkout.invoke(id) }
    fun completeWorkout(id: String) = runOperation { completeWorkout.invoke(id) }

    private fun runOperation(onSuccess: () -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            operationInProgress.value = true
            errorMessage.value = null
            runCatching { block() }
                .onSuccess { onSuccess() }
                .onFailure { errorMessage.value = it.message ?: "Unknown error" }
            operationInProgress.value = false
        }
    }
}
