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
