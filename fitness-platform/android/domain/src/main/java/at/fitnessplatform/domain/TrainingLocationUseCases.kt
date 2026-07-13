package at.fitnessplatform.domain

import at.fitnessplatform.core.model.CatalogExercise
import at.fitnessplatform.core.model.CatalogFilter
import at.fitnessplatform.core.model.EquipmentDefinitions
import at.fitnessplatform.core.model.LocationType
import at.fitnessplatform.core.model.MuscleRole
import at.fitnessplatform.core.model.TrainingLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

private const val MAX_LOCATION_NAME_LENGTH = 80

private fun normalizedLocationName(name: String): String = name.trim().replace(Regex("\\s+"), " ").also {
    if (it.isBlank() || it.length > MAX_LOCATION_NAME_LENGTH) {
        throw ValidationException("Training location name must contain 1 to 80 characters.")
    }
}

private fun validatedEquipment(slugs: Set<String>): Set<String> {
    val normalized = slugs - EquipmentDefinitions.NONE
    val unknown = normalized - EquipmentDefinitions.slugs
    if (unknown.isNotEmpty()) throw ValidationException("Unknown equipment: ${unknown.sorted().joinToString()}")
    return normalized
}

class ObserveTrainingLocationsUseCase(private val repository: TrainingLocationRepository) {
    operator fun invoke(): Flow<List<TrainingLocation>> = repository.observeLocations()
}

class CreateTrainingLocationUseCase(private val repository: TrainingLocationRepository) {
    suspend operator fun invoke(name: String, type: LocationType, equipment: Set<String>) =
        repository.create(normalizedLocationName(name), type, validatedEquipment(equipment))
}

class UpdateTrainingLocationUseCase(private val repository: TrainingLocationRepository) {
    suspend operator fun invoke(location: TrainingLocation) = repository.update(
        location.copy(
            name = normalizedLocationName(location.name),
            equipmentSlugs = validatedEquipment(location.equipmentSlugs),
        ),
    )
}

class SelectActiveTrainingLocationUseCase(private val repository: TrainingLocationRepository) {
    suspend operator fun invoke(id: String) = repository.setActive(id)
}

class UpdateLocationEquipmentUseCase(private val repository: TrainingLocationRepository) {
    suspend operator fun invoke(id: String, equipment: Set<String>) =
        repository.replaceEquipment(id, validatedEquipment(equipment))
}

class DeleteTrainingLocationUseCase(private val repository: TrainingLocationRepository) {
    suspend operator fun invoke(id: String) = repository.delete(id)
}

data class CompatibleCatalogState(
    val activeLocation: TrainingLocation?,
    val exercises: List<CatalogExercise>,
    val requiresLocationSelection: Boolean,
)

fun CatalogExercise.isCompatibleWith(location: TrainingLocation): Boolean =
    equipment.isEmpty() || equipment.all { it == EquipmentDefinitions.NONE || it in location.availableEquipment }

class ObserveCompatibleCatalogUseCase(
    private val catalogRepository: CatalogRepository,
    private val locationRepository: TrainingLocationRepository,
) {
    operator fun invoke(
        filter: CatalogFilter = CatalogFilter(),
        showAll: Boolean = false,
    ): Flow<CompatibleCatalogState> = combine(
        catalogRepository.observeCatalog(filter),
        locationRepository.observeActiveLocation(),
    ) { catalog, location ->
        CompatibleCatalogState(
            activeLocation = location,
            exercises = when {
                showAll -> catalog
                location == null -> emptyList()
                else -> catalog.filter { it.isCompatibleWith(location) }
            },
            requiresLocationSelection = location == null && !showAll,
        )
    }
}

class FindCompatibleAlternativesUseCase {
    operator fun invoke(
        exercise: CatalogExercise,
        candidates: List<CatalogExercise>,
        location: TrainingLocation,
    ): List<CatalogExercise> {
        val primary = exercise.muscles.filter { it.role == MuscleRole.PRIMARY }.map { it.slug }.toSet()
        return candidates.asSequence()
            .filter { it.id != exercise.id && it.isCompatibleWith(location) }
            .filter { candidate ->
                candidate.muscles.any { it.role == MuscleRole.PRIMARY && it.slug in primary }
            }
            .sortedWith(compareByDescending<CatalogExercise> { it.trackingType == exercise.trackingType }.thenBy { it.name }.thenBy { it.id })
            .toList()
    }
}
