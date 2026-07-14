package at.fitnessplatform.domain

import at.fitnessplatform.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

private const val MAX_PLAN_NAME = 100
private const val MAX_WEEKS = 12
private const val MAX_DAYS = 7
private const val MAX_BLOCKS = 12
private const val MAX_EXERCISES = 30
private const val MAX_SETS = 20
private val tempoPhasePattern = Regex("^[0-9Xx]$")
private val disallowedPlanText = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F-\\u009F\\u202A-\\u202E\\u2066-\\u2069]")

interface TrainingPlanRepository {
    fun observePlans(): Flow<List<TrainingPlan>>
    fun observeActivePlan(): Flow<TrainingPlan?>
    fun observePlan(id: String): Flow<TrainingPlan?>
    suspend fun getPlan(id: String): TrainingPlan?
    suspend fun create(plan: TrainingPlan): TrainingPlan
    suspend fun update(plan: TrainingPlan): TrainingPlan
    suspend fun copy(id: String, transform: (TrainingPlan) -> TrainingPlan = { it }): TrainingPlan
    suspend fun setActive(id: String)
    suspend fun setArchived(id: String, archived: Boolean)
    suspend fun delete(id: String)
    suspend fun seedStarterPlans()
}

class PlanRemovalDecisionRequiredException(
    val affectedPlanDayIds: Set<String>,
) : IllegalStateException("BLOCKED_PENDING_DECISION")

fun validateTrainingPlan(plan: TrainingPlan) {
    validateText(plan.name, MAX_PLAN_NAME, "Plan name", allowBlank = false, singleLine = true)
    validateText(plan.description, 2_000, "Plan description", allowBlank = true, singleLine = false)
    ensure(plan.name == plan.name.trim()) {
        "Plan name must contain 1 to $MAX_PLAN_NAME characters."
    }
    ensure(plan.weeks.size <= MAX_WEEKS) { "A plan supports at most $MAX_WEEKS weeks." }
    requireUniquePositions(plan.weeks.map(PlanWeek::position), "weeks")
    requireUniqueValues(plan.weeks.map(PlanWeek::weekIndex), "week indices")
    plan.weeks.forEach { week ->
        ensure(week.weekIndex in 0 until MAX_WEEKS) { "Week index is outside the plan range." }
        validateText(week.title, 100, "Week title", allowBlank = true, singleLine = true)
        ensure(week.days.size <= MAX_DAYS) { "A week supports at most $MAX_DAYS days." }
        requireUniquePositions(week.days.map(PlanDay::position), "days")
        requireUniqueValues(week.days.map(PlanDay::relativeDayIndex), "relative day indices")
        week.days.forEach { day ->
            ensure(day.relativeDayIndex in 0 until MAX_DAYS) { "Relative day index must be between 0 and 6." }
            ensure(day.estimatedDurationMinutes?.let { it in 1..1_440 } != false) {
                "Estimated duration must be between 1 and 1440 minutes."
            }
            validateText(day.title, 100, "Day title", allowBlank = false, singleLine = true)
            validateText(day.notes, 2_000, "Day notes", allowBlank = true, singleLine = false)
            ensure(day.blocks.size <= MAX_BLOCKS) { "A day supports at most $MAX_BLOCKS blocks." }
            ensure(day.blocks.sumOf { it.exercises.size } <= MAX_EXERCISES) {
                "A day supports at most $MAX_EXERCISES exercises."
            }
            requireUniquePositions(day.blocks.map(PlanBlock::position), "blocks")
            day.blocks.forEach { block ->
                validateText(block.title, 100, "Block title", allowBlank = true, singleLine = true)
                ensure(block.rounds?.let { it in 1..100 } != false) { "Block rounds must be between 1 and 100." }
                requireUniquePositions(block.exercises.map(PlanExercise::position), "exercises")
                block.exercises.forEach(::validatePlanExercise)
            }
        }
    }
}

@Suppress("CyclomaticComplexMethod")
private fun validatePlanExercise(exercise: PlanExercise) {
    val reference = exercise.reference
    when (reference.kind) {
        ExerciseReferenceKind.CUSTOM -> {
            ensure(!reference.customExerciseId.isNullOrBlank()) { "Custom references require an exercise UUID." }
            ensure(reference.catalogSource == null && reference.catalogExternalId == null) { "Custom references cannot contain catalog identity." }
        }
        ExerciseReferenceKind.CATALOG -> {
            ensure(!reference.catalogSource.isNullOrBlank() && !reference.catalogExternalId.isNullOrBlank()) {
                "Catalog references require source and external ID."
            }
            ensure(reference.customExerciseId == null) { "Catalog references cannot contain a custom UUID." }
        }
    }
    ensure(reference.snapshot.name.isNotBlank()) { "Exercise snapshots require a name." }
    validateText(exercise.notes, 2_000, "Exercise notes", allowBlank = true, singleLine = false)
    ensure(exercise.sets.size <= MAX_SETS) { "An exercise supports at most $MAX_SETS sets." }
    requireUniquePositions(exercise.sets.map(SetPrescription::position), "sets")
    exercise.sets.forEach { set ->
        val repsMin = set.repsMin
        ensure(repsMin?.let { it > 0 } != false) { "Minimum repetitions must be positive." }
        ensure(set.repsMax?.let { maximum -> repsMin != null && maximum >= repsMin } != false) {
            "Maximum repetitions require a minimum and cannot be lower."
        }
        ensure(set.targetWeightKg?.let { it >= 0.0 } != false) { "Weight cannot be negative." }
        ensure(set.durationSeconds?.let { it > 0 } != false) { "Duration must be positive." }
        ensure(set.distanceMeters?.let { it > 0.0 } != false) { "Distance must be positive." }
        ensure(set.targetRpe?.let { it in 1.0..10.0 } != false) { "RPE must be between 1 and 10." }
        ensure(set.targetRir?.let { it in 0..10 } != false) { "RIR must be between 0 and 10." }
        ensure(set.targetRpe == null || set.targetRir == null) { "Choose either an RPE or an RIR target." }
        ensure(set.restSeconds?.let { it in 0..3600 } != false) { "Rest must be between 0 and 3600 seconds." }
        set.tempo?.let { tempo ->
            ensure(
                listOf(tempo.eccentric, tempo.bottomPause, tempo.concentric, tempo.topPause)
                    .all(tempoPhasePattern::matches),
            ) { "Tempo phases must each use one digit or X." }
        }
        when (reference.snapshot.trackingType) {
            TrackingType.REPS_WEIGHT -> {
                ensure(set.repsMin != null) { "Reps-and-weight exercises require repetitions." }
                ensure(set.durationSeconds == null && set.distanceMeters == null) {
                    "Reps-and-weight exercises cannot use duration or distance targets."
                }
            }
            TrackingType.REPS -> {
                ensure(set.repsMin != null) { "Rep exercises require repetitions." }
                ensure(set.targetWeightKg == null && set.durationSeconds == null && set.distanceMeters == null) {
                    "Rep exercises only accept repetition and intensity targets."
                }
            }
            TrackingType.DURATION -> {
                ensure(set.durationSeconds != null) { "Duration exercises require seconds." }
                ensure(set.repsMin == null && set.repsMax == null && set.distanceMeters == null && set.targetWeightKg == null) {
                    "Duration exercises only accept duration targets."
                }
            }
            TrackingType.DISTANCE_DURATION -> {
                ensure(set.durationSeconds != null || set.distanceMeters != null) {
                    "Distance-duration exercises require distance or duration."
                }
                ensure(set.repsMin == null && set.repsMax == null && set.targetWeightKg == null) {
                    "Distance-duration exercises only accept distance, duration, and intensity targets."
                }
            }
            TrackingType.MANUAL -> Unit
        }
        when (set.setType) {
            PlanSetType.TIME -> ensure(set.durationSeconds != null) { "Time sets require a duration." }
            PlanSetType.DISTANCE -> ensure(set.distanceMeters != null) { "Distance sets require a distance." }
            PlanSetType.AMRAP -> ensure(set.repsMin != null) { "AMRAP sets require a repetition target." }
            else -> Unit
        }
    }
}

private fun validateText(
    value: String,
    maxLength: Int,
    label: String,
    allowBlank: Boolean,
    singleLine: Boolean,
) {
    ensure(!disallowedPlanText.containsMatchIn(value)) { "$label contains unsupported characters." }
    ensure(allowBlank || value.isNotBlank()) { "$label cannot be blank." }
    ensure(value.length <= maxLength) { "$label is too long." }
    ensure(!singleLine || ('\n' !in value && '\r' !in value)) { "$label must be a single line." }
}

private fun requireUniquePositions(positions: List<Int>, label: String) {
    ensure(positions.all { it >= 0 } && positions.toSet().size == positions.size) {
        "$label require unique non-negative positions."
    }
}

private fun requireUniqueValues(values: List<Int>, label: String) {
    ensure(values.toSet().size == values.size) { "$label must be unique." }
}

private inline fun ensure(condition: Boolean, message: () -> String) {
    if (!condition) throw ValidationException(message())
}

class ObserveTrainingPlansUseCase(private val repository: TrainingPlanRepository) {
    operator fun invoke() = repository.observePlans()
}

class ObserveTrainingPlanUseCase(private val repository: TrainingPlanRepository) {
    operator fun invoke(id: String) = repository.observePlan(id)
}

class ObserveActiveTrainingPlanUseCase(private val repository: TrainingPlanRepository) {
    operator fun invoke() = repository.observeActivePlan()
}

class SaveTrainingPlanUseCase(private val repository: TrainingPlanRepository) {
    suspend operator fun invoke(plan: TrainingPlan): TrainingPlan {
        val normalized = plan.copy(
            name = plan.name.trim().replace(Regex("[ \\t]+"), " "),
            description = plan.description.trim(),
        )
        validateTrainingPlan(normalized)
        return if (repository.getPlan(plan.id) == null) repository.create(normalized) else repository.update(normalized)
    }
}

class CopyTrainingPlanUseCase(private val repository: TrainingPlanRepository) {
    suspend operator fun invoke(id: String) = repository.copy(id)
}

class AdaptTrainingPlanCopyUseCase(
    private val repository: TrainingPlanRepository,
    private val alternatives: FindCompatibleAlternativesUseCase,
) {
    suspend operator fun invoke(
        id: String,
        location: TrainingLocation,
        catalog: List<CatalogExercise>,
    ): TrainingPlan = repository.copy(id) { copied ->
        copied.copy(
            name = "${copied.name} · ${location.name}",
            weeks = copied.weeks.map { week ->
                week.copy(days = week.days.map { day ->
                    day.copy(blocks = day.blocks.map { block ->
                        block.copy(exercises = block.exercises.map { exercise ->
                            adaptExercise(exercise, location, catalog)
                        })
                    })
                })
            },
        )
    }

    private fun adaptExercise(
        exercise: PlanExercise,
        location: TrainingLocation,
        catalog: List<CatalogExercise>,
    ): PlanExercise = if (
        exercise.reference.snapshot.equipment.all {
            it == EquipmentDefinitions.NONE || it in location.availableEquipment
        }
    ) {
        exercise
    } else {
        val original = catalog.firstOrNull {
            it.source == exercise.reference.catalogSource && it.externalId == exercise.reference.catalogExternalId
        }
        original?.let { alternatives(it, catalog, location).firstOrNull() }?.let { replacement ->
            exercise.copy(
                reference = ExerciseReference(
                    kind = ExerciseReferenceKind.CATALOG,
                    catalogSource = replacement.source,
                    catalogExternalId = replacement.externalId,
                    catalogExerciseId = replacement.id,
                    snapshot = ExerciseSnapshot(
                        replacement.name,
                        replacement.trackingType,
                        replacement.equipment.toSortedSet().ifEmpty { sortedSetOf(EquipmentDefinitions.NONE) },
                        replacement.muscles.firstOrNull { it.role == MuscleRole.PRIMARY }?.slug,
                    ),
                ),
            )
        } ?: exercise
    }
}

enum class PlanStructureKind { WEEK, DAY, BLOCK }

class EditTrainingPlanUseCase(
    private val repository: TrainingPlanRepository,
    private val ids: UuidProvider,
) {
    suspend fun addExercise(plan: TrainingPlan, blockId: String, reference: ExerciseReference) = save(
        plan.mapBlock(blockId) { block ->
            block.copy(
                exercises = block.exercises + PlanExercise(
                    ids.newUuid(),
                    block.exercises.size,
                    reference,
                    sets = listOf(defaultSet(reference, ids.newUuid())),
                ),
            )
        },
    )

    suspend fun addWeek(plan: TrainingPlan) = save(plan.copy(weeks = plan.weeks + PlanWeek(
        ids.newUuid(),
        plan.weeks.size,
        "Week ${plan.weeks.size + 1}",
    )))

    suspend fun addDay(plan: TrainingPlan, weekId: String) = save(plan.mapWeek(weekId) { week ->
        val position = week.days.size
        week.copy(days = week.days + PlanDay(ids.newUuid(), position, "Day ${position + 1}"))
    })

    suspend fun addBlock(plan: TrainingPlan, dayId: String) = save(plan.mapDay(dayId) { day ->
        val position = day.blocks.size
        day.copy(blocks = day.blocks + PlanBlock(ids.newUuid(), position, PlanBlockType.MAIN, "Block ${position + 1}"))
    })

    suspend fun removeStructure(plan: TrainingPlan, kind: PlanStructureKind, id: String) = save(
        when (kind) {
            PlanStructureKind.WEEK -> plan.copy(weeks = plan.weeks.filterNot { it.id == id }.reindexWeeks())
            PlanStructureKind.DAY -> plan.copy(weeks = plan.weeks.map { week ->
                week.copy(days = week.days.filterNot { it.id == id }.reindexDays())
            })
            PlanStructureKind.BLOCK -> plan.copy(weeks = plan.weeks.map { week ->
                week.copy(days = week.days.map { day ->
                    day.copy(blocks = day.blocks.filterNot { it.id == id }.reindexBlocks())
                })
            })
        },
    )

    suspend fun moveStructure(plan: TrainingPlan, kind: PlanStructureKind, id: String, delta: Int) = save(
        when (kind) {
            PlanStructureKind.WEEK -> plan.copy(weeks = plan.weeks.moveItem(id, delta) { it.id }.reindexWeeks())
            PlanStructureKind.DAY -> plan.copy(weeks = plan.weeks.map { week ->
                week.copy(days = week.days.moveItem(id, delta) { it.id }.reindexDays())
            })
            PlanStructureKind.BLOCK -> plan.copy(weeks = plan.weeks.map { week ->
                week.copy(days = week.days.map { day ->
                    day.copy(blocks = day.blocks.moveItem(id, delta) { it.id }.reindexBlocks())
                })
            })
        },
    )

    suspend fun removeExercise(plan: TrainingPlan, exerciseId: String) = save(plan.mapExerciseLists { rows ->
        rows.filterNot { it.id == exerciseId }.reindexExercises()
    })

    suspend fun moveExercise(plan: TrainingPlan, exerciseId: String, delta: Int) = save(
        plan.mapExerciseLists { it.moveItem(exerciseId, delta) { row -> row.id }.reindexExercises() },
    )

    suspend fun saveSet(plan: TrainingPlan, exerciseId: String, set: SetPrescription) = save(
        plan.mapExercise(exerciseId) { exercise ->
            val rows = if (exercise.sets.any { it.id == set.id }) {
                exercise.sets.map { if (it.id == set.id) set else it }
            } else {
                exercise.sets + set.copy(position = exercise.sets.size)
            }
            exercise.copy(sets = rows.sortedBy { it.position })
        },
    )

    suspend fun addSet(plan: TrainingPlan, exerciseId: String) = save(plan.mapExercise(exerciseId) { exercise ->
        exercise.copy(sets = exercise.sets + defaultSet(exercise.reference, ids.newUuid()).copy(position = exercise.sets.size))
    })

    suspend fun deleteSet(plan: TrainingPlan, exerciseId: String, setId: String) = save(
        plan.mapExercise(exerciseId) { exercise ->
            exercise.copy(sets = exercise.sets.filterNot { it.id == setId }.mapIndexed { index, row ->
                row.copy(position = index)
            })
        },
    )

    suspend fun moveSet(plan: TrainingPlan, exerciseId: String, setId: String, delta: Int) = save(
        plan.mapExercise(exerciseId) { exercise ->
            exercise.copy(sets = exercise.sets.moveItem(setId, delta) { it.id }.mapIndexed { index, row ->
                row.copy(position = index)
            })
        },
    )

    private suspend fun save(plan: TrainingPlan) = repository.update(plan)
}

private fun defaultSet(reference: ExerciseReference, id: String) = SetPrescription(
    id = id,
    position = 0,
    repsMin = 8.takeIf { reference.snapshot.trackingType in setOf(TrackingType.REPS, TrackingType.REPS_WEIGHT) },
    repsMax = 8.takeIf { reference.snapshot.trackingType in setOf(TrackingType.REPS, TrackingType.REPS_WEIGHT) },
    durationSeconds = 30.takeIf {
        reference.snapshot.trackingType in setOf(TrackingType.DURATION, TrackingType.DISTANCE_DURATION)
    },
    restSeconds = 90,
)

private fun TrainingPlan.mapBlock(id: String, transform: (PlanBlock) -> PlanBlock) = copy(
    weeks = weeks.map { week -> week.copy(days = week.days.map { day ->
        day.copy(blocks = day.blocks.map { if (it.id == id) transform(it) else it })
    }) },
)

private fun TrainingPlan.mapWeek(id: String, transform: (PlanWeek) -> PlanWeek) =
    copy(weeks = weeks.map { if (it.id == id) transform(it) else it })

private fun TrainingPlan.mapDay(id: String, transform: (PlanDay) -> PlanDay) = copy(
    weeks = weeks.map { week -> week.copy(days = week.days.map { if (it.id == id) transform(it) else it }) },
)

private fun TrainingPlan.mapExerciseLists(transform: (List<PlanExercise>) -> List<PlanExercise>) = copy(
    weeks = weeks.map { week -> week.copy(days = week.days.map { day ->
        day.copy(blocks = day.blocks.map { block -> block.copy(exercises = transform(block.exercises)) })
    }) },
)

private fun TrainingPlan.mapExercise(id: String, transform: (PlanExercise) -> PlanExercise) = copy(
    weeks = weeks.map { week -> week.copy(days = week.days.map { day ->
        day.copy(blocks = day.blocks.map { block ->
            block.copy(exercises = block.exercises.map { if (it.id == id) transform(it) else it })
        })
    }) },
)

private fun List<PlanWeek>.reindexWeeks() = mapIndexed { index, row -> row.copy(position = index, weekIndex = index) }
private fun List<PlanDay>.reindexDays() = mapIndexed { index, row -> row.copy(position = index, relativeDayIndex = index) }
private fun List<PlanBlock>.reindexBlocks() = mapIndexed { index, row -> row.copy(position = index) }
private fun List<PlanExercise>.reindexExercises() = mapIndexed { index, row -> row.copy(position = index) }

private fun <T> List<T>.moveItem(id: String, delta: Int, identifier: (T) -> String): List<T> {
    val from = indexOfFirst { identifier(it) == id }
    if (from < 0) return this
    val to = (from + delta).coerceIn(0, lastIndex)
    return if (from == to) this else toMutableList().apply { add(to, removeAt(from)) }
}

enum class PlanScheduleActivationDecision { KEEP_CURRENT_SCHEDULE, START_NEW_SCHEDULE_SETUP, CANCEL }

data class PlanActivationResult(
    val activated: Boolean,
    val scheduleSetupRequired: Boolean = false,
)

class SetActiveTrainingPlanUseCase(
    private val repository: TrainingPlanRepository,
    private val calendarRepository: TrainingCalendarRepository,
) {
    suspend operator fun invoke(
        id: String,
        decision: PlanScheduleActivationDecision? = null,
    ): PlanActivationResult {
        val activeSchedule = calendarRepository.observeActiveSchedule().first()
        if (activeSchedule != null && activeSchedule.planId != id) {
            ensure(decision != null) { "PLAN_SCHEDULE_DECISION_REQUIRED" }
            when (decision) {
                PlanScheduleActivationDecision.CANCEL -> return PlanActivationResult(false)
                PlanScheduleActivationDecision.KEEP_CURRENT_SCHEDULE -> Unit
                PlanScheduleActivationDecision.START_NEW_SCHEDULE_SETUP -> {
                    calendarRepository.saveSchedule(activeSchedule.copy(isActive = false))
                }
                null -> error("Decision was validated above.")
            }
        }
        repository.setActive(id)
        return PlanActivationResult(
            activated = true,
            scheduleSetupRequired = decision == PlanScheduleActivationDecision.START_NEW_SCHEDULE_SETUP,
        )
    }
}

class ArchiveTrainingPlanUseCase(
    private val repository: TrainingPlanRepository,
    private val calendarRepository: TrainingCalendarRepository,
) {
    suspend operator fun invoke(id: String, archived: Boolean) {
        if (archived) deactivateScheduleForPlan(calendarRepository, id)
        repository.setArchived(id, archived)
    }
}

class DeleteTrainingPlanUseCase(
    private val repository: TrainingPlanRepository,
    private val calendarRepository: TrainingCalendarRepository,
) {
    suspend operator fun invoke(id: String) {
        deactivateScheduleForPlan(calendarRepository, id)
        repository.delete(id)
    }
}

private suspend fun deactivateScheduleForPlan(repository: TrainingCalendarRepository, planId: String) {
    val schedule = repository.observeActiveSchedule().first()
    if (schedule?.planId == planId) repository.saveSchedule(schedule.copy(isActive = false))
}

class SeedStarterTrainingPlansUseCase(private val repository: TrainingPlanRepository) {
    suspend operator fun invoke() = repository.seedStarterPlans()
}

fun TrainingPlan.snapshot(
    dayId: String,
    plannedDurationMinutes: Int,
    trainingLocationId: String?,
    scheduledStartEpochMs: Long?,
    timeZoneId: String,
): WorkoutPlanSnapshot {
    val day = weeks.asSequence().flatMap { it.days }.firstOrNull { it.id == dayId }
        ?: error("Plan day does not exist.")
    ensure(plannedDurationMinutes in 1..1_440) { "Planned duration must be between 1 and 1440 minutes." }
    ensure(timeZoneId.isNotBlank()) { "A workout snapshot requires a time zone." }
    return WorkoutPlanSnapshot(
        planId = id,
        planRevision = revision,
        planName = name,
        dayId = day.id,
        dayTitle = day.title,
        plannedDurationMinutes = plannedDurationMinutes,
        trainingLocationId = trainingLocationId,
        scheduledStartEpochMs = scheduledStartEpochMs,
        timeZoneId = timeZoneId,
        exercises = day.blocks.sortedBy { it.position }.flatMap { block ->
            block.exercises.sortedBy { it.position }.map { exercise ->
                WorkoutPlanExerciseSnapshot(exercise.id, exercise.reference, exercise.sets.sortedBy { it.position })
            }
        },
    )
}
