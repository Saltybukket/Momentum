package at.fitnessplatform.core.model

enum class SyncStatus { LOCAL_ONLY, PENDING, SYNCING, SYNCED, FAILED, CONFLICT }

enum class ExerciseConflictType {
    BOTH_MODIFIED,
    REMOTE_DELETED_LOCAL_MODIFIED,
    LOCAL_DELETED_REMOTE_MODIFIED,
    REVISION_MISMATCH,
}

enum class ConflictResolutionStatus { OPEN, PENDING_CONFIRMATION, RESOLVED }

enum class ExerciseConflictResolution { KEEP_LOCAL, TAKE_SERVER, MERGE }

enum class CatalogStatus { DRAFT, PUBLISHED, DEPRECATED }

enum class MuscleRole { PRIMARY, SECONDARY }

data class Muscle(val slug: String, val name: String)

data class Equipment(val slug: String, val name: String)

data class CatalogMuscle(val slug: String, val role: MuscleRole)

data class CatalogExercise(
    val id: String,
    val externalId: String,
    val source: String,
    val provenance: String,
    val licenseName: String,
    val licenseUrl: String,
    val version: String,
    val status: CatalogStatus,
    val reviewed: Boolean,
    val name: String,
    val description: String,
    val trackingType: TrackingType,
    val muscles: List<CatalogMuscle>,
    val equipment: List<String>,
)

data class CatalogFilter(
    val query: String = "",
    val muscle: String? = null,
    val equipment: String? = null,
)

enum class UnitSystem { METRIC, IMPERIAL }

enum class OnboardingStatus { NOT_STARTED, SKIPPED, IN_PROGRESS, COMPLETED }

enum class TrackingType { REPS_WEIGHT, REPS, DURATION, DISTANCE_DURATION, MANUAL }

enum class WorkoutStatus { PLANNED, IN_PROGRESS, PAUSED, COMPLETED, CANCELLED }

data class GuestProfile(
    val id: String,
    val displayName: String,
    val createdAtEpochMs: Long,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val onboardingStatus: OnboardingStatus = OnboardingStatus.NOT_STARTED,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    val serverId: String? = null,
    val conflictVersion: Long? = null,
)

data class CustomExercise(
    val id: String,
    val ownerProfileId: String,
    val name: String,
    val description: String = "",
    val primaryMuscleGroup: String,
    val requiredEquipment: String,
    val trackingType: TrackingType,
    val notes: String = "",
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val serverId: String? = null,
    val conflictVersion: Long? = null,
    val deletedAtEpochMs: Long? = null,
)

data class ExerciseConflict(
    val id: String,
    val exerciseId: String,
    val type: ExerciseConflictType,
    val localRevision: Long?,
    val remoteRevision: Long,
    val localSnapshot: CustomExercise,
    val remoteSnapshot: CustomExercise,
    val detectedAtEpochMs: Long,
    val resolutionStatus: ConflictResolutionStatus,
    val resolvedAtEpochMs: Long? = null,
)

data class WorkoutExercise(
    val id: String,
    val workoutId: String,
    val exerciseId: String,
    val position: Int,
)

data class Workout(
    val id: String,
    val ownerProfileId: String,
    val title: String,
    val status: WorkoutStatus = WorkoutStatus.PLANNED,
    val startTimeEpochMs: Long? = null,
    val endTimeEpochMs: Long? = null,
    val notes: String = "",
    val exercises: List<WorkoutExercise> = emptyList(),
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val serverId: String? = null,
    val conflictVersion: Long? = null,
)

enum class OutboxOperationType { UPSERT_PROFILE, UPSERT_EXERCISE, DELETE_EXERCISE, UPSERT_WORKOUT }

data class OutboxOperation(
    val id: String,
    val aggregateId: String,
    val operationType: OutboxOperationType,
    val payloadJson: String,
    val createdAtEpochMs: Long,
    val status: SyncStatus = SyncStatus.PENDING,
    val retryCount: Int = 0,
    val lastError: String? = null,
)
