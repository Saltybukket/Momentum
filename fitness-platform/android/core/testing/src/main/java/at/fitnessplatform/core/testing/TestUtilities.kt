@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package at.fitnessplatform.core.testing

import at.fitnessplatform.core.model.Clock
import at.fitnessplatform.core.model.CustomExercise
import at.fitnessplatform.core.model.GuestProfile
import at.fitnessplatform.core.model.SyncStatus
import at.fitnessplatform.core.model.TrackingType
import at.fitnessplatform.core.model.UnitSystem
import at.fitnessplatform.core.model.UuidProvider
import at.fitnessplatform.core.model.Workout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class FakeClock(var current: Long = 1_700_000_000_000L) : Clock {
    override fun nowEpochMs(): Long = current
    fun advanceBy(millis: Long) { current += millis }
}

class FakeUuidProvider(private val prefix: String = "00000000-0000-0000-0000-") : UuidProvider {
    private var counter = 1L
    override fun newUuid(): String = prefix + counter++.toString().padStart(12, '0')
}

class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) { Dispatchers.setMain(dispatcher) }
    override fun finished(description: Description) { Dispatchers.resetMain() }
}

object TestData {
    fun profile(id: String = "profile-1") = GuestProfile(
        id = id,
        displayName = "Test Guest",
        createdAtEpochMs = 1_700_000_000_000L,
        unitSystem = UnitSystem.METRIC,
        syncStatus = SyncStatus.PENDING,
    )

    fun exercise(id: String = "exercise-1") = CustomExercise(
        id = id,
        ownerProfileId = "profile-1",
        name = "Demo squat",
        primaryMuscleGroup = "Legs",
        requiredEquipment = "None",
        trackingType = TrackingType.REPS,
        createdAtEpochMs = 1_700_000_000_000L,
        updatedAtEpochMs = 1_700_000_000_000L,
    )

    fun workout(id: String = "workout-1") = Workout(
        id = id,
        ownerProfileId = "profile-1",
        title = "Demo workout",
        createdAtEpochMs = 1_700_000_000_000L,
        updatedAtEpochMs = 1_700_000_000_000L,
    )
}
