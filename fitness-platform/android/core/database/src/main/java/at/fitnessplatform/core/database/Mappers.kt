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

fun TrainingLocationWithEquipment.toModel() = TrainingLocation(
    id = location.id,
    name = location.name,
    type = LocationType.valueOf(location.type),
    equipmentSlugs = equipment.mapTo(linkedSetOf()) { it.equipmentSlug },
    isActive = location.isActive,
    createdAtEpochMs = location.createdAtEpochMs,
    updatedAtEpochMs = location.updatedAtEpochMs,
    revision = location.revision,
    deletedAtEpochMs = location.deletedAtEpochMs,
)

fun TrainingLocation.toEntity() = TrainingLocationEntity(
    id = id,
    name = name,
    type = type.name,
    isActive = isActive,
    activeSlot = if (isActive && deletedAtEpochMs == null) 1 else null,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
    revision = revision,
    deletedAtEpochMs = deletedAtEpochMs,
)

fun TrainingPlanWithWeeks.toModel() = TrainingPlan(
    id = plan.id,
    ownerProfileId = plan.ownerProfileId,
    name = plan.name,
    description = plan.description,
    goal = TrainingPlanGoal.valueOf(plan.goal),
    isActive = plan.isActive,
    isArchived = plan.isArchived,
    sourceTemplateId = plan.sourceTemplateId,
    createdAtEpochMs = plan.createdAtEpochMs,
    updatedAtEpochMs = plan.updatedAtEpochMs,
    revision = plan.revision,
    deletedAtEpochMs = plan.deletedAtEpochMs,
    weeks = weeks.sortedBy { it.week.position }.map { week ->
        PlanWeek(
            id = week.week.id,
            position = week.week.position,
            title = week.week.title,
            weekIndex = week.week.weekIndex,
            days = week.days.sortedBy { it.day.position }.map { day ->
                PlanDay(
                    id = day.day.id,
                    position = day.day.position,
                    title = day.day.title,
                    relativeDayIndex = day.day.relativeDayIndex,
                    estimatedDurationMinutes = day.day.estimatedDurationMinutes,
                    notes = day.day.notes,
                    blocks = day.blocks.sortedBy { it.block.position }.map { block ->
                        PlanBlock(
                            id = block.block.id,
                            position = block.block.position,
                            type = PlanBlockType.valueOf(block.block.type),
                            title = block.block.title,
                            rounds = block.block.rounds,
                            exercises = block.exercises.sortedBy { it.exercise.position }.map { exercise ->
                                exercise.toModel()
                            },
                        )
                    },
                )
            },
        )
    },
)

private fun PlanExerciseWithSets.toModel() = PlanExercise(
    id = exercise.id,
    position = exercise.position,
    reference = ExerciseReference(
        kind = ExerciseReferenceKind.valueOf(exercise.referenceKind),
        customExerciseId = exercise.customExerciseId,
        catalogSource = exercise.catalogSource,
        catalogExternalId = exercise.catalogExternalId,
        catalogExerciseId = exercise.catalogExerciseId,
        snapshot = ExerciseSnapshot(
            name = exercise.snapshotName,
            trackingType = TrackingType.valueOf(exercise.snapshotTrackingType),
            equipment = exercise.snapshotEquipment,
            primaryMuscle = exercise.snapshotPrimaryMuscle,
        ),
        resolutionStatus = ExerciseResolutionStatus.valueOf(exercise.resolutionStatus),
    ),
    optional = exercise.optional,
    notes = exercise.notes,
    sets = sets.sortedBy { it.position }.map {
        SetPrescription(
            id = it.id,
            position = it.position,
            setType = PlanSetType.valueOf(it.setType),
            repsMin = it.repsMin,
            repsMax = it.repsMax,
            durationSeconds = it.durationSeconds,
            distanceMeters = it.distanceMeters,
            targetWeightKg = it.targetWeightKg,
            targetRpe = it.targetRpe,
            targetRir = it.targetRir,
            restSeconds = it.restSeconds,
            tempo = it.tempoEccentric?.let { eccentric ->
                TempoPrescription(
                    eccentric,
                    requireNotNull(it.tempoBottomPause),
                    requireNotNull(it.tempoConcentric),
                    requireNotNull(it.tempoTopPause),
                )
            },
        )
    },
)

fun TrainingPlan.toEntity() = TrainingPlanEntity(
    id = id,
    ownerProfileId = ownerProfileId,
    name = name,
    description = description,
    goal = goal.name,
    isActive = isActive,
    activeSlot = ownerProfileId.takeIf { isActive && !isArchived && deletedAtEpochMs == null },
    isArchived = isArchived,
    sourceTemplateId = sourceTemplateId,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
    revision = revision,
    deletedAtEpochMs = deletedAtEpochMs,
)

data class TrainingPlanRows(
    val weeks: List<PlanWeekEntity>,
    val days: List<PlanDayEntity>,
    val blocks: List<PlanBlockEntity>,
    val exercises: List<PlanExerciseEntity>,
    val sets: List<PlanSetPrescriptionEntity>,
)

@Suppress("NestedBlockDepth")
fun TrainingPlan.toRows(): TrainingPlanRows {
    val weekRows = mutableListOf<PlanWeekEntity>()
    val dayRows = mutableListOf<PlanDayEntity>()
    val blockRows = mutableListOf<PlanBlockEntity>()
    val exerciseRows = mutableListOf<PlanExerciseEntity>()
    val setRows = mutableListOf<PlanSetPrescriptionEntity>()
    weeks.forEach { week ->
        weekRows += PlanWeekEntity(week.id, id, week.position, week.title, week.weekIndex)
        week.days.forEach { day ->
            dayRows += PlanDayEntity(
                day.id,
                week.id,
                day.position,
                day.title,
                day.relativeDayIndex,
                day.estimatedDurationMinutes,
                day.notes,
            )
            day.blocks.forEach { block ->
                blockRows += PlanBlockEntity(
                    block.id,
                    day.id,
                    block.position,
                    block.type.name,
                    block.title,
                    block.rounds,
                )
                block.exercises.forEach { exercise ->
                    val reference = exercise.reference
                    exerciseRows += PlanExerciseEntity(
                        id = exercise.id,
                        blockId = block.id,
                        position = exercise.position,
                        referenceKind = reference.kind.name,
                        customExerciseId = reference.customExerciseId,
                        catalogSource = reference.catalogSource,
                        catalogExternalId = reference.catalogExternalId,
                        catalogExerciseId = reference.catalogExerciseId,
                        snapshotName = reference.snapshot.name,
                        snapshotTrackingType = reference.snapshot.trackingType.name,
                        snapshotEquipment = reference.snapshot.equipment,
                        snapshotPrimaryMuscle = reference.snapshot.primaryMuscle,
                        resolutionStatus = reference.resolutionStatus.name,
                        optional = exercise.optional,
                        notes = exercise.notes,
                    )
                    setRows += exercise.sets.map {
                        PlanSetPrescriptionEntity(
                            id = it.id,
                            planExerciseId = exercise.id,
                            position = it.position,
                            setType = it.setType.name,
                            repsMin = it.repsMin,
                            repsMax = it.repsMax,
                            durationSeconds = it.durationSeconds,
                            distanceMeters = it.distanceMeters,
                            targetWeightKg = it.targetWeightKg,
                            targetRpe = it.targetRpe,
                            targetRir = it.targetRir,
                            restSeconds = it.restSeconds,
                            tempoEccentric = it.tempo?.eccentric,
                            tempoBottomPause = it.tempo?.bottomPause,
                            tempoConcentric = it.tempo?.concentric,
                            tempoTopPause = it.tempo?.topPause,
                        )
                    }
                }
            }
        }
    }
    return TrainingPlanRows(weekRows, dayRows, blockRows, exerciseRows, setRows)
}
