package at.fitnessplatform.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.fitnessplatform.core.model.CatalogExercise
import at.fitnessplatform.core.model.CatalogFilter
import at.fitnessplatform.core.model.Clock
import at.fitnessplatform.core.model.CustomExercise
import at.fitnessplatform.core.model.AvailabilityRule
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
import at.fitnessplatform.core.model.ScheduleOverride
import at.fitnessplatform.core.model.TrackingType
import at.fitnessplatform.core.model.TrainingLocation
import at.fitnessplatform.core.model.TrainingPlan
import at.fitnessplatform.core.model.TrainingPlanGoal
import at.fitnessplatform.core.model.UuidProvider
import at.fitnessplatform.domain.ArchiveTrainingPlanUseCase
import at.fitnessplatform.domain.ActivatePlanWithScheduleUseCase
import at.fitnessplatform.domain.AdaptTrainingPlanCopyUseCase
import at.fitnessplatform.domain.CatalogRepository
import at.fitnessplatform.domain.CopyTrainingPlanUseCase
import at.fitnessplatform.domain.DeleteTrainingPlanUseCase
import at.fitnessplatform.domain.ExerciseRepository
import at.fitnessplatform.domain.FindCompatibleAlternativesUseCase
import at.fitnessplatform.domain.EditTrainingPlanUseCase
import at.fitnessplatform.domain.ObserveTrainingPlansUseCase
import at.fitnessplatform.domain.SaveTrainingPlanUseCase
import at.fitnessplatform.domain.SeedStarterTrainingPlansUseCase
import at.fitnessplatform.domain.SetActiveTrainingPlanUseCase
import at.fitnessplatform.domain.TrainingLocationRepository
import at.fitnessplatform.domain.PlanStructureKind
import at.fitnessplatform.domain.PlanScheduleActivationDecision
import at.fitnessplatform.domain.PreviewPlanScheduleUseCase
import at.fitnessplatform.domain.ScheduleRuleDraft
import at.fitnessplatform.domain.ScheduleSetupPreview
import at.fitnessplatform.domain.TrainingCalendarRepository
import at.fitnessplatform.domain.isCompatibleWith
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlanExerciseChoice(
    val key: String,
    val name: String,
    val equipment: Set<String>,
    val compatible: Boolean,
    val reference: ExerciseReference,
)

data class TrainingPlansUiState(
    val today: LocalDate = LocalDate.ofEpochDay(0),
    val loading: Boolean = true,
    val plans: List<TrainingPlan> = emptyList(),
    val selectedPlanId: String? = null,
    val choices: List<PlanExerciseChoice> = emptyList(),
    val activeLocation: TrainingLocation? = null,
    val locations: List<TrainingLocation> = emptyList(),
    val availability: List<AvailabilityRule> = emptyList(),
    val overrides: List<ScheduleOverride> = emptyList(),
    val pendingActivationPlanId: String? = null,
    val pendingScheduleSetupPlanId: String? = null,
    val pendingScheduleSetup: ScheduleSetupPreview? = null,
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
    private val adaptPlanCopy: AdaptTrainingPlanCopyUseCase,
    private val editPlan: EditTrainingPlanUseCase,
    private val activatePlan: SetActiveTrainingPlanUseCase,
    private val activatePlanWithSchedule: ActivatePlanWithScheduleUseCase,
    private val previewPlanSchedule: PreviewPlanScheduleUseCase,
    private val archivePlan: ArchiveTrainingPlanUseCase,
    private val deletePlan: DeleteTrainingPlanUseCase,
    private val seedStarterPlans: SeedStarterTrainingPlansUseCase,
    private val catalogRepository: CatalogRepository,
    private val exerciseRepository: ExerciseRepository,
    private val locationRepository: TrainingLocationRepository,
    private val calendarRepository: TrainingCalendarRepository,
    private val ids: UuidProvider,
    private val clock: Clock,
) : ViewModel() {
    val state = MutableStateFlow(
        TrainingPlansUiState(
            today = Instant.ofEpochMilli(clock.nowEpochMs()).atZone(ZoneId.systemDefault()).toLocalDate(),
        ),
    )
    private var catalog: List<CatalogExercise> = emptyList()

    init {
        viewModelScope.launch {
            runCatching { seedStarterPlans() }.onFailure { error ->
                state.update { it.copy(error = error.message ?: "PLAN_SEED_FAILED") }
            }
        }
        viewModelScope.launch {
            val scheduleConstraints = combine(
                locationRepository.observeLocations(),
                calendarRepository.observeAvailability(),
                calendarRepository.observeOverrides(),
            ) { locations, availability, overrides -> Triple(locations, availability, overrides) }
            combine(
                observePlans(),
                catalogRepository.observeCatalog(CatalogFilter()),
                exerciseRepository.observeExercises(),
                scheduleConstraints,
            ) { plans, catalogRows, privateRows, constraints ->
                val (locations, availability, overrides) = constraints
                val location = locations.firstOrNull(TrainingLocation::isActive)
                catalog = catalogRows
                TrainingPlansUiState(
                    today = state.value.today,
                    loading = false,
                    plans = plans,
                    selectedPlanId = state.value.selectedPlanId?.takeIf { id -> plans.any { it.id == id } },
                    choices = choices(catalogRows, privateRows, location),
                    activeLocation = location,
                    locations = locations,
                    availability = availability,
                    overrides = overrides,
                    pendingActivationPlanId = state.value.pendingActivationPlanId,
                    pendingScheduleSetupPlanId = state.value.pendingScheduleSetupPlanId,
                    pendingScheduleSetup = state.value.pendingScheduleSetup,
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

    fun create(
        ownerProfileId: String,
        name: String,
        description: String,
        goal: TrainingPlanGoal,
        onSuccess: () -> Unit = {},
    ) = operation(onSuccess) {
        val now = clock.nowEpochMs()
        val plan = TrainingPlan(
            id = ids.newUuid(),
            ownerProfileId = ownerProfileId,
            name = name,
            description = description,
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

    fun rename(
        plan: TrainingPlan,
        name: String,
        description: String,
        goal: TrainingPlanGoal,
        onSuccess: () -> Unit = {},
    ) = operation(onSuccess) {
        savePlan(plan.copy(name = name, description = description, goal = goal))
    }

    fun addExercise(
        plan: TrainingPlan,
        blockId: String,
        choice: PlanExerciseChoice,
        onSuccess: () -> Unit = {},
    ) = operation(onSuccess) {
        editPlan.addExercise(plan, blockId, choice.reference)
    }

    fun addWeek(plan: TrainingPlan) = operation {
        editPlan.addWeek(plan)
    }

    fun addDay(plan: TrainingPlan, weekId: String) = operation {
        editPlan.addDay(plan, weekId)
    }

    fun addBlock(plan: TrainingPlan, dayId: String) = operation {
        editPlan.addBlock(plan, dayId)
    }

    fun removeStructure(plan: TrainingPlan, kind: PlanStructureKind, id: String) = operation {
        editPlan.removeStructure(plan, kind, id)
    }

    fun moveStructure(plan: TrainingPlan, kind: PlanStructureKind, id: String, delta: Int) = operation {
        editPlan.moveStructure(plan, kind, id, delta)
    }

    fun removeExercise(plan: TrainingPlan, exerciseId: String) = operation {
        editPlan.removeExercise(plan, exerciseId)
    }

    fun saveSet(
        plan: TrainingPlan,
        exerciseId: String,
        prescription: SetPrescription,
        onSuccess: () -> Unit = {},
    ) = operation(onSuccess) {
        editPlan.saveSet(plan, exerciseId, prescription)
    }

    fun addSet(plan: TrainingPlan, exerciseId: String) = operation {
        editPlan.addSet(plan, exerciseId)
    }

    fun deleteSet(plan: TrainingPlan, exerciseId: String, setId: String) = operation {
        editPlan.deleteSet(plan, exerciseId, setId)
    }

    fun moveSet(plan: TrainingPlan, exerciseId: String, setId: String, delta: Int) = operation {
        editPlan.moveSet(plan, exerciseId, setId, delta)
    }

    fun move(plan: TrainingPlan, exerciseId: String, delta: Int) = operation {
        editPlan.moveExercise(plan, exerciseId, delta)
    }

    fun copy(id: String) = operation { state.update { it.copy(selectedPlanId = copyPlan(id).id) } }
    fun activate(id: String) = viewModelScope.launch {
        if (state.value.saving) return@launch
        state.update { it.copy(saving = true, error = null) }
        runCatching { activatePlan(id) }
            .onFailure { error ->
                state.update {
                    if (error.message == "PLAN_SCHEDULE_DECISION_REQUIRED") {
                        it.copy(pendingActivationPlanId = id)
                    } else {
                        it.copy(error = error.message ?: "PLAN_OPERATION_FAILED")
                    }
                }
            }
        state.update { it.copy(saving = false) }
    }

    fun resolveActivation(decision: PlanScheduleActivationDecision) {
        val id = state.value.pendingActivationPlanId ?: return
        if (decision == PlanScheduleActivationDecision.CANCEL) {
            state.update { it.copy(pendingActivationPlanId = null) }
            return
        }
        operation {
            val result = activatePlan(id, decision)
            state.update {
                it.copy(
                    pendingActivationPlanId = null,
                    pendingScheduleSetupPlanId = id.takeIf { result.scheduleSetupRequired },
                )
            }
        }
    }

    fun previewActivationSchedule(
        startDate: LocalDate,
        timeZoneId: String,
        drafts: List<ScheduleRuleDraft>,
        onSuccess: () -> Unit = {},
    ) = operation(onSuccess) {
        val planId = requireNotNull(state.value.pendingScheduleSetupPlanId) {
            "No plan activation schedule is pending."
        }
        val plan = requireNotNull(state.value.plans.firstOrNull { it.id == planId }) {
            "Training plan does not exist."
        }
        state.update {
            it.copy(
                pendingScheduleSetup = previewPlanSchedule(
                    plan,
                    startDate,
                    timeZoneId,
                    drafts,
                    it.availability,
                    it.overrides,
                    it.locations,
                ),
            )
        }
    }

    fun confirmActivationSchedule(onSuccess: () -> Unit = {}) = operation(onSuccess) {
        val planId = requireNotNull(state.value.pendingScheduleSetupPlanId) {
            "No plan activation schedule is pending."
        }
        val plan = requireNotNull(state.value.plans.firstOrNull { it.id == planId }) {
            "Training plan does not exist."
        }
        val preview = requireNotNull(state.value.pendingScheduleSetup) {
            "No activation schedule preview is pending."
        }
        activatePlanWithSchedule(plan, preview.startDate, preview.timeZoneId, preview.drafts)
        state.update {
            it.copy(pendingScheduleSetupPlanId = null, pendingScheduleSetup = null)
        }
    }

    fun cancelActivationSchedule() {
        state.update { it.copy(pendingScheduleSetupPlanId = null, pendingScheduleSetup = null) }
    }
    fun archive(id: String, archived: Boolean) = operation { archivePlan(id, archived) }
    fun delete(id: String) = operation { deletePlan(id); state.update { it.copy(selectedPlanId = null) } }

    fun adaptCopy(id: String) = operation {
        val location = locationRepository.observeActiveLocation().first()
            ?: error("Select a training location before adapting a plan.")
        state.update { it.copy(selectedPlanId = adaptPlanCopy(id, location, catalog).id) }
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
                exercise.equipment.toSortedSet().ifEmpty { sortedSetOf("none") },
                location == null || exercise.isCompatibleWith(location),
                exercise.reference(),
            )
        })
        addAll(privateExercises.map { exercise ->
            PlanExerciseChoice(
                "custom:${exercise.id}",
                exercise.name,
                setOf(exercise.requiredEquipment),
                location == null || exercise.requiredEquipment == "none" ||
                    exercise.requiredEquipment in location.availableEquipment,
                ExerciseReference(
                    kind = ExerciseReferenceKind.CUSTOM,
                    customExerciseId = exercise.id,
                    snapshot = ExerciseSnapshot(
                        exercise.name,
                        exercise.trackingType,
                        setOf(exercise.requiredEquipment),
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
            equipment.toSortedSet().ifEmpty { sortedSetOf("none") },
            muscles.firstOrNull { it.role == MuscleRole.PRIMARY }?.slug,
        ),
    )

    private fun operation(onSuccess: () -> Unit = {}, block: suspend () -> Unit) = viewModelScope.launch {
        if (state.value.saving) return@launch
        state.update { it.copy(saving = true, error = null) }
        runCatching { block() }
            .onSuccess { onSuccess() }
            .onFailure { error -> state.update { it.copy(error = error.message ?: "PLAN_OPERATION_FAILED") } }
        state.update { it.copy(saving = false) }
    }
}
