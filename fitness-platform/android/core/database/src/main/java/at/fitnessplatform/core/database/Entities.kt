package at.fitnessplatform.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "guest_profile")
data class GuestProfileEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val createdAtEpochMs: Long,
    val unitSystem: String,
    val onboardingStatus: String,
    val syncStatus: String,
    val serverId: String?,
    val conflictVersion: Long?,
)

@Entity(
    tableName = "custom_exercises",
    foreignKeys = [ForeignKey(
        entity = GuestProfileEntity::class,
        parentColumns = ["id"],
        childColumns = ["ownerProfileId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("ownerProfileId"), Index("syncStatus")],
)
data class CustomExerciseEntity(
    @PrimaryKey val id: String,
    val ownerProfileId: String,
    val name: String,
    val description: String,
    val primaryMuscleGroup: String,
    val requiredEquipment: String,
    val trackingType: String,
    val notes: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val syncStatus: String,
    val serverId: String?,
    val conflictVersion: Long?,
    val deletedAtEpochMs: Long?,
)

@Entity(
    tableName = "workouts",
    foreignKeys = [ForeignKey(
        entity = GuestProfileEntity::class,
        parentColumns = ["id"],
        childColumns = ["ownerProfileId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("ownerProfileId"), Index("status"), Index("syncStatus")],
)
data class WorkoutEntity(
    @PrimaryKey val id: String,
    val ownerProfileId: String,
    val title: String,
    val status: String,
    val startTimeEpochMs: Long?,
    val endTimeEpochMs: Long?,
    val notes: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val syncStatus: String,
    val serverId: String?,
    val conflictVersion: Long?,
)

@Entity(
    tableName = "workout_exercises",
    primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CustomExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("workoutId"), Index("exerciseId"), Index(value = ["workoutId", "position"], unique = true)],
)
data class WorkoutExerciseEntity(
    val id: String,
    val workoutId: String,
    val exerciseId: String,
    val position: Int,
)

@Entity(tableName = "sync_outbox", indices = [Index("status"), Index("createdAtEpochMs")])
data class OutboxEntity(
    @PrimaryKey val id: String,
    val aggregateId: String,
    val operationType: String,
    val payloadJson: String,
    val createdAtEpochMs: Long,
    val status: String,
    val retryCount: Int,
    val lastError: String?,
    val claimOwner: String? = null,
    val claimExpiresAtEpochMs: Long? = null,
)

@Entity(
    tableName = "exercise_conflicts",
    foreignKeys = [ForeignKey(
        entity = CustomExerciseEntity::class,
        parentColumns = ["id"],
        childColumns = ["exerciseId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["exerciseId"], unique = true), Index("resolutionStatus")],
)
data class ExerciseConflictEntity(
    @PrimaryKey val id: String,
    val exerciseId: String,
    val conflictType: String,
    val localRevision: Long?,
    val remoteRevision: Long,
    val localSnapshotJson: String,
    val remoteSnapshotJson: String,
    val detectedAtEpochMs: Long,
    val resolutionStatus: String,
    val resolvedAtEpochMs: Long?,
)

@Entity(tableName = "catalog_exercises", indices = [Index(value = ["source", "externalId"], unique = true), Index("name")])
data class CatalogExerciseEntity(
    @PrimaryKey val id: String,
    val externalId: String,
    val source: String,
    val provenance: String,
    val licenseName: String,
    val licenseUrl: String,
    val version: String,
    val status: String,
    val reviewed: Boolean,
    val name: String,
    val description: String,
    val trackingType: String,
)

@Entity(tableName = "catalog_muscles")
data class CatalogMuscleEntity(@PrimaryKey val slug: String, val name: String)

@Entity(tableName = "catalog_equipment")
data class CatalogEquipmentEntity(@PrimaryKey val slug: String, val name: String)

@Entity(
    tableName = "catalog_exercise_muscles",
    primaryKeys = ["exerciseId", "muscleSlug"],
    foreignKeys = [
        ForeignKey(entity = CatalogExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CatalogMuscleEntity::class, parentColumns = ["slug"], childColumns = ["muscleSlug"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("exerciseId"), Index("muscleSlug")],
)
data class CatalogExerciseMuscleEntity(val exerciseId: String, val muscleSlug: String, val role: String)

@Entity(
    tableName = "catalog_exercise_equipment",
    primaryKeys = ["exerciseId", "equipmentSlug"],
    foreignKeys = [
        ForeignKey(entity = CatalogExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CatalogEquipmentEntity::class, parentColumns = ["slug"], childColumns = ["equipmentSlug"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("exerciseId"), Index("equipmentSlug")],
)
data class CatalogExerciseEquipmentEntity(val exerciseId: String, val equipmentSlug: String)

@Entity(tableName = "catalog_metadata")
data class CatalogMetadataEntity(
    @PrimaryKey val singletonId: Int = 1,
    val schemaVersion: String,
    val catalogVersion: String,
    val contentHash: String,
    val retrievedAtEpochMs: Long,
    val source: String,
)

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val singletonId: Int = 1,
    val exerciseCursor: Long = 0,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "training_locations",
    indices = [
        Index(value = ["activeSlot"], unique = true),
        Index(value = ["isActive", "deletedAtEpochMs"]),
    ],
)
data class TrainingLocationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val isActive: Boolean,
    val activeSlot: Int?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val revision: Long,
    val deletedAtEpochMs: Long?,
)

@Entity(
    tableName = "training_location_equipment",
    primaryKeys = ["locationId", "equipmentSlug"],
    foreignKeys = [ForeignKey(
        entity = TrainingLocationEntity::class,
        parentColumns = ["id"],
        childColumns = ["locationId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("locationId"), Index("equipmentSlug")],
)
data class TrainingLocationEquipmentEntity(val locationId: String, val equipmentSlug: String)

@Entity(
    tableName = "training_plans",
    foreignKeys = [ForeignKey(
        entity = GuestProfileEntity::class,
        parentColumns = ["id"],
        childColumns = ["ownerProfileId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index("ownerProfileId"),
        Index(value = ["activeSlot"], unique = true),
        Index(value = ["ownerProfileId", "isArchived", "deletedAtEpochMs"]),
    ],
)
data class TrainingPlanEntity(
    @PrimaryKey val id: String,
    val ownerProfileId: String,
    val name: String,
    val description: String,
    val goal: String,
    val isActive: Boolean,
    val activeSlot: String?,
    val isArchived: Boolean,
    val sourceTemplateId: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val revision: Long,
    val deletedAtEpochMs: Long?,
)

@Entity(
    tableName = "plan_weeks",
    foreignKeys = [ForeignKey(
        entity = TrainingPlanEntity::class,
        parentColumns = ["id"],
        childColumns = ["planId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("planId"), Index(value = ["planId", "position"], unique = true)],
)
data class PlanWeekEntity(
    @PrimaryKey val id: String,
    val planId: String,
    val position: Int,
    val title: String,
    val weekIndex: Int,
)

@Entity(
    tableName = "plan_days",
    foreignKeys = [ForeignKey(
        entity = PlanWeekEntity::class,
        parentColumns = ["id"],
        childColumns = ["weekId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("weekId"), Index(value = ["weekId", "position"], unique = true)],
)
data class PlanDayEntity(
    @PrimaryKey val id: String,
    val weekId: String,
    val position: Int,
    val title: String,
    val relativeDayIndex: Int,
    val estimatedDurationMinutes: Int?,
    val notes: String,
)

@Entity(
    tableName = "plan_blocks",
    foreignKeys = [ForeignKey(
        entity = PlanDayEntity::class,
        parentColumns = ["id"],
        childColumns = ["dayId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("dayId"), Index(value = ["dayId", "position"], unique = true)],
)
data class PlanBlockEntity(
    @PrimaryKey val id: String,
    val dayId: String,
    val position: Int,
    val type: String,
    val title: String,
    val rounds: Int?,
)

@Entity(
    tableName = "plan_exercises",
    foreignKeys = [ForeignKey(
        entity = PlanBlockEntity::class,
        parentColumns = ["id"],
        childColumns = ["blockId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("blockId"), Index(value = ["blockId", "position"], unique = true)],
)
data class PlanExerciseEntity(
    @PrimaryKey val id: String,
    val blockId: String,
    val position: Int,
    val referenceKind: String,
    val customExerciseId: String?,
    val catalogSource: String?,
    val catalogExternalId: String?,
    val catalogExerciseId: String?,
    val snapshotName: String,
    val snapshotTrackingType: String,
    val snapshotEquipment: String,
    val snapshotPrimaryMuscle: String?,
    val resolutionStatus: String,
    val optional: Boolean,
    val notes: String,
)

@Entity(
    tableName = "plan_set_prescriptions",
    foreignKeys = [ForeignKey(
        entity = PlanExerciseEntity::class,
        parentColumns = ["id"],
        childColumns = ["planExerciseId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index("planExerciseId"),
        Index(value = ["planExerciseId", "position"], unique = true),
    ],
)
data class PlanSetPrescriptionEntity(
    @PrimaryKey val id: String,
    val planExerciseId: String,
    val position: Int,
    val setType: String,
    val repsMin: Int?,
    val repsMax: Int?,
    val durationSeconds: Int?,
    val distanceMeters: Double?,
    val targetWeightKg: Double?,
    val targetRpe: Double?,
    val targetRir: Int?,
    val restSeconds: Int?,
    val tempoEccentric: String?,
    val tempoBottomPause: String?,
    val tempoConcentric: String?,
    val tempoTopPause: String?,
)
