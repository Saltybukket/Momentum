package at.fitnessplatform.domain

import at.fitnessplatform.core.model.CustomExercise
import at.fitnessplatform.core.model.CatalogExercise
import at.fitnessplatform.core.model.CatalogFilter
import at.fitnessplatform.core.model.Equipment
import at.fitnessplatform.core.model.Muscle
import at.fitnessplatform.core.model.DomainEvent
import at.fitnessplatform.core.model.ExerciseConflict
import at.fitnessplatform.core.model.ExerciseConflictResolution
import at.fitnessplatform.core.model.GuestProfile
import at.fitnessplatform.core.model.Workout
import at.fitnessplatform.core.model.LocationType
import at.fitnessplatform.core.model.TrainingLocation
import kotlinx.coroutines.flow.Flow

interface GuestProfileRepository {
    fun observeProfile(): Flow<GuestProfile?>
    suspend fun getProfile(): GuestProfile?
    suspend fun create(displayName: String): GuestProfile
    suspend fun update(profile: GuestProfile): GuestProfile
}

interface ExerciseRepository {
    fun observeExercises(): Flow<List<CustomExercise>>
    fun observeExerciseConflicts(): Flow<List<ExerciseConflict>>
    suspend fun getExercise(id: String): CustomExercise?
    suspend fun create(
        name: String,
        description: String,
        primaryMuscleGroup: String,
        requiredEquipment: String,
        trackingType: at.fitnessplatform.core.model.TrackingType,
        notes: String,
    ): CustomExercise
    suspend fun update(exercise: CustomExercise): CustomExercise
    suspend fun delete(id: String)
    suspend fun resolveConflict(
        exerciseId: String,
        resolution: ExerciseConflictResolution,
        mergedExercise: CustomExercise? = null,
    )
}

interface WorkoutRepository {
    fun observeWorkouts(): Flow<List<Workout>>
    suspend fun getWorkout(id: String): Workout?
    suspend fun create(title: String, exerciseIds: List<String>, notes: String): Workout
    suspend fun start(id: String): Workout
    suspend fun complete(id: String): Workout
}

interface CatalogRepository {
    fun observeCatalog(filter: CatalogFilter = CatalogFilter()): Flow<List<CatalogExercise>>
    fun observeExercise(id: String): Flow<CatalogExercise?>
    fun observeMuscles(): Flow<List<Muscle>>
    fun observeEquipment(): Flow<List<Equipment>>
    suspend fun seedIfEmpty()
    suspend fun refresh()
}

interface TrainingLocationRepository {
    fun observeLocations(): Flow<List<TrainingLocation>>
    fun observeActiveLocation(): Flow<TrainingLocation?>
    suspend fun getLocation(id: String): TrainingLocation?
    suspend fun create(name: String, type: LocationType, equipmentSlugs: Set<String>): TrainingLocation
    suspend fun update(location: TrainingLocation): TrainingLocation
    suspend fun setActive(id: String)
    suspend fun replaceEquipment(id: String, equipmentSlugs: Set<String>)
    suspend fun delete(id: String)
}

interface SyncPreferencesRepository {
    fun observeEnabled(): Flow<Boolean>
    fun observePendingCount(): Flow<Int>
    fun observeCredentialState(): Flow<GuestCredentialStatus>
    suspend fun setEnabled(enabled: Boolean)
    suspend fun resetCredentialsForNewIdentity()
}

enum class GuestCredentialStatus {
    READY,
    INVALIDATED,
    RECOVERY_REJECTED,
}

fun interface DomainEventHandler<T : DomainEvent> { suspend fun handle(event: T) }

interface DomainEventDispatcher {
    suspend fun publish(event: DomainEvent)
    fun <T : DomainEvent> register(type: Class<T>, handler: DomainEventHandler<T>)
}

interface SyncEnqueuer {
    fun enqueue()
    fun cancel()
}
