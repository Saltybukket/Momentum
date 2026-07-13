package at.fitnessplatform.feature.main

import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumUiContractTest {
    @Test
    fun `new workout never selects a private exercise implicitly`() {
        assertTrue(newWorkoutExerciseIds().isEmpty())
    }
}
