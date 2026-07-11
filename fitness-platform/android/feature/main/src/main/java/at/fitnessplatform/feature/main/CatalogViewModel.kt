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

    init { viewModelScope.launch { repository.seedIfEmpty(); refreshing.value = false } }
    fun setMuscle(slug: String?) { filter.value = filter.value.copy(muscle = slug) }
    fun setEquipment(slug: String?) { filter.value = filter.value.copy(equipment = slug) }
    fun refresh() = viewModelScope.launch {
        refreshing.value = true
        error.value = null
        runCatching { repository.refresh() }
            .onSuccess { offline.value = false }
            .onFailure { offline.value = true; error.value = "Showing saved catalog. Refresh failed." }
        refreshing.value = false
    }
    fun observeExercise(id: String) = repository.observeExercise(id)
}
