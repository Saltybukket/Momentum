package at.fitnessplatform.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.fitnessplatform.core.model.CatalogExercise
import at.fitnessplatform.core.model.CatalogFilter
import at.fitnessplatform.core.model.Equipment
import at.fitnessplatform.core.model.Muscle
import at.fitnessplatform.core.model.TrainingLocation
import at.fitnessplatform.domain.CatalogRepository
import at.fitnessplatform.domain.ObserveCompatibleCatalogUseCase
import at.fitnessplatform.domain.FindCompatibleAlternativesUseCase
import at.fitnessplatform.domain.isCompatibleWith
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    val activeLocation: TrainingLocation? = null,
    val showAll: Boolean = false,
    val requiresLocationSelection: Boolean = false,
    val error: String? = null,
)

sealed interface CatalogDetailState {
    data object Loading : CatalogDetailState
    data class Loaded(
        val exercise: CatalogExercise,
        val compatibility: CatalogCompatibility,
        val missingEquipment: List<MissingEquipment>,
        val muscleLabels: Map<String, String>,
        val equipmentLabels: Map<String, String>,
        val alternatives: List<CatalogExercise>,
    ) : CatalogDetailState
    data object NotFound : CatalogDetailState
    data class Error(val message: String) : CatalogDetailState
}

enum class CatalogCompatibility { COMPATIBLE, LOCATION_REQUIRED, MISSING_EQUIPMENT }
data class MissingEquipment(val slug: String, val label: String?)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModel @Inject constructor(
    private val repository: CatalogRepository,
    private val compatibleCatalog: ObserveCompatibleCatalogUseCase,
    private val findAlternatives: FindCompatibleAlternativesUseCase,
) : ViewModel() {
    private val filter = MutableStateFlow(CatalogFilter())
    private val refreshing = MutableStateFlow(true)
    private val offline = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val showAll = MutableStateFlow(false)
    private val compatible = combine(filter, showAll) { selectedFilter, includeAll -> selectedFilter to includeAll }
        .flatMapLatest { (selectedFilter, includeAll) -> compatibleCatalog(selectedFilter, includeAll) }

    val state = combine(compatible, repository.observeMuscles(), repository.observeEquipment(), filter, refreshing, offline, error, showAll) {
            values ->
        @Suppress("UNCHECKED_CAST")
        CatalogUiState(
            loading = values[4] as Boolean,
            exercises = (values[0] as at.fitnessplatform.domain.CompatibleCatalogState).exercises,
            muscles = values[1] as List<Muscle>,
            equipment = values[2] as List<Equipment>,
            filter = values[3] as CatalogFilter,
            offline = values[5] as Boolean,
            error = values[6] as String?,
            activeLocation = (values[0] as at.fitnessplatform.domain.CompatibleCatalogState).activeLocation,
            requiresLocationSelection = (values[0] as at.fitnessplatform.domain.CompatibleCatalogState).requiresLocationSelection,
            showAll = values[7] as Boolean,
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
    fun setShowAll(value: Boolean) { showAll.value = value }
    fun refresh() = viewModelScope.launch {
        refreshing.value = true
        error.value = null
        runCatching { repository.refresh() }
            .onSuccess { offline.value = false }
            .onFailure { offline.value = true; error.value = "CATALOG_REFRESH_FAILED" }
        refreshing.value = false
    }
    fun detailState(id: String): Flow<CatalogDetailState> = combine(
        repository.observeExercise(id),
        compatibleCatalog(showAll = true),
        repository.observeEquipment(),
        repository.observeMuscles(),
    ) { exercise, compatibleState, equipmentLabels, muscleLabels ->
        if (exercise == null) {
            CatalogDetailState.NotFound
        } else {
            val location = compatibleState.activeLocation
            val missing = if (location == null) emptyList() else {
                exercise.equipment.filterNot { it in location.availableEquipment }
            }
            val compatibility = when {
                location == null -> CatalogCompatibility.LOCATION_REQUIRED
                missing.isEmpty() -> CatalogCompatibility.COMPATIBLE
                else -> CatalogCompatibility.MISSING_EQUIPMENT
            }
            CatalogDetailState.Loaded(
                exercise = exercise,
                compatibility = compatibility,
                missingEquipment = missing.map { slug ->
                    MissingEquipment(slug, equipmentLabels.firstOrNull { it.slug == slug }?.name)
                },
                muscleLabels = muscleLabels.associate { it.slug to it.name },
                equipmentLabels = equipmentLabels.associate { it.slug to it.name },
                alternatives = if (compatibility != CatalogCompatibility.MISSING_EQUIPMENT) emptyList() else {
                    findAlternatives(exercise, compatibleState.exercises, checkNotNull(location))
                },
            )
        }
    }
        .catch { emit(CatalogDetailState.Error("CATALOG_DETAIL_FAILED")) }
        .onStart { emit(CatalogDetailState.Loading) }
}
