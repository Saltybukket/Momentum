package at.fitnessplatform.feature.main

import at.fitnessplatform.core.model.LocationPreset
import at.fitnessplatform.core.model.LocationType
import at.fitnessplatform.core.model.TrainingLocation
import at.fitnessplatform.core.testing.MainDispatcherRule
import at.fitnessplatform.domain.CreateTrainingLocationUseCase
import at.fitnessplatform.domain.DeleteTrainingLocationUseCase
import at.fitnessplatform.domain.ObserveTrainingLocationsUseCase
import at.fitnessplatform.domain.SelectActiveTrainingLocationUseCase
import at.fitnessplatform.domain.TrainingLocationRepository
import at.fitnessplatform.domain.UpdateLocationEquipmentUseCase
import at.fitnessplatform.domain.UpdateTrainingLocationUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrainingLocationsViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test fun `view model creates activates edits equipment and deletes locations`() = runTest {
        val repository = MutableLocationRepository()
        val viewModel = viewModel(repository)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        viewModel.create("Home", LocationType.HOME, LocationPreset.HOME_BASIC)
        viewModel.create("Gym", LocationType.FITNESS_CENTER, LocationPreset.GYM_FULL)
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.locations.size)
        assertEquals("Home", viewModel.state.value.active?.name)

        val gym = viewModel.state.value.locations.single { it.name == "Gym" }
        viewModel.setActive(gym.id)
        viewModel.saveEquipment(gym.id, setOf("mat"))
        advanceUntilIdle()
        assertEquals("Gym", viewModel.state.value.active?.name)
        assertEquals(setOf("mat"), viewModel.state.value.active?.equipmentSlugs)

        viewModel.delete(gym.id)
        advanceUntilIdle()
        assertEquals("Home", viewModel.state.value.active?.name)
        assertFalse(viewModel.state.value.busy)
        job.cancel()
    }

    @Test fun `validation error is exposed without creating partial state`() = runTest {
        val repository = MutableLocationRepository()
        val viewModel = viewModel(repository)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        viewModel.create(" ", LocationType.CUSTOM, LocationPreset.EMPTY_CUSTOM)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.error != null)
        assertTrue(viewModel.state.value.locations.isEmpty())
        job.cancel()
    }

    private fun viewModel(repository: MutableLocationRepository) = TrainingLocationsViewModel(
        ObserveTrainingLocationsUseCase(repository),
        CreateTrainingLocationUseCase(repository),
        UpdateTrainingLocationUseCase(repository),
        SelectActiveTrainingLocationUseCase(repository),
        UpdateLocationEquipmentUseCase(repository),
        DeleteTrainingLocationUseCase(repository),
    )
}

private class MutableLocationRepository : TrainingLocationRepository {
    private val state = MutableStateFlow<List<TrainingLocation>>(emptyList())
    override fun observeLocations(): Flow<List<TrainingLocation>> = state
    override fun observeActiveLocation(): Flow<TrainingLocation?> = state.map { rows -> rows.firstOrNull { it.isActive } }
    override suspend fun getLocation(id: String) = state.value.firstOrNull { it.id == id }
    override suspend fun create(name: String, type: LocationType, equipmentSlugs: Set<String>): TrainingLocation {
        val row = TrainingLocation("id-${state.value.size}", name, type, equipmentSlugs, state.value.isEmpty(), 1, 1)
        state.value += row
        return row
    }
    override suspend fun update(location: TrainingLocation): TrainingLocation = location.also { updated ->
        state.value = state.value.map { if (it.id == updated.id) updated else it }
    }
    override suspend fun setActive(id: String) { state.value = state.value.map { it.copy(isActive = it.id == id) } }
    override suspend fun replaceEquipment(id: String, equipmentSlugs: Set<String>) {
        state.value = state.value.map { if (it.id == id) it.copy(equipmentSlugs = equipmentSlugs) else it }
    }
    override suspend fun delete(id: String) {
        val wasActive = state.value.firstOrNull { it.id == id }?.isActive == true
        state.value = state.value.filterNot { it.id == id }
        if (wasActive && state.value.isNotEmpty()) {
            state.value = state.value.mapIndexed { index, location -> location.copy(isActive = index == 0) }
        }
    }
}
