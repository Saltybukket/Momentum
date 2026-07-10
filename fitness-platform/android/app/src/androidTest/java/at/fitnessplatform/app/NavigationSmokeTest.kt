package at.fitnessplatform.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun guestCanReachExerciseScreen() {
        rule.waitForIdle()
        if (rule.onAllNodesWithText("Create guest profile").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithText("Create guest profile").performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithText("Custom exercises").fetchSemanticsNodes().isNotEmpty() }
        }
        rule.onNodeWithText("Custom exercises").performClick()
        rule.onNodeWithText("Custom exercises").assertIsDisplayed()
    }
}
