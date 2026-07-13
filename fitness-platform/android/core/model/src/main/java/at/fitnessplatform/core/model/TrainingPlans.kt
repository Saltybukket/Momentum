package at.fitnessplatform.core.model

enum class ExerciseReferenceKind { CUSTOM, CATALOG }
enum class ExerciseResolutionStatus { RESOLVED, UNAVAILABLE, DEPRECATED, DELETED_CUSTOM }
enum class TrainingPlanGoal { GENERAL_FITNESS, STRENGTH, MUSCLE_BUILDING, MOBILITY, RUNNING, CUSTOM }
enum class PlanBlockType { WARMUP, MAIN, SUPERSET, CIRCUIT, MOBILITY, OPTIONAL, COOLDOWN }
enum class PlanSetType { WARMUP, WORK, DROP, AMRAP, TIME, DISTANCE }

data class ExerciseSnapshot(
    val name: String,
    val trackingType: TrackingType,
    val equipment: Set<String>,
    val primaryMuscle: String? = null,
)

data class ExerciseReference(
    val kind: ExerciseReferenceKind,
    val customExerciseId: String? = null,
    val catalogSource: String? = null,
    val catalogExternalId: String? = null,
    val catalogExerciseId: String? = null,
    val snapshot: ExerciseSnapshot,
    val resolutionStatus: ExerciseResolutionStatus = ExerciseResolutionStatus.RESOLVED,
)

data class SetPrescription(
    val id: String,
    val position: Int,
    val setType: PlanSetType = PlanSetType.WORK,
    val repsMin: Int? = null,
    val repsMax: Int? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null,
    val targetWeightKg: Double? = null,
    val targetRpe: Double? = null,
    val targetRir: Int? = null,
    val restSeconds: Int? = null,
    val tempo: TempoPrescription? = null,
)

data class TempoPrescription(
    val eccentric: String,
    val bottomPause: String,
    val concentric: String,
    val topPause: String,
)

data class PlanExercise(
    val id: String,
    val position: Int,
    val reference: ExerciseReference,
    val notes: String = "",
    val sets: List<SetPrescription> = emptyList(),
    val optional: Boolean = false,
)

data class PlanBlock(
    val id: String,
    val position: Int,
    val type: PlanBlockType,
    val title: String,
    val exercises: List<PlanExercise> = emptyList(),
    val rounds: Int? = null,
)

data class PlanDay(
    val id: String,
    val position: Int,
    val title: String,
    val blocks: List<PlanBlock> = emptyList(),
    val relativeDayIndex: Int = position,
    val estimatedDurationMinutes: Int? = null,
    val notes: String = "",
)

data class PlanWeek(
    val id: String,
    val position: Int,
    val title: String,
    val days: List<PlanDay> = emptyList(),
    val weekIndex: Int = position,
)

data class TrainingPlan(
    val id: String,
    val ownerProfileId: String,
    val name: String,
    val description: String = "",
    val goal: TrainingPlanGoal = TrainingPlanGoal.GENERAL_FITNESS,
    val isActive: Boolean = false,
    val isArchived: Boolean = false,
    val sourceTemplateId: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val revision: Long = 0,
    val deletedAtEpochMs: Long? = null,
    val weeks: List<PlanWeek> = emptyList(),
)

data class WorkoutPlanSnapshot(
    val planId: String,
    val planRevision: Long,
    val planName: String,
    val dayId: String,
    val dayTitle: String,
    val plannedDurationMinutes: Int,
    val trainingLocationId: String?,
    val scheduledStartEpochMs: Long?,
    val timeZoneId: String,
    val exercises: List<WorkoutPlanExerciseSnapshot>,
)

data class WorkoutPlanExerciseSnapshot(
    val planExerciseId: String,
    val reference: ExerciseReference,
    val prescriptions: List<SetPrescription>,
)
