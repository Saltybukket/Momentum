package at.fitnessplatform.core.database

import at.fitnessplatform.core.model.*

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
