package at.fitnessplatform.core.database

import at.fitnessplatform.core.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val conflictJson = Json { ignoreUnknownKeys = true }

@Serializable
data class ExerciseConflictSnapshot(
    val id: String,
    val ownerProfileId: String,
    val name: String,
    val description: String,
    val primaryMuscleGroup: String,
    val requiredEquipment: String,
    val trackingType: String,
    val notes: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val revision: Long?,
    val deletedAtEpochMs: Long?,
)

fun CustomExerciseEntity.toConflictSnapshot() = ExerciseConflictSnapshot(
    id, ownerProfileId, name, description, primaryMuscleGroup, requiredEquipment, trackingType,
    notes, createdAtEpochMs, updatedAtEpochMs, conflictVersion, deletedAtEpochMs,
)

fun ExerciseConflictSnapshot.toModel(syncStatus: SyncStatus = SyncStatus.CONFLICT) = CustomExercise(
    id, ownerProfileId, name, description, primaryMuscleGroup, requiredEquipment,
    TrackingType.valueOf(trackingType), notes, createdAtEpochMs, updatedAtEpochMs, syncStatus,
    id, revision, deletedAtEpochMs,
)

fun ExerciseConflictEntity.toModel(): ExerciseConflict = ExerciseConflict(
    id = id,
    exerciseId = exerciseId,
    type = ExerciseConflictType.valueOf(conflictType),
    localRevision = localRevision,
    remoteRevision = remoteRevision,
    localSnapshot = conflictJson.decodeFromString<ExerciseConflictSnapshot>(localSnapshotJson).toModel(),
    remoteSnapshot = conflictJson.decodeFromString<ExerciseConflictSnapshot>(remoteSnapshotJson).toModel(SyncStatus.SYNCED),
    detectedAtEpochMs = detectedAtEpochMs,
    resolutionStatus = ConflictResolutionStatus.valueOf(resolutionStatus),
    resolvedAtEpochMs = resolvedAtEpochMs,
)

fun GuestProfileEntity.toModel() = GuestProfile(
    id, displayName, createdAtEpochMs, UnitSystem.valueOf(unitSystem),
    OnboardingStatus.valueOf(onboardingStatus), SyncStatus.valueOf(syncStatus), serverId, conflictVersion,
)
fun GuestProfile.toEntity() = GuestProfileEntity(
    id, displayName, createdAtEpochMs, unitSystem.name, onboardingStatus.name,
    syncStatus.name, serverId, conflictVersion,
)

fun CustomExerciseEntity.toModel() = CustomExercise(
    id, ownerProfileId, name, description, primaryMuscleGroup, requiredEquipment,
    TrackingType.valueOf(trackingType), notes, createdAtEpochMs, updatedAtEpochMs,
    SyncStatus.valueOf(syncStatus), serverId, conflictVersion, deletedAtEpochMs,
)
fun CustomExercise.toEntity() = CustomExerciseEntity(
    id, ownerProfileId, name, description, primaryMuscleGroup, requiredEquipment,
    trackingType.name, notes, createdAtEpochMs, updatedAtEpochMs, syncStatus.name,
    serverId, conflictVersion, deletedAtEpochMs,
)

fun WorkoutWithExercises.toModel() = Workout(
    id = workout.id,
    ownerProfileId = workout.ownerProfileId,
    title = workout.title,
    status = WorkoutStatus.valueOf(workout.status),
    startTimeEpochMs = workout.startTimeEpochMs,
    endTimeEpochMs = workout.endTimeEpochMs,
    notes = workout.notes,
    exercises = exercises.sortedBy { it.position }.map { WorkoutExercise(it.id, it.workoutId, it.exerciseId, it.position) },
    createdAtEpochMs = workout.createdAtEpochMs,
    updatedAtEpochMs = workout.updatedAtEpochMs,
    syncStatus = SyncStatus.valueOf(workout.syncStatus),
    serverId = workout.serverId,
    conflictVersion = workout.conflictVersion,
)
fun Workout.toEntity() = WorkoutEntity(
    id, ownerProfileId, title, status.name, startTimeEpochMs, endTimeEpochMs, notes,
    createdAtEpochMs, updatedAtEpochMs, syncStatus.name, serverId, conflictVersion,
)
fun WorkoutExercise.toEntity() = WorkoutExerciseEntity(id, workoutId, exerciseId, position)

fun CatalogExerciseWithRelations.toModel() = CatalogExercise(
    id = exercise.id, externalId = exercise.externalId, source = exercise.source,
    provenance = exercise.provenance, licenseName = exercise.licenseName,
    licenseUrl = exercise.licenseUrl, version = exercise.version,
    status = CatalogStatus.valueOf(exercise.status), reviewed = exercise.reviewed,
    name = exercise.name, description = exercise.description,
    trackingType = TrackingType.valueOf(exercise.trackingType),
    muscles = muscles.map { CatalogMuscle(it.muscleSlug, MuscleRole.valueOf(it.role)) },
    equipment = equipment.map { it.equipmentSlug },
)

fun CatalogExercise.toEntity() = CatalogExerciseEntity(
    id, externalId, source, provenance, licenseName, licenseUrl, version, status.name,
    reviewed, name, description, trackingType.name,
)
fun CatalogExercise.toMuscleEntities() = muscles.map { CatalogExerciseMuscleEntity(id, it.slug, it.role.name) }
fun CatalogExercise.toEquipmentEntities() = equipment.map { CatalogExerciseEquipmentEntity(id, it) }
