package at.fitnessplatform.feature.main

import at.fitnessplatform.core.model.*
import at.fitnessplatform.core.testing.MainDispatcherRule
import at.fitnessplatform.domain.CatalogRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import app.cash.turbine.test

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test fun `offline first seed filters and refresh failure preserve catalog`() = runTest {
        val repository = FakeCatalogRepository()
        val viewModel = CatalogViewModel(repository)
        val states = mutableListOf<CatalogUiState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect { states += it } }
        advanceUntilIdle()
        assertEquals(2, states.last().exercises.size)
        viewModel.setMuscle("core")
        advanceUntilIdle()
        assertEquals(listOf("Plank"), states.last().exercises.map { it.name })
        viewModel.setEquipment("bodyweight")
        advanceUntilIdle()
        assertEquals(1, states.last().exercises.size)
        repository.failRefresh = true
        viewModel.refresh()
        advanceUntilIdle()
        assertTrue(states.last().offline)
        assertFalse(states.last().loading)
        assertEquals("Plank", states.last().exercises.single().name)
        job.cancel()
    }

    @Test
    fun `seed failure leaves loading and exposes recoverable error`() = runTest {
        val repository = FakeCatalogRepository().apply { failSeed = true }
        val viewModel = CatalogViewModel(repository)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect {}
        }

        advanceUntilIdle()

        assertFalse(viewModel.state.value.loading)
        assertTrue(viewModel.state.value.offline)
        assertEquals("CATALOG_SEED_FAILED", viewModel.state.value.error)
        job.cancel()
    }

    @Test
    fun `detail distinguishes initial loading from not found`() = runTest {
        val viewModel = CatalogViewModel(FakeCatalogRepository())

        viewModel.detailState("missing").test {
            assertEquals(CatalogDetailState.Loading, awaitItem())
            assertEquals(CatalogDetailState.NotFound, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `detail maps repository failure to safe error state`() = runTest {
        val repository = FakeCatalogRepository().apply { failDetail = true }
        val viewModel = CatalogViewModel(repository)

        viewModel.detailState("broken").test {
            assertEquals(CatalogDetailState.Loading, awaitItem())
            assertEquals(CatalogDetailState.Error("CATALOG_DETAIL_FAILED"), awaitItem())
            awaitComplete()
        }
    }
}

private class FakeCatalogRepository : CatalogRepository {
    private val rows = MutableStateFlow<List<CatalogExercise>>(emptyList())
    var failRefresh = false
    var failSeed = false
    var failDetail = false
    override fun observeCatalog(filter: CatalogFilter): Flow<List<CatalogExercise>> = rows.map { list ->
        list.filter { row -> (filter.muscle == null || row.muscles.any { it.slug == filter.muscle }) && (filter.equipment == null || filter.equipment in row.equipment) }
    }
    override fun observeExercise(id: String): Flow<CatalogExercise?> = if (failDetail) {
        flow { error("database internals must not reach the UI") }
    } else {
        rows.map { list -> list.firstOrNull { it.id == id } }
    }
    override fun observeMuscles() = MutableStateFlow(listOf(Muscle("core", "Core"), Muscle("legs", "Legs")))
    override fun observeEquipment() = MutableStateFlow(listOf(Equipment("bodyweight", "Bodyweight"), Equipment("bench", "Bench")))
    override suspend fun seedIfEmpty() {
        if (failSeed) error("seed failure")
        if (rows.value.isEmpty()) rows.value = listOf(catalog("1", "Plank", "core", "bodyweight"), catalog("2", "Squat", "legs", "bench"))
    }
    override suspend fun refresh() { if (failRefresh) error("offline") }
    private fun catalog(id: String, name: String, muscle: String, equipment: String) = CatalogExercise(
        id, id, "demo", "self-authored", "CC0-1.0", "https://creativecommons.org/publicdomain/zero/1.0/", "1",
        CatalogStatus.PUBLISHED, true, name, "description", TrackingType.REPS,
        listOf(CatalogMuscle(muscle, MuscleRole.PRIMARY)), listOf(equipment),
    )
}
