package at.fitnessplatform.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.fitnessplatform.core.model.LocationPreset
import at.fitnessplatform.core.model.LocationPresets
import at.fitnessplatform.core.model.LocationType
import at.fitnessplatform.core.model.TrainingLocation
import at.fitnessplatform.domain.CreateTrainingLocationUseCase
import at.fitnessplatform.domain.DeleteTrainingLocationUseCase
import at.fitnessplatform.domain.ObserveTrainingLocationsUseCase
import at.fitnessplatform.domain.SelectActiveTrainingLocationUseCase
import at.fitnessplatform.domain.UpdateLocationEquipmentUseCase
import at.fitnessplatform.domain.UpdateTrainingLocationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LocationOperation { CREATE, UPDATE, ACTIVATE, EQUIPMENT, DELETE }

data class TrainingLocationsUiState(
    val loading: Boolean = true,
    val locations: List<TrainingLocation> = emptyList(),
    val saving: LocationOperation? = null,
    val completedOperation: LocationOperation? = null,
    val error: String? = null,
) {
    val active: TrainingLocation? get() = locations.firstOrNull { it.isActive }
    val busy: Boolean get() = saving != null
}

@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TrainingLocationsViewModel @Inject constructor(
    private val observeLocations: ObserveTrainingLocationsUseCase,
    private val createLocation: CreateTrainingLocationUseCase,
    private val updateLocation: UpdateTrainingLocationUseCase,
    private val selectActive: SelectActiveTrainingLocationUseCase,
    private val updateEquipment: UpdateLocationEquipmentUseCase,
    private val deleteLocation: DeleteTrainingLocationUseCase,
) : ViewModel() {
    private val saving = MutableStateFlow<LocationOperation?>(null)
    private val completed = MutableStateFlow<LocationOperation?>(null)
    private val error = MutableStateFlow<String?>(null)
    private val sourceFailed = MutableStateFlow(false)
    private val reload = MutableStateFlow(0)
    private val locations = reload.flatMapLatest {
        observeLocations()
            .onStart { sourceFailed.value = false }
            .catch {
                sourceFailed.value = true
                error.value = "LOCATION_LOAD_FAILED"
                emit(emptyList())
            }
    }

    val state = combine(locations, saving, completed, error, sourceFailed) { rows, operation, done, message, failed ->
        TrainingLocationsUiState(false, rows, operation, done, message ?: if (failed) "LOCATION_LOAD_FAILED" else null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrainingLocationsUiState())

    fun create(name: String, type: LocationType, preset: LocationPreset) = operation(LocationOperation.CREATE) {
        createLocation(name, type, LocationPresets.equipment(preset))
    }

    fun rename(location: TrainingLocation, name: String, type: LocationType) = operation(LocationOperation.UPDATE) {
        updateLocation(location.copy(name = name, type = type))
    }

    fun setActive(id: String) = operation(LocationOperation.ACTIVATE) { selectActive(id) }
    fun saveEquipment(id: String, slugs: Set<String>) = operation(LocationOperation.EQUIPMENT) { updateEquipment(id, slugs) }
    fun delete(id: String) = operation(LocationOperation.DELETE) { deleteLocation(id) }
    fun clearError() { error.value = null }
    fun retry() { error.value = null; reload.value += 1 }
    fun acknowledgeCompletion() { completed.value = null }

    private fun operation(kind: LocationOperation, block: suspend () -> Unit) = viewModelScope.launch {
        if (saving.value != null) return@launch
        saving.value = kind
        completed.value = null
        error.value = null
        runCatching { block() }
            .onSuccess { completed.value = kind }
            .onFailure { error.value = "LOCATION_OPERATION_FAILED" }
        saving.value = null
    }
}
