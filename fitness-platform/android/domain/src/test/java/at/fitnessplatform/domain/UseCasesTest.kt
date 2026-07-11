package at.fitnessplatform.domain

import at.fitnessplatform.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UseCasesTest {
    @Test fun `blank exercise names are rejected`() = runTest {
        val useCase = CreateExerciseUseCase(FakeExerciseRepository())
        var thrown = false
        try {
            useCase("   ", "", "Legs", "None", TrackingType.REPS, "")
        } catch (_: ValidationException) {
            thrown = true
        }
        assertEquals(true, thrown)
    }

    @Test fun `guest display name is normalized before storage`() = runTest {
        val repository = FakeProfileRepository()
        val profile = CreateGuestProfileUseCase(repository)("  Tobias  ")
        assertEquals("Tobias", profile.displayName)
    }
}

private class FakeProfileRepository : GuestProfileRepository {
    private val state = MutableStateFlow<GuestProfile?>(null)
    override fun observeProfile(): Flow<GuestProfile?> = state
    override suspend fun getProfile(): GuestProfile? = state.value
    override suspend fun create(displayName: String): GuestProfile = GuestProfile("p", displayName, 1).also { state.value = it }
    override suspend fun update(profile: GuestProfile): GuestProfile = profile.also { state.value = it }
}

private class FakeExerciseRepository : ExerciseRepository {
    private val state = MutableStateFlow<List<CustomExercise>>(emptyList())
    private val conflicts = MutableStateFlow<List<ExerciseConflict>>(emptyList())
    override fun observeExercises(): Flow<List<CustomExercise>> = state
    override fun observeExerciseConflicts(): Flow<List<ExerciseConflict>> = conflicts
    override suspend fun getExercise(id: String): CustomExercise? = state.value.firstOrNull { it.id == id }
    override suspend fun create(name: String, description: String, primaryMuscleGroup: String, requiredEquipment: String, trackingType: TrackingType, notes: String): CustomExercise =
        CustomExercise("e", "p", name, description, primaryMuscleGroup, requiredEquipment, trackingType, notes, 1, 1).also { state.value += it }
    override suspend fun update(exercise: CustomExercise): CustomExercise = exercise
    override suspend fun delete(id: String) = Unit
    override suspend fun resolveConflict(exerciseId: String, resolution: ExerciseConflictResolution, mergedExercise: CustomExercise?) = Unit
}
