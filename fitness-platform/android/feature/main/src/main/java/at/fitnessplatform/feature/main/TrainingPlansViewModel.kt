package at.fitnessplatform.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.fitnessplatform.core.model.CatalogExercise
import at.fitnessplatform.core.model.CatalogFilter
import at.fitnessplatform.core.model.Clock
import at.fitnessplatform.core.model.CustomExercise
import at.fitnessplatform.core.model.ExerciseReference
import at.fitnessplatform.core.model.ExerciseReferenceKind
import at.fitnessplatform.core.model.ExerciseSnapshot
import at.fitnessplatform.core.model.MuscleRole
import at.fitnessplatform.core.model.PlanBlock
import at.fitnessplatform.core.model.PlanBlockType
import at.fitnessplatform.core.model.PlanDay
import at.fitnessplatform.core.model.PlanExercise
import at.fitnessplatform.core.model.PlanWeek
import at.fitnessplatform.core.model.SetPrescription
import at.fitnessplatform.core.model.TrackingType
import at.fitnessplatform.core.model.TrainingLocation
import at.fitnessplatform.core.model.TrainingPlan
import at.fitnessplatform.core.model.TrainingPlanGoal
import at.fitnessplatform.core.model.UuidProvider
import at.fitnessplatform.domain.ArchiveTrainingPlanUseCase
import at.fitnessplatform.domain.CatalogRepository
import at.fitnessplatform.domain.CopyTrainingPlanUseCase
import at.fitnessplatform.domain.DeleteTrainingPlanUseCase
import at.fitnessplatform.domain.ExerciseRepository
import at.fitnessplatform.domain.FindCompatibleAlternativesUseCase
import at.fitnessplatform.domain.ObserveTrainingPlansUseCase
import at.fitnessplatform.domain.SaveTrainingPlanUseCase
import at.fitnessplatform.domain.SeedStarterTrainingPlansUseCase
import at.fitnessplatform.domain.SetActiveTrainingPlanUseCase
import at.fitnessplatform.domain.TrainingLocationRepository
import at.fitnessplatform.domain.isCompatibleWith
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlanExerciseChoice(
    val key: String,
    val name: String,
    val equipment: String,
    val compatible: Boolean,
    val reference: ExerciseReference,
)

enum class PlanStructureKind { WEEK, DAY, BLOCK }

data class TrainingPlansUiState(
    val loading: Boolean = true,
    val plans: List<TrainingPlan> = emptyList(),
    val selectedPlanId: String? = null,
    val choices: List<PlanExerciseChoice> = emptyList(),
    val activeLocation: TrainingLocation? = null,
    val saving: Boolean = false,
    val error: String? = null,
) {
    val selectedPlan: TrainingPlan? get() = plans.firstOrNull { it.id == selectedPlanId }
}

@HiltViewModel
@Suppress("LongParameterList", "TooManyFunctions")
class TrainingPlansViewModel @Inject constructor(
    observePlans: ObserveTrainingPlansUseCase,
    private val savePlan: SaveTrainingPlanUseCase,
    private val copyPlan: CopyTrainingPlanUseCase,
    private val activatePlan: SetActiveTrainingPlanUseCase,
    private val archivePlan: ArchiveTrainingPlanUseCase,
    private val deletePlan: DeleteTrainingPlanUseCase,
    private val seedStarterPlans: SeedStarterTrainingPlansUseCase,
    private val catalogRepository: CatalogRepository,
    private val exerciseRepository: ExerciseRepository,
    private val locationRepository: TrainingLocationRepository,
    private val alternatives: FindCompatibleAlternativesUseCase,
    private val ids: UuidProvider,
    private val clock: Clock,
) : ViewModel() {
    val state = MutableStateFlow(TrainingPlansUiState())
    private var catalog: List<CatalogExercise> = emptyList()

    init {
        viewModelScope.launch {
            runCatching { seedStarterPlans() }.onFailure { error ->
                state.update { it.copy(error = error.message ?: "PLAN_SEED_FAILED") }
            }
        }
        viewModelScope.launch {
            combine(
                observePlans(),
                catalogRepository.observeCatalog(CatalogFilter()),
                exerciseRepository.observeExercises(),
                locationRepository.observeActiveLocation(),
            ) { plans, catalogRows, privateRows, location ->
                catalog = catalogRows
                TrainingPlansUiState(
                    loading = false,
                    plans = plans,
                    selectedPlanId = state.value.selectedPlanId?.takeIf { id -> plans.any { it.id == id } },
                    choices = choices(catalogRows, privateRows, location),
                    activeLocation = location,
                    saving = state.value.saving,
                    error = state.value.error,
                )
            }.catch { error ->
                state.update { it.copy(loading = false, error = error.message ?: "PLAN_LOAD_FAILED") }
            }.collect(state)
        }
    }

    fun select(id: String?) = state.update { it.copy(selectedPlanId = id) }
    fun clearError() = state.update { it.copy(error = null) }

    fun create(ownerProfileId: String, name: String, goal: TrainingPlanGoal) = operation {
        val now = clock.nowEpochMs()
        val plan = TrainingPlan(
            id = ids.newUuid(),
            ownerProfileId = ownerProfileId,
            name = name,
            goal = goal,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            weeks = listOf(
                PlanWeek(
                    ids.newUuid(),
                    0,
                    "Week 1",
                    listOf(
                        PlanDay(
                            ids.newUuid(),
                            0,
                            "Day 1",
                            listOf(PlanBlock(ids.newUuid(), 0, PlanBlockType.MAIN, "Main")),
                        ),
                    ),
                ),
            ),
        )
        state.update { it.copy(selectedPlanId = savePlan(plan).id) }
    }

    fun rename(plan: TrainingPlan, name: String, description: String, goal: TrainingPlanGoal) = operation {
        savePlan(plan.copy(name = name, description = description, goal = goal))
    }

    fun addExercise(plan: TrainingPlan, blockId: String, choice: PlanExerciseChoice) = operation {
        val updated = plan.mapBlock(blockId) { block ->
            block.copy(
                exercises = block.exercises + PlanExercise(
                    id = ids.newUuid(),
                    position = block.exercises.size,
                    reference = choice.reference,
                    sets = listOf(defaultSet(choice.reference)),
                ),
            )
        }
        savePlan(updated)
    }

    fun addWeek(plan: TrainingPlan) = operation {
        val position = plan.weeks.size
        savePlan(plan.copy(weeks = plan.weeks + PlanWeek(ids.newUuid(), position, "Week ${position + 1}")))
    }

    fun addDay(plan: TrainingPlan, weekId: String) = operation {
        savePlan(plan.mapWeek(weekId) { week ->
            val position = week.days.size
            week.copy(days = week.days + PlanDay(ids.newUuid(), position, "Day ${position + 1}"))
        })
    }

    fun addBlock(plan: TrainingPlan, dayId: String) = operation {
        savePlan(plan.mapDay(dayId) { day ->
            val position = day.blocks.size
            day.copy(blocks = day.blocks + PlanBlock(ids.newUuid(), position, PlanBlockType.MAIN, "Block ${position + 1}"))
        })
    }

    fun removeStructure(plan: TrainingPlan, kind: PlanStructureKind, id: String) = operation {
        val updated = when (kind) {
            PlanStructureKind.WEEK -> plan.copy(
                weeks = plan.weeks.filterNot { it.id == id }.mapIndexed { index, week ->
                    week.copy(position = index, weekIndex = index)
                },
            )
            PlanStructureKind.DAY -> plan.copy(
                weeks = plan.weeks.map { week ->
                    week.copy(days = week.days.filterNot { it.id == id }.mapIndexed { index, day ->
                        day.copy(position = index, relativeDayIndex = index)
                    })
                },
            )
            PlanStructureKind.BLOCK -> plan.copy(
                weeks = plan.weeks.map { week ->
                    week.copy(days = week.days.map { day ->
                        day.copy(blocks = day.blocks.filterNot { it.id == id }.mapIndexed { index, block ->
                            block.copy(position = index)
                        })
                    })
                },
            )
        }
        savePlan(updated)
    }

    fun moveStructure(plan: TrainingPlan, kind: PlanStructureKind, id: String, delta: Int) = operation {
        val updated = when (kind) {
            PlanStructureKind.WEEK -> plan.copy(
                weeks = plan.weeks.moveItem(id, delta) { it.id }.mapIndexed { index, week ->
                    week.copy(position = index, weekIndex = index)
                },
            )
            PlanStructureKind.DAY -> plan.copy(
                weeks = plan.weeks.map { week ->
                    week.copy(days = week.days.moveItem(id, delta) { it.id }.mapIndexed { index, day ->
                        day.copy(position = index, relativeDayIndex = index)
                    })
                },
            )
            PlanStructureKind.BLOCK -> plan.copy(
                weeks = plan.weeks.map { week ->
                    week.copy(days = week.days.map { day ->
                        day.copy(blocks = day.blocks.moveItem(id, delta) { it.id }.mapIndexed { index, block ->
                            block.copy(position = index)
                        })
                    })
                },
            )
        }
        savePlan(updated)
    }

    fun removeExercise(plan: TrainingPlan, exerciseId: String) = operation {
        savePlan(plan.mapExerciseLists { exercises ->
            exercises.filterNot { it.id == exerciseId }.mapIndexed { index, exercise -> exercise.copy(position = index) }
        })
    }

    fun saveSet(plan: TrainingPlan, exerciseId: String, prescription: SetPrescription) = operation {
        savePlan(plan.mapExercise(exerciseId) { exercise ->
            val exists = exercise.sets.any { it.id == prescription.id }
            val sets = if (exists) {
                exercise.sets.map { if (it.id == prescription.id) prescription else it }
            } else {
                exercise.sets + prescription.copy(position = exercise.sets.size)
            }
            exercise.copy(sets = sets.sortedBy { it.position })
        })
    }

    fun addSet(plan: TrainingPlan, exerciseId: String) = operation {
        savePlan(plan.mapExercise(exerciseId) { exercise ->
            exercise.copy(
                sets = exercise.sets + defaultSet(exercise.reference).copy(position = exercise.sets.size),
            )
        })
    }

    fun deleteSet(plan: TrainingPlan, exerciseId: String, setId: String) = operation {
        savePlan(plan.mapExercise(exerciseId) { exercise ->
            exercise.copy(
                sets = exercise.sets.filterNot { it.id == setId }
                    .mapIndexed { index, prescription -> prescription.copy(position = index) },
            )
        })
    }

    fun move(plan: TrainingPlan, exerciseId: String, delta: Int) = operation {
        savePlan(plan.mapExerciseLists { exercises ->
                val from = exercises.indexOfFirst { it.id == exerciseId }
                if (from < 0) {
                    exercises
                } else {
                    val to = (from + delta).coerceIn(0, exercises.lastIndex)
                    if (from == to) exercises else exercises.toMutableList().apply {
                        add(to, removeAt(from))
                    }.mapIndexed { index, exercise -> exercise.copy(position = index) }
                }
            })
    }

    fun copy(id: String) = operation { state.update { it.copy(selectedPlanId = copyPlan(id).id) } }
    fun activate(id: String) = operation { activatePlan(id) }
    fun archive(id: String, archived: Boolean) = operation { archivePlan(id, archived) }
    fun delete(id: String) = operation { deletePlan(id); state.update { it.copy(selectedPlanId = null) } }

    fun adaptCopy(id: String) = operation {
        val location = locationRepository.observeActiveLocation().first()
            ?: error("Select a training location before adapting a plan.")
        val copied = copyPlan(id)
        val adapted = copied.copy(
            name = "${copied.name} · ${location.name}",
            weeks = copied.weeks.map { week ->
                week.copy(days = week.days.map { day ->
                    day.copy(blocks = day.blocks.map { block ->
                        block.copy(exercises = block.exercises.map { adaptExercise(it, location) })
                    })
                })
            },
        )
        state.update { it.copy(selectedPlanId = savePlan(adapted).id) }
    }

    private fun adaptExercise(exercise: PlanExercise, location: TrainingLocation): PlanExercise = when {
        exercise.reference.snapshot.equipment in location.availableEquipment -> exercise
        exercise.reference.snapshot.equipment == "none" -> exercise
        else -> {
            val original = catalog.firstOrNull {
                it.source == exercise.reference.catalogSource && it.externalId == exercise.reference.catalogExternalId
            }
            val replacement = original?.let { alternatives(it, catalog, location).firstOrNull() }
            replacement?.let { exercise.copy(reference = it.reference()) } ?: exercise
        }
    }

    private fun choices(
        catalog: List<CatalogExercise>,
        privateExercises: List<CustomExercise>,
        location: TrainingLocation?,
    ): List<PlanExerciseChoice> = buildList {
        addAll(catalog.map { exercise ->
            PlanExerciseChoice(
                "catalog:${exercise.source}:${exercise.externalId}",
                exercise.name,
                exercise.equipment.joinToString().ifBlank { "none" },
                location == null || exercise.isCompatibleWith(location),
                exercise.reference(),
            )
        })
        addAll(privateExercises.map { exercise ->
            PlanExerciseChoice(
                "custom:${exercise.id}",
                exercise.name,
                exercise.requiredEquipment,
                location == null || exercise.requiredEquipment == "none" ||
                    exercise.requiredEquipment in location.availableEquipment,
                ExerciseReference(
                    kind = ExerciseReferenceKind.CUSTOM,
                    customExerciseId = exercise.id,
                    snapshot = ExerciseSnapshot(
                        exercise.name,
                        exercise.trackingType,
                        exercise.requiredEquipment,
                        exercise.primaryMuscleGroup,
                    ),
                ),
            )
        })
    }

    private fun CatalogExercise.reference() = ExerciseReference(
        kind = ExerciseReferenceKind.CATALOG,
        catalogSource = source,
        catalogExternalId = externalId,
        catalogExerciseId = id,
        snapshot = ExerciseSnapshot(
            name,
            trackingType,
            equipment.firstOrNull() ?: "none",
            muscles.firstOrNull { it.role == MuscleRole.PRIMARY }?.slug,
        ),
    )

    private fun defaultSet(reference: ExerciseReference) = SetPrescription(
        id = ids.newUuid(),
        position = 0,
        repsMin = 8.takeIf {
            reference.snapshot.trackingType == TrackingType.REPS ||
                reference.snapshot.trackingType == TrackingType.REPS_WEIGHT
        },
        repsMax = 8.takeIf {
            reference.snapshot.trackingType == TrackingType.REPS ||
                reference.snapshot.trackingType == TrackingType.REPS_WEIGHT
        },
        durationSeconds = 30.takeIf {
            reference.snapshot.trackingType == TrackingType.DURATION ||
                reference.snapshot.trackingType == TrackingType.DISTANCE_DURATION
        },
        restSeconds = 90,
    )

    private fun operation(block: suspend () -> Unit) = viewModelScope.launch {
        if (state.value.saving) return@launch
        state.update { it.copy(saving = true, error = null) }
        runCatching { block() }.onFailure { error ->
            state.update { it.copy(error = error.message ?: "PLAN_OPERATION_FAILED") }
        }
        state.update { it.copy(saving = false) }
    }
}

private fun TrainingPlan.mapBlock(id: String, transform: (PlanBlock) -> PlanBlock) = copy(
    weeks = weeks.map { week ->
        week.copy(days = week.days.map { day ->
            day.copy(blocks = day.blocks.map { if (it.id == id) transform(it) else it })
        })
    },
)

private fun TrainingPlan.mapWeek(id: String, transform: (PlanWeek) -> PlanWeek) = copy(
    weeks = weeks.map { if (it.id == id) transform(it) else it },
)

private fun TrainingPlan.mapDay(id: String, transform: (PlanDay) -> PlanDay) = copy(
    weeks = weeks.map { week ->
        week.copy(days = week.days.map { if (it.id == id) transform(it) else it })
    },
)

private fun TrainingPlan.mapExerciseLists(transform: (List<PlanExercise>) -> List<PlanExercise>) = copy(
    weeks = weeks.map { week ->
        week.copy(days = week.days.map { day ->
            day.copy(blocks = day.blocks.map { block -> block.copy(exercises = transform(block.exercises)) })
        })
    },
)

private fun TrainingPlan.mapExercise(id: String, transform: (PlanExercise) -> PlanExercise) = copy(
    weeks = weeks.map { week ->
        week.copy(days = week.days.map { day ->
            day.copy(blocks = day.blocks.map { block ->
                block.copy(exercises = block.exercises.map { if (it.id == id) transform(it) else it })
            })
        })
    },
)

private fun <T> List<T>.moveItem(id: String, delta: Int, identifier: (T) -> String): List<T> {
    val from = indexOfFirst { identifier(it) == id }
    return if (from < 0) {
        this
    } else {
        val to = (from + delta).coerceIn(0, lastIndex)
        if (from == to) this else toMutableList().apply { add(to, removeAt(from)) }
    }
}
