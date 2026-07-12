package at.fitnessplatform.data

import androidx.room.withTransaction
import android.content.Context
import at.fitnessplatform.core.database.*
import at.fitnessplatform.core.model.*
import at.fitnessplatform.domain.*
import at.fitnessplatform.core.network.CatalogExerciseDto
import at.fitnessplatform.core.network.CatalogSnapshotDto
import at.fitnessplatform.core.network.FitnessApi
import at.fitnessplatform.core.datastore.GuestSessionStore
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

private fun outboxEntity(
    ids: UuidProvider,
    clock: Clock,
    aggregateId: String,
    type: OutboxOperationType,
    payload: String,
) = OutboxEntity(
    id = ids.newUuid(),
    aggregateId = aggregateId,
    operationType = type.name,
    payloadJson = payload,
    createdAtEpochMs = clock.nowEpochMs(),
    status = SyncStatus.PENDING.name,
    retryCount = 0,
    lastError = null,
)

@Singleton
class RoomSyncPreferencesRepository @Inject constructor(
    private val sessionStore: GuestSessionStore,
    private val outboxDao: OutboxDao,
    private val syncEnqueuer: SyncEnqueuer,
) : SyncPreferencesRepository {
    override fun observeEnabled() = sessionStore.syncEnabled
    override fun observePendingCount() = outboxDao.observePendingCount()
    override suspend fun setEnabled(enabled: Boolean) {
        sessionStore.setSyncEnabled(enabled)
        if (enabled) syncEnqueuer.enqueue() else syncEnqueuer.cancel()
    }
}

private fun CatalogExerciseDto.toCatalogModel() = CatalogExercise(
    id, externalId, source, provenance, licenseName, licenseUrl, version,
    CatalogStatus.valueOf(status), reviewed, name, description, TrackingType.valueOf(trackingType),
    muscles.map { CatalogMuscle(it.slug, MuscleRole.valueOf(it.role)) }, equipment,
)

@Singleton
class RoomCatalogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val catalogDao: CatalogDao,
    private val api: FitnessApi,
    private val json: Json,
) : CatalogRepository {
    override fun observeCatalog(filter: CatalogFilter) =
        catalogDao.observe(filter.query, filter.muscle, filter.equipment).map { rows -> rows.map { it.toModel() } }
    override fun observeExercise(id: String) = catalogDao.observeOne(id).map { it?.toModel() }
    override fun observeMuscles() = catalogDao.observeMuscles().map { rows -> rows.map { Muscle(it.slug, it.name) } }
    override fun observeEquipment() = catalogDao.observeEquipment().map { rows -> rows.map { Equipment(it.slug, it.name) } }

    override suspend fun seedIfEmpty() {
        if (catalogDao.count() == 0) {
            val snapshot = context.assets.open("catalog-demo.json").bufferedReader().use {
                json.decodeFromString<CatalogSnapshotDto>(it.readText())
            }
            replace(snapshot, "bundled-seed")
        }
    }

    override suspend fun refresh() {
        replace(api.catalogSnapshot(), "network")
    }

    private suspend fun replace(snapshot: CatalogSnapshotDto, source: String) {
        if (catalogDao.metadata()?.contentHash == snapshot.contentHash) return
        val exercises = snapshot.exercises.map { it.toCatalogModel() }
        database.withTransaction {
            catalogDao.deleteExercises()
            catalogDao.deleteMuscles()
            catalogDao.deleteEquipment()
            catalogDao.insertMuscles(snapshot.muscles.map { CatalogMuscleEntity(it.slug, it.name) })
            catalogDao.insertEquipment(snapshot.equipment.map { CatalogEquipmentEntity(it.slug, it.name) })
            catalogDao.insertExercises(exercises.map { it.toEntity() })
            catalogDao.insertExerciseMuscles(exercises.flatMap { it.toMuscleEntities() })
            catalogDao.insertExerciseEquipment(exercises.flatMap { it.toEquipmentEntities() })
            catalogDao.putMetadata(CatalogMetadataEntity(
                schemaVersion = snapshot.schemaVersion,
                catalogVersion = snapshot.catalogVersion,
                contentHash = snapshot.contentHash,
                retrievedAtEpochMs = System.currentTimeMillis(),
                source = source,
            ))
        }
    }
}

@Singleton
class RoomGuestProfileRepository @Inject constructor(
    private val database: AppDatabase,
    private val profileDao: GuestProfileDao,
    private val outboxDao: OutboxDao,
    private val ids: UuidProvider,
    private val clock: Clock,
    private val events: DomainEventDispatcher,
    private val syncEnqueuer: SyncEnqueuer,
) : GuestProfileRepository {
    override fun observeProfile(): Flow<GuestProfile?> = profileDao.observe().map { it?.toModel() }
    override suspend fun getProfile(): GuestProfile? = profileDao.get()?.toModel()

    override suspend fun create(displayName: String): GuestProfile {
        profileDao.get()?.let { return it.toModel() }
        val now = clock.nowEpochMs()
        val profile = GuestProfile(ids.newUuid(), displayName, now, syncStatus = SyncStatus.PENDING)
        database.withTransaction {
            profileDao.insert(profile.toEntity())
            outboxDao.insert(profileOutbox(profile))
        }
        events.publish(GuestProfileCreated(ids.newUuid(), now, profile.id))
        syncEnqueuer.enqueue()
        return profile
    }

    override suspend fun update(profile: GuestProfile): GuestProfile {
        val updated = profile.copy(syncStatus = SyncStatus.PENDING)
        database.withTransaction {
            profileDao.update(updated.toEntity())
            outboxDao.insert(profileOutbox(updated))
        }
        syncEnqueuer.enqueue()
        return updated
    }

    private fun profileOutbox(profile: GuestProfile): OutboxEntity {
        val payload = buildJsonObject {
            put("display_name", profile.displayName)
            put("unit_system", profile.unitSystem.name)
            put("onboarding_status", profile.onboardingStatus.name)
        }.toString()
        return outboxEntity(ids, clock, profile.id, OutboxOperationType.UPSERT_PROFILE, payload)
    }
}

@Singleton
class RoomExerciseRepository @Inject constructor(
    private val database: AppDatabase,
    private val profileDao: GuestProfileDao,
    private val exerciseDao: ExerciseDao,
    private val conflictDao: ExerciseConflictDao,
    private val outboxDao: OutboxDao,
    private val ids: UuidProvider,
    private val clock: Clock,
    private val events: DomainEventDispatcher,
    private val syncEnqueuer: SyncEnqueuer,
) : ExerciseRepository {
    override fun observeExercises(): Flow<List<CustomExercise>> = exerciseDao.observeActive().map { rows -> rows.map { it.toModel() } }
    override fun observeExerciseConflicts() = conflictDao.observeOpen().map { rows -> rows.map { it.toModel() } }
    override suspend fun getExercise(id: String): CustomExercise? = exerciseDao.get(id)?.toModel()

    override suspend fun create(
        name: String,
        description: String,
        primaryMuscleGroup: String,
        requiredEquipment: String,
        trackingType: TrackingType,
        notes: String,
    ): CustomExercise {
        val profile = profileDao.get() ?: error("Create a guest profile before adding exercises.")
        val now = clock.nowEpochMs()
        val exercise = CustomExercise(
            id = ids.newUuid(), ownerProfileId = profile.id, name = name, description = description,
            primaryMuscleGroup = primaryMuscleGroup, requiredEquipment = requiredEquipment,
            trackingType = trackingType, notes = notes, createdAtEpochMs = now, updatedAtEpochMs = now,
        )
        database.withTransaction {
            exerciseDao.insert(exercise.toEntity())
            outboxDao.insert(exerciseOutbox(exercise, OutboxOperationType.UPSERT_EXERCISE))
        }
        events.publish(ExerciseCreated(ids.newUuid(), now, exercise.id))
        syncEnqueuer.enqueue()
        return exercise
    }

    override suspend fun update(exercise: CustomExercise): CustomExercise {
        require(exercise.syncStatus != SyncStatus.CONFLICT) { "Resolve the sync conflict before editing this exercise." }
        val updated = exercise.copy(updatedAtEpochMs = clock.nowEpochMs(), syncStatus = SyncStatus.PENDING)
        database.withTransaction {
            exerciseDao.update(updated.toEntity())
            outboxDao.insert(exerciseOutbox(updated, OutboxOperationType.UPSERT_EXERCISE))
        }
        syncEnqueuer.enqueue()
        return updated
    }

    override suspend fun delete(id: String) {
        val current = exerciseDao.get(id)?.toModel() ?: return
        require(current.syncStatus != SyncStatus.CONFLICT) { "Resolve the sync conflict before deleting this exercise." }
        if (current.deletedAtEpochMs != null) return
        val deleted = current.copy(deletedAtEpochMs = clock.nowEpochMs(), syncStatus = SyncStatus.PENDING)
        database.withTransaction {
            exerciseDao.update(deleted.toEntity())
            outboxDao.insert(exerciseOutbox(deleted, OutboxOperationType.DELETE_EXERCISE))
        }
        syncEnqueuer.enqueue()
    }

    override suspend fun resolveConflict(
        exerciseId: String,
        resolution: ExerciseConflictResolution,
        mergedExercise: CustomExercise?,
    ) {
        val conflict = conflictDao.getOpenForExercise(exerciseId)?.toModel()
            ?: error("No open conflict exists for this exercise.")
        val current = exerciseDao.get(exerciseId) ?: error("Conflicted exercise is missing locally.")
        val remote = conflict.remoteSnapshot
        database.withTransaction {
            when (resolution) {
                ExerciseConflictResolution.TAKE_SERVER -> {
                    exerciseDao.replace(remote.copy(ownerProfileId = current.ownerProfileId, syncStatus = SyncStatus.SYNCED).toEntity())
                    outboxDao.deleteUnacknowledgedForAggregate(exerciseId)
                    conflictDao.updateStatus(exerciseId, ConflictResolutionStatus.RESOLVED.name, clock.nowEpochMs())
                }
                ExerciseConflictResolution.KEEP_LOCAL,
                ExerciseConflictResolution.MERGE -> {
                    val selected = when (resolution) {
                        ExerciseConflictResolution.KEEP_LOCAL -> conflict.localSnapshot
                        ExerciseConflictResolution.MERGE -> requireNotNull(mergedExercise) { "A merged exercise is required." }
                        ExerciseConflictResolution.TAKE_SERVER -> error("Handled above")
                    }
                    val resolved = selected.copy(
                        id = exerciseId,
                        ownerProfileId = current.ownerProfileId,
                        updatedAtEpochMs = clock.nowEpochMs(),
                        syncStatus = SyncStatus.PENDING,
                        serverId = remote.serverId ?: exerciseId,
                        conflictVersion = remote.conflictVersion,
                    )
                    outboxDao.deleteUnacknowledgedForAggregate(exerciseId)
                    exerciseDao.replace(resolved.toEntity())
                    outboxDao.insert(exerciseOutbox(resolved, if (resolved.deletedAtEpochMs == null) OutboxOperationType.UPSERT_EXERCISE else OutboxOperationType.DELETE_EXERCISE))
                    conflictDao.updateStatus(exerciseId, ConflictResolutionStatus.PENDING_CONFIRMATION.name, null)
                }
            }
        }
        if (resolution != ExerciseConflictResolution.TAKE_SERVER) syncEnqueuer.enqueue()
    }

    private fun exerciseOutbox(exercise: CustomExercise, type: OutboxOperationType): OutboxEntity {
        val payload = buildJsonObject {
            put("id", exercise.id)
            put("name", exercise.name)
            put("description", exercise.description)
            put("primary_muscle_group", exercise.primaryMuscleGroup)
            put("equipment", exercise.requiredEquipment)
            put("tracking_type", exercise.trackingType.name)
            put("notes", exercise.notes)
            put("base_revision", exercise.conflictVersion)
        }.toString()
        return outboxEntity(ids, clock, exercise.id, type, payload)
    }
}

@Singleton
class RoomWorkoutRepository @Inject constructor(
    private val database: AppDatabase,
    private val profileDao: GuestProfileDao,
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val outboxDao: OutboxDao,
    private val ids: UuidProvider,
    private val clock: Clock,
    private val events: DomainEventDispatcher,
    private val syncEnqueuer: SyncEnqueuer,
) : WorkoutRepository {
    override fun observeWorkouts(): Flow<List<Workout>> = workoutDao.observeAll().map { rows -> rows.map { it.toModel() } }
    override suspend fun getWorkout(id: String): Workout? = workoutDao.get(id)?.toModel()

    override suspend fun create(title: String, exerciseIds: List<String>, notes: String): Workout {
        val profile = profileDao.get() ?: error("Create a guest profile before adding workouts.")
        exerciseIds.forEach { requireNotNull(exerciseDao.get(it)) { "Exercise $it does not exist." } }
        val now = clock.nowEpochMs()
        val workoutId = ids.newUuid()
        val workout = Workout(
            id = workoutId, ownerProfileId = profile.id, title = title, notes = notes,
            exercises = exerciseIds.mapIndexed { index, exerciseId ->
                WorkoutExercise(ids.newUuid(), workoutId, exerciseId, index)
            },
            createdAtEpochMs = now, updatedAtEpochMs = now,
        )
        database.withTransaction {
            workoutDao.insertWorkout(workout.toEntity())
            workoutDao.insertExercises(workout.exercises.map { it.toEntity() })
            outboxDao.insert(workoutOutbox(workout, OutboxOperationType.UPSERT_WORKOUT))
        }
        events.publish(WorkoutCreated(ids.newUuid(), now, workout.id))
        syncEnqueuer.enqueue()
        return workout
    }

    override suspend fun start(id: String): Workout {
        val current = requireNotNull(workoutDao.get(id)?.toModel()) { "Workout not found." }
        if (current.status == WorkoutStatus.IN_PROGRESS || current.status == WorkoutStatus.COMPLETED) return current
        val now = clock.nowEpochMs()
        val updated = current.copy(status = WorkoutStatus.IN_PROGRESS, startTimeEpochMs = now, updatedAtEpochMs = now, syncStatus = SyncStatus.PENDING)
        database.withTransaction {
            workoutDao.updateWorkout(updated.toEntity())
            outboxDao.insert(workoutOutbox(updated, OutboxOperationType.START_WORKOUT))
        }
        events.publish(WorkoutStarted(ids.newUuid(), now, id))
        syncEnqueuer.enqueue()
        return updated
    }

    override suspend fun complete(id: String): Workout {
        val current = requireNotNull(workoutDao.get(id)?.toModel()) { "Workout not found." }
        if (current.status == WorkoutStatus.COMPLETED) return current
        require(current.status == WorkoutStatus.IN_PROGRESS) { "Only a started workout can be completed." }
        val now = clock.nowEpochMs()
        val updated = current.copy(status = WorkoutStatus.COMPLETED, endTimeEpochMs = now, updatedAtEpochMs = now, syncStatus = SyncStatus.PENDING)
        database.withTransaction {
            workoutDao.updateWorkout(updated.toEntity())
            outboxDao.insert(workoutOutbox(updated, OutboxOperationType.COMPLETE_WORKOUT))
        }
        val deterministicEventId = UUID.nameUUIDFromBytes("WorkoutCompleted:$id".toByteArray(StandardCharsets.UTF_8)).toString()
        events.publish(WorkoutCompleted(deterministicEventId, now, id))
        syncEnqueuer.enqueue()
        return updated
    }

    private fun workoutOutbox(workout: Workout, type: OutboxOperationType): OutboxEntity {
        val payload = buildJsonObject {
            put("id", workout.id)
            if (type == OutboxOperationType.UPSERT_WORKOUT) {
                put("title", workout.title)
                put("notes", workout.notes)
                putJsonArray("exercise_ids") { workout.exercises.sortedBy { it.position }.forEach { add(kotlinx.serialization.json.JsonPrimitive(it.exerciseId)) } }
            }
        }.toString()
        return outboxEntity(ids, clock, workout.id, type, payload)
    }
}
