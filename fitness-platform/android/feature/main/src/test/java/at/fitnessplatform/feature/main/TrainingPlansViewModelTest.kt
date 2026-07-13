package at.fitnessplatform.feature.main

import at.fitnessplatform.core.model.CatalogExercise
import at.fitnessplatform.core.model.CatalogFilter
import at.fitnessplatform.core.model.Clock
import at.fitnessplatform.core.model.CustomExercise
import at.fitnessplatform.core.model.Equipment
import at.fitnessplatform.core.model.ExerciseConflictResolution
import at.fitnessplatform.core.model.Muscle
import at.fitnessplatform.core.model.TrackingType
import at.fitnessplatform.core.model.TrainingLocation
import at.fitnessplatform.core.model.TrainingPlan
import at.fitnessplatform.core.model.TrainingPlanGoal
import at.fitnessplatform.core.model.UuidProvider
import at.fitnessplatform.core.testing.MainDispatcherRule
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
import at.fitnessplatform.domain.TrainingPlanRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrainingPlansViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test fun `view model creates edits reorders and separates private picker choices`() = runTest {
        val plans = MutablePlanRepository()
        val ids = SequentialIds()
        val viewModel = TrainingPlansViewModel(
            ObserveTrainingPlansUseCase(plans),
            SaveTrainingPlanUseCase(plans),
            CopyTrainingPlanUseCase(plans),
            SetActiveTrainingPlanUseCase(plans),
            ArchiveTrainingPlanUseCase(plans),
            DeleteTrainingPlanUseCase(plans),
            SeedStarterTrainingPlansUseCase(plans),
            EmptyCatalogRepository(),
            OneExerciseRepository(),
            EmptyLocationRepository(),
            FindCompatibleAlternativesUseCase(),
            ids,
            object : Clock { override fun nowEpochMs() = 100L },
        )
        advanceUntilIdle()
        assertEquals(listOf("Your private exercise"), viewModel.state.value.choices.map { it.name })

        viewModel.create("profile", "Strength", TrainingPlanGoal.STRENGTH)
        advanceUntilIdle()
        val created = viewModel.state.value.selectedPlan ?: error("plan missing")
        viewModel.addExercise(
            created,
            created.weeks.single().days.single().blocks.single().id,
            viewModel.state.value.choices.single(),
        )
        advanceUntilIdle()
        val withExercise = viewModel.state.value.selectedPlan ?: error("exercise missing")
        assertEquals("Your private exercise", withExercise.exercise().reference.snapshot.name)

        val prescription = withExercise.exercise().sets.single().copy(repsMin = 12, repsMax = 12, restSeconds = 120)
        viewModel.saveSet(withExercise, withExercise.exercise().id, prescription)
        advanceUntilIdle()
        assertEquals(12, viewModel.state.value.selectedPlan?.exercise()?.sets?.single()?.repsMin)

        val withSet = viewModel.state.value.selectedPlan ?: error("set update missing")
        viewModel.addSet(withSet, withSet.exercise().id)
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.selectedPlan?.exercise()?.sets?.size)

        val withSecondSet = viewModel.state.value.selectedPlan ?: error("set add missing")
        viewModel.addWeek(withSecondSet)
        advanceUntilIdle()
        val withWeek = viewModel.state.value.selectedPlan ?: error("week add missing")
        assertEquals(2, withWeek.weeks.size)
        viewModel.addDay(withWeek, withWeek.weeks.last().id)
        advanceUntilIdle()
        val withDay = viewModel.state.value.selectedPlan ?: error("day add missing")
        viewModel.addBlock(withDay, withDay.weeks.last().days.single().id)
        advanceUntilIdle()
        val structured = viewModel.state.value.selectedPlan ?: error("block add missing")
        assertEquals(1, structured.weeks.last().days.single().blocks.size)
        viewModel.removeStructure(
            structured,
            PlanStructureKind.BLOCK,
            structured.weeks.last().days.single().blocks.single().id,
        )
        advanceUntilIdle()
        assertTrue(viewModel.state.value.selectedPlan?.weeks?.last()?.days?.single()?.blocks?.isEmpty() == true)

        viewModel.copy(created.id)
        advanceUntilIdle()
        val copied = viewModel.state.value.selectedPlan ?: error("copy missing")
        viewModel.activate(copied.id)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.plans.single { it.id == copied.id }.isActive)
        viewModel.archive(copied.id, true)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.plans.single { it.id == copied.id }.isArchived)
        assertFalse(viewModel.state.value.saving)
    }

    @Test fun `view model surfaces failed save without replacing previous plan`() = runTest {
        val plans = MutablePlanRepository().apply { failSave = true }
        val viewModel = TrainingPlansViewModel(
            ObserveTrainingPlansUseCase(plans), SaveTrainingPlanUseCase(plans), CopyTrainingPlanUseCase(plans),
            SetActiveTrainingPlanUseCase(plans), ArchiveTrainingPlanUseCase(plans), DeleteTrainingPlanUseCase(plans),
            SeedStarterTrainingPlansUseCase(plans),
            EmptyCatalogRepository(), OneExerciseRepository(), EmptyLocationRepository(),
            FindCompatibleAlternativesUseCase(), SequentialIds(), object : Clock { override fun nowEpochMs() = 1L },
        )
        advanceUntilIdle()
        viewModel.create("profile", "Rejected", TrainingPlanGoal.CUSTOM)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.error != null)
        assertTrue(viewModel.state.value.plans.isEmpty())
    }
}

private fun TrainingPlan.exercise() = weeks.single().days.single().blocks.single().exercises.single()

private class SequentialIds : UuidProvider {
    private var next = 0
    override fun newUuid() = "id-${next++}"
}

private class MutablePlanRepository : TrainingPlanRepository {
    val state = MutableStateFlow<List<TrainingPlan>>(emptyList())
    var failSave = false
    override fun observePlans(): Flow<List<TrainingPlan>> = state
    override fun observeActivePlan(): Flow<TrainingPlan?> = MutableStateFlow(state.value.firstOrNull { it.isActive })
    override fun observePlan(id: String): Flow<TrainingPlan?> = MutableStateFlow(state.value.firstOrNull { it.id == id })
    override suspend fun getPlan(id: String) = state.value.firstOrNull { it.id == id }
    override suspend fun create(plan: TrainingPlan): TrainingPlan {
        if (failSave) error("storage failed")
        state.value += plan
        return plan
    }
    override suspend fun update(plan: TrainingPlan): TrainingPlan {
        if (failSave) error("storage failed")
        state.value = state.value.map { if (it.id == plan.id) plan else it }
        return plan
    }
    override suspend fun copy(id: String): TrainingPlan {
        val copied = requireNotNull(getPlan(id)).copy(id = "$id-copy", sourceTemplateId = id, isActive = false)
        state.value += copied
        return copied
    }
    override suspend fun setActive(id: String) {
        state.value = state.value.map { it.copy(isActive = it.id == id) }
    }
    override suspend fun setArchived(id: String, archived: Boolean) {
        state.value = state.value.map { if (it.id == id) it.copy(isArchived = archived, isActive = false) else it }
    }
    override suspend fun delete(id: String) { state.value = state.value.filterNot { it.id == id } }
    override suspend fun seedStarterPlans() = Unit
}

private class EmptyCatalogRepository : CatalogRepository {
    override fun observeCatalog(filter: CatalogFilter): Flow<List<CatalogExercise>> = MutableStateFlow(emptyList())
    override fun observeExercise(id: String): Flow<CatalogExercise?> = MutableStateFlow(null)
    override fun observeMuscles(): Flow<List<Muscle>> = MutableStateFlow(emptyList())
    override fun observeEquipment(): Flow<List<Equipment>> = MutableStateFlow(emptyList())
    override suspend fun seedIfEmpty() = Unit
    override suspend fun refresh() = Unit
}

private class OneExerciseRepository : ExerciseRepository {
    private val row = CustomExercise(
        "private", "profile", "Your private exercise", "", "legs", "none", TrackingType.REPS, "", 1, 1,
    )
    override fun observeExercises(): Flow<List<CustomExercise>> = MutableStateFlow(listOf(row))
    override fun observeExerciseConflicts() = MutableStateFlow(emptyList<at.fitnessplatform.core.model.ExerciseConflict>())
    override suspend fun getExercise(id: String) = row.takeIf { it.id == id }
    override suspend fun create(
        name: String,
        description: String,
        primaryMuscleGroup: String,
        requiredEquipment: String,
        trackingType: TrackingType,
        notes: String,
    ) = error("unused")
    override suspend fun update(exercise: CustomExercise) = error("unused")
    override suspend fun delete(id: String) = Unit
    override suspend fun resolveConflict(
        exerciseId: String,
        resolution: ExerciseConflictResolution,
        mergedExercise: CustomExercise?,
    ) = Unit
}

private class EmptyLocationRepository : TrainingLocationRepository {
    override fun observeLocations(): Flow<List<TrainingLocation>> = MutableStateFlow(emptyList())
    override fun observeActiveLocation(): Flow<TrainingLocation?> = MutableStateFlow(null)
    override suspend fun getLocation(id: String): TrainingLocation? = null
    override suspend fun create(
        name: String,
        type: at.fitnessplatform.core.model.LocationType,
        equipmentSlugs: Set<String>,
    ) = error("unused")
    override suspend fun update(location: TrainingLocation) = error("unused")
    override suspend fun setActive(id: String) = Unit
    override suspend fun replaceEquipment(id: String, equipmentSlugs: Set<String>) = Unit
    override suspend fun delete(id: String) = Unit
}
