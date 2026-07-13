package at.fitnessplatform.feature.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootNavigationTest {
    @Test
    fun nestedRoutesResolveToTheirRootFamilies() {
        mapOf(
            "home" to "home",
            "workouts" to "workouts",
            "workout-detail/42" to "workouts",
            "active-workout/42" to "workouts",
            "workout-summary/42" to "workouts",
            "exercises" to "exercises",
            "exercise/42" to "exercises",
            "catalog" to "exercises",
            "catalog/42" to "exercises",
            "catalog-detail/42" to "exercises",
            "custom-exercise/42" to "exercises",
            "custom-exercise-edit/42" to "exercises",
            "conflicts" to "exercises",
            "conflict/42" to "exercises",
            "profile" to "profile",
            "privacy" to "profile",
            "guest-recovery" to "profile",
            "settings" to "profile",
            "locations" to "profile",
        ).forEach { (route, expectedRoot) ->
            assertEquals(expectedRoot, rootRouteFor(route))
        }
    }

    @Test
    fun unknownOrMissingRouteDoesNotSelectARoot() {
        assertNull(rootRouteFor(null))
        assertNull(rootRouteFor("not-registered"))
    }

    @Test
    fun rootDestinationsNeverShowUpWhileSubpagesDo() {
        listOf("home", "workouts", "exercises", "profile").forEach { route ->
            assertFalse(showsUpNavigation(route))
        }
        listOf("catalog", "catalog/42", "privacy", "locations", "exercise/42").forEach { route ->
            assertTrue(showsUpNavigation(route))
        }
    }
}
