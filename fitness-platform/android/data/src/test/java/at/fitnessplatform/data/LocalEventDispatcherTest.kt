package at.fitnessplatform.data

import at.fitnessplatform.core.model.WorkoutCompleted
import at.fitnessplatform.domain.DomainEventHandler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalEventDispatcherTest {
    @Test fun `same event id is handled exactly once`() = runTest {
        val dispatcher = LocalEventDispatcher()
        var calls = 0
        dispatcher.register(WorkoutCompleted::class.java, DomainEventHandler { calls++ })
        val event = WorkoutCompleted("event-1", 1, "workout-1")
        dispatcher.publish(event)
        dispatcher.publish(event)
        assertEquals(1, calls)
    }
}
