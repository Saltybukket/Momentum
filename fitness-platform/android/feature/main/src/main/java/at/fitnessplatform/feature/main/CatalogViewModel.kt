package at.fitnessplatform.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.fitnessplatform.core.model.CatalogExercise
import at.fitnessplatform.core.model.CatalogFilter
import at.fitnessplatform.core.model.Equipment
import at.fitnessplatform.core.model.Muscle
import at.fitnessplatform.domain.CatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CatalogUiState(
    val loading: Boolean = true,
    val exercises: List<CatalogExercise> = emptyList(),
    val muscles: List<Muscle> = emptyList(),
    val equipment: List<Equipment> = emptyList(),
    val filter: CatalogFilter = CatalogFilter(),
    val offline: Boolean = false,
    val error: String? = null,
)

sealed interface CatalogDetailState {
    data object Loading : CatalogDetailState
    data class Loaded(val exercise: CatalogExercise) : CatalogDetailState
    data object NotFound : CatalogDetailState
    data class Error(val message: String) : CatalogDetailState
}

@HiltViewModel
class CatalogViewModel @Inject constructor(private val repository: CatalogRepository) : ViewModel() {
    private val filter = MutableStateFlow(CatalogFilter())
    private val refreshing = MutableStateFlow(true)
    private val offline = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val exercises = filter.flatMapLatest(repository::observeCatalog)

    val state = combine(exercises, repository.observeMuscles(), repository.observeEquipment(), filter, refreshing, offline, error) {
            values ->
        @Suppress("UNCHECKED_CAST")
        CatalogUiState(
            loading = values[4] as Boolean,
            exercises = values[0] as List<CatalogExercise>,
            muscles = values[1] as List<Muscle>,
            equipment = values[2] as List<Equipment>,
            filter = values[3] as CatalogFilter,
            offline = values[5] as Boolean,
            error = values[6] as String?,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogUiState())

    init {
        viewModelScope.launch {
            try {
                repository.seedIfEmpty()
            } catch (_: Exception) {
                offline.value = true
                error.value = "CATALOG_SEED_FAILED"
            } finally {
                refreshing.value = false
            }
        }
    }
    fun setMuscle(slug: String?) { filter.value = filter.value.copy(muscle = slug) }
    fun setEquipment(slug: String?) { filter.value = filter.value.copy(equipment = slug) }
    fun setQuery(query: String) { filter.value = filter.value.copy(query = query) }
    fun refresh() = viewModelScope.launch {
        refreshing.value = true
        error.value = null
        runCatching { repository.refresh() }
            .onSuccess { offline.value = false }
            .onFailure { offline.value = true; error.value = "CATALOG_REFRESH_FAILED" }
        refreshing.value = false
    }
    fun detailState(id: String): Flow<CatalogDetailState> = repository.observeExercise(id)
        .map<CatalogExercise?, CatalogDetailState> { exercise ->
            exercise?.let(CatalogDetailState::Loaded) ?: CatalogDetailState.NotFound
        }
        .catch { emit(CatalogDetailState.Error("CATALOG_DETAIL_FAILED")) }
        .onStart { emit(CatalogDetailState.Loading) }
}
