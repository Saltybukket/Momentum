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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TrainingLocationsUiState(
    val loading: Boolean = true,
    val locations: List<TrainingLocation> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
) {
    val active: TrainingLocation? get() = locations.firstOrNull { it.isActive }
}

@HiltViewModel
class TrainingLocationsViewModel @Inject constructor(
    observeLocations: ObserveTrainingLocationsUseCase,
    private val createLocation: CreateTrainingLocationUseCase,
    private val updateLocation: UpdateTrainingLocationUseCase,
    private val selectActive: SelectActiveTrainingLocationUseCase,
    private val updateEquipment: UpdateLocationEquipmentUseCase,
    private val deleteLocation: DeleteTrainingLocationUseCase,
) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val state = combine(observeLocations(), busy, error) { locations, isBusy, message ->
        TrainingLocationsUiState(false, locations, isBusy, message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrainingLocationsUiState())

    fun create(name: String, type: LocationType, preset: LocationPreset) = operation {
        createLocation(name, type, LocationPresets.equipment(preset))
    }

    fun rename(location: TrainingLocation, name: String, type: LocationType) = operation {
        updateLocation(location.copy(name = name, type = type))
    }

    fun setActive(id: String) = operation { selectActive(id) }
    fun saveEquipment(id: String, slugs: Set<String>) = operation { updateEquipment(id, slugs) }
    fun delete(id: String) = operation { deleteLocation(id) }
    fun clearError() { error.value = null }

    private fun operation(block: suspend () -> Unit) = viewModelScope.launch {
        busy.value = true
        error.value = null
        runCatching { block() }.onFailure { error.value = it.message ?: "LOCATION_OPERATION_FAILED" }
        busy.value = false
    }
}
