package at.fitnessplatform.domain

import at.fitnessplatform.core.model.CatalogExercise
import at.fitnessplatform.core.model.CatalogFilter
import at.fitnessplatform.core.model.CatalogMuscle
import at.fitnessplatform.core.model.CatalogStatus
import at.fitnessplatform.core.model.Equipment
import at.fitnessplatform.core.model.EquipmentDefinitions
import at.fitnessplatform.core.model.LocationPreset
import at.fitnessplatform.core.model.LocationPresets
import at.fitnessplatform.core.model.LocationType
import at.fitnessplatform.core.model.Muscle
import at.fitnessplatform.core.model.MuscleRole
import at.fitnessplatform.core.model.TrackingType
import at.fitnessplatform.core.model.TrainingLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingLocationUseCasesTest {
    @Test fun `create normalizes names presets and rejects unknown equipment`() = runTest {
        val repository = FakeLocationRepository()
        val created = CreateTrainingLocationUseCase(repository)(
            "  Home   corner  ",
            LocationType.HOME,
            LocationPresets.equipment(LocationPreset.HOME_BASIC),
        )
        assertEquals("Home corner", created.name)
        assertTrue("open-floor" in created.equipmentSlugs)
        assertFalse(EquipmentDefinitions.NONE in created.equipmentSlugs)

        val rejected = runCatching {
            CreateTrainingLocationUseCase(repository)("Bad", LocationType.CUSTOM, setOf("teleporter"))
        }.exceptionOrNull()
        assertTrue(rejected is ValidationException)
    }

    @Test fun `compatible catalog requires an active location unless all is explicit`() = runTest {
        val locations = FakeLocationRepository()
        val catalog = FakeCatalogRepository(listOf(exercise("body", emptyList()), exercise("bar", listOf("barbell"))))
        val useCase = ObserveCompatibleCatalogUseCase(catalog, locations)

        val withoutLocation = useCase().first()
        assertTrue(withoutLocation.requiresLocationSelection)
        assertTrue(withoutLocation.exercises.isEmpty())
        assertEquals(2, useCase(showAll = true).first().exercises.size)

        locations.create("Home", LocationType.HOME, setOf("mat"))
        assertEquals(listOf("body"), useCase().first().exercises.map { it.id })
    }

    @Test fun `alternatives share primary muscle and rank tracking type deterministically`() {
        val location = location(equipment = setOf("dumbbells"))
        val original = exercise("original", listOf("barbell"), TrackingType.REPS_WEIGHT)
        val duration = exercise("duration", listOf("dumbbells"), TrackingType.DURATION)
        val preferredB = exercise("preferred-b", listOf("dumbbells"), TrackingType.REPS_WEIGHT, "Zulu")
        val preferredA = exercise("preferred-a", listOf("dumbbells"), TrackingType.REPS_WEIGHT, "Alpha")
        val incompatible = exercise("cable", listOf("cable-machine"), TrackingType.REPS_WEIGHT)

        assertEquals(
            listOf("preferred-a", "preferred-b", "duration"),
            FindCompatibleAlternativesUseCase()(
                original,
                listOf(original, duration, preferredB, incompatible, preferredA),
                location,
            ).map { it.id },
        )
    }

    private fun location(equipment: Set<String>) = TrainingLocation(
        "location", "Home", LocationType.HOME, equipment, true, 1, 1,
    )

    private fun exercise(
        id: String,
        equipment: List<String>,
        trackingType: TrackingType = TrackingType.REPS,
        name: String = id,
    ) = CatalogExercise(
        id, id, "self-authored", "synthetic", "CC0-1.0", "https://example.test/license",
        "1", CatalogStatus.PUBLISHED, true, name, "description", trackingType,
        listOf(CatalogMuscle("legs", MuscleRole.PRIMARY)), equipment,
    )
}

private class FakeLocationRepository : TrainingLocationRepository {
    private val state = MutableStateFlow<List<TrainingLocation>>(emptyList())
    override fun observeLocations(): Flow<List<TrainingLocation>> = state
    override fun observeActiveLocation(): Flow<TrainingLocation?> = MutableStateFlow(state.value.firstOrNull { it.isActive })
    override suspend fun getLocation(id: String) = state.value.firstOrNull { it.id == id }
    override suspend fun create(name: String, type: LocationType, equipmentSlugs: Set<String>): TrainingLocation {
        val created = TrainingLocation("location-${state.value.size}", name, type, equipmentSlugs, state.value.isEmpty(), 1, 1)
        state.value += created
        return created
    }
    override suspend fun update(location: TrainingLocation) = location.also { value ->
        state.value = state.value.map { if (it.id == value.id) value else it }
    }
    override suspend fun setActive(id: String) {
        state.value = state.value.map { it.copy(isActive = it.id == id) }
    }
    override suspend fun replaceEquipment(id: String, equipmentSlugs: Set<String>) {
        state.value = state.value.map { if (it.id == id) it.copy(equipmentSlugs = equipmentSlugs) else it }
    }
    override suspend fun delete(id: String) { state.value = state.value.filterNot { it.id == id } }
}

private class FakeCatalogRepository(initial: List<CatalogExercise>) : CatalogRepository {
    private val catalog = MutableStateFlow(initial)
    override fun observeCatalog(filter: CatalogFilter): Flow<List<CatalogExercise>> = catalog
    override fun observeExercise(id: String): Flow<CatalogExercise?> = MutableStateFlow(catalog.value.firstOrNull { it.id == id })
    override fun observeMuscles(): Flow<List<Muscle>> = MutableStateFlow(emptyList())
    override fun observeEquipment(): Flow<List<Equipment>> = MutableStateFlow(emptyList())
    override suspend fun seedIfEmpty() = Unit
    override suspend fun refresh() = Unit
}
