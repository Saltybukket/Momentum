package at.fitnessplatform.domain

import at.fitnessplatform.core.model.CustomExercise
import at.fitnessplatform.core.model.ExerciseConflict
import at.fitnessplatform.core.model.ExerciseConflictResolution
import at.fitnessplatform.core.model.GuestProfile
import at.fitnessplatform.core.model.TrackingType
import at.fitnessplatform.core.model.Workout
import kotlinx.coroutines.flow.Flow

class ValidationException(message: String) : IllegalArgumentException(message)

class ObserveProfileUseCase(private val repository: GuestProfileRepository) {
    operator fun invoke(): Flow<GuestProfile?> = repository.observeProfile()
}

class CreateGuestProfileUseCase(private val repository: GuestProfileRepository) {
    suspend operator fun invoke(displayName: String): GuestProfile {
        val normalized = displayName.trim()
        if (normalized.length !in 2..60) throw ValidationException("Display name must contain 2 to 60 characters.")
        return repository.create(normalized)
    }
}

class UpdateGuestProfileUseCase(private val repository: GuestProfileRepository) {
    suspend operator fun invoke(profile: GuestProfile): GuestProfile {
        val normalized = profile.displayName.trim()
        if (normalized.length !in 2..60) throw ValidationException("Display name must contain 2 to 60 characters.")
        return repository.update(profile.copy(displayName = normalized))
    }
}

class ObserveExercisesUseCase(private val repository: ExerciseRepository) {
    operator fun invoke(): Flow<List<CustomExercise>> = repository.observeExercises()
}

class ObserveExerciseConflictsUseCase(private val repository: ExerciseRepository) {
    operator fun invoke(): Flow<List<ExerciseConflict>> = repository.observeExerciseConflicts()
}

class CreateExerciseUseCase(private val repository: ExerciseRepository) {
    suspend operator fun invoke(
        name: String,
        description: String,
        primaryMuscleGroup: String,
        requiredEquipment: String,
        trackingType: TrackingType,
        notes: String,
    ): CustomExercise {
        val normalizedName = name.trim()
        val validationError = when {
            normalizedName.isBlank() -> "Exercise name must not be empty."
            normalizedName.length > 120 -> "Exercise name must not exceed 120 characters."
            primaryMuscleGroup.isBlank() -> "A primary muscle group is required."
            else -> null
        }
        if (validationError != null) throw ValidationException(validationError)
        return repository.create(
            normalizedName,
            description.trim(),
            primaryMuscleGroup.trim(),
            requiredEquipment.trim().ifBlank { "None" },
            trackingType,
            notes.trim(),
        )
    }
}

class UpdateExerciseUseCase(private val repository: ExerciseRepository) {
    suspend operator fun invoke(exercise: CustomExercise): CustomExercise {
        if (exercise.name.trim().isBlank()) throw ValidationException("Exercise name must not be empty.")
        return repository.update(exercise.copy(name = exercise.name.trim()))
    }
}

class DeleteExerciseUseCase(private val repository: ExerciseRepository) {
    suspend operator fun invoke(id: String) = repository.delete(id)
}

class ResolveExerciseConflictUseCase(private val repository: ExerciseRepository) {
    suspend operator fun invoke(
        exerciseId: String,
        resolution: ExerciseConflictResolution,
        mergedExercise: CustomExercise? = null,
    ) = repository.resolveConflict(exerciseId, resolution, mergedExercise)
}

class ObserveWorkoutsUseCase(private val repository: WorkoutRepository) {
    operator fun invoke(): Flow<List<Workout>> = repository.observeWorkouts()
}

class CreateWorkoutUseCase(private val repository: WorkoutRepository) {
    suspend operator fun invoke(title: String, exerciseIds: List<String>, notes: String = ""): Workout {
        val normalized = title.trim()
        if (normalized.isBlank()) throw ValidationException("Workout title must not be empty.")
        return repository.create(normalized, exerciseIds.distinct(), notes.trim())
    }
}

class StartWorkoutUseCase(private val repository: WorkoutRepository) {
    suspend operator fun invoke(id: String): Workout = repository.start(id)
}

class CompleteWorkoutUseCase(private val repository: WorkoutRepository) {
    suspend operator fun invoke(id: String): Workout = repository.complete(id)
}
