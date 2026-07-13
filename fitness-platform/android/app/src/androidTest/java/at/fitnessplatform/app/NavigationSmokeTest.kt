package at.fitnessplatform.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun guestCanReachExerciseScreen() {
        rule.waitForIdle()
        if (rule.onAllNodesWithText("Create local profile").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithText("Display name").performTextInput("Guest")
            rule.onNodeWithText("Create local profile").performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithText("Exercises").fetchSemanticsNodes().isNotEmpty() }
        }
        rule.onNodeWithText("Exercises").performClick()
        rule.onNodeWithText("Custom exercises").assertIsDisplayed()
    }

    @Test fun momentumShellExposesFourRootDestinations() {
        rule.waitForIdle()
        if (rule.onAllNodesWithText("Create local profile").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithText("Display name").performTextInput("Guest")
            rule.onNodeWithText("Create local profile").performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithText("Home").fetchSemanticsNodes().isNotEmpty() }
        }
        listOf("Home", "Workouts", "Exercises", "Profile").forEach { label ->
            rule.onNodeWithText(label).assertIsDisplayed()
        }
        rule.onNodeWithText("Momentum").assertIsDisplayed()
    }
}
