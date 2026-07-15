package at.fitnessplatform.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import at.fitnessplatform.feature.main.R as MainFeatureR
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun ensureLocalProfile() {
        val createProfile = rule.activity.getString(MainFeatureR.string.guest_create)
        val displayName = rule.activity.getString(MainFeatureR.string.display_name)
        rule.waitForIdle()
        if (rule.onAllNodesWithText(createProfile).fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithText(displayName).performTextInput("Guest")
            rule.onNodeWithText(createProfile).performClick()
            val home = rule.activity.getString(MainFeatureR.string.nav_home)
            rule.waitUntil(5_000) { rule.onAllNodesWithText(home).fetchSemanticsNodes().isNotEmpty() }
        }
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText(rule.activity.getString(MainFeatureR.string.nav_home))
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("root-nav-home").performClick()
        rule.waitForIdle()
    }

    @Test fun guestCanReachExerciseScreen() {
        ensureLocalProfile()
        rule.onNodeWithTag("root-nav-exercises").performClick()
        rule.onNodeWithTag("screen-exercises").assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(MainFeatureR.string.custom_exercises_title)).assertIsDisplayed()
    }

    @Test fun momentumShellExposesAccessibleIconsForFourRootDestinations() {
        ensureLocalProfile()
        mapOf(
            "home" to MainFeatureR.string.nav_home,
            "workouts" to MainFeatureR.string.nav_workouts,
            "exercises" to MainFeatureR.string.nav_exercises,
            "profile" to MainFeatureR.string.nav_profile,
        ).forEach { (route, labelResource) ->
            val label = rule.activity.getString(labelResource)
            rule.onNodeWithTag("root-nav-$route")
                .assertIsDisplayed()
            rule.onNodeWithContentDescription(label, useUnmergedTree = true).assertIsDisplayed()
        }
        rule.onNodeWithTag("root-nav-home").assertIsSelected()
        rule.onNodeWithText(rule.activity.getString(MainFeatureR.string.app_name)).assertIsDisplayed()
    }

    @Test fun systemBackFromPrivacyRestoresSelectedProfileRoot() {
        ensureLocalProfile()
        rule.onNodeWithTag("root-nav-profile").performClick()
        rule.onNodeWithTag("profile-open-privacy").performClick()
        rule.onNodeWithTag("screen-privacy").assertIsDisplayed()
        rule.onNodeWithTag("root-nav-profile").assertIsSelected()

        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithTag("screen-profile").assertIsDisplayed()
        rule.onNodeWithTag("root-nav-profile").assertIsSelected()
    }

    @Test fun rootNavigationReturnsFromWorkoutsToHome() {
        ensureLocalProfile()
        rule.onNodeWithTag("root-nav-workouts").performClick().assertIsSelected()
        rule.onNodeWithTag("screen-workouts").assertIsDisplayed()
        rule.onNodeWithTag("screen-home").assertDoesNotExist()
        rule.onNodeWithTag("root-nav-home").performClick().assertIsSelected()
        rule.onNodeWithTag("screen-home").assertIsDisplayed()
        rule.onNodeWithTag("screen-workouts").assertDoesNotExist()
        rule.onNodeWithText(rule.activity.getString(MainFeatureR.string.home_quick_actions)).assertIsDisplayed()
    }

    @Test fun plansUpReturnsToSelectedWorkoutsRoot() {
        ensureLocalProfile()
        rule.onNodeWithTag("root-nav-workouts").performClick()
        rule.onNodeWithTag("workouts-open-plans").performClick()
        rule.onNodeWithTag("screen-plans").assertIsDisplayed()
        rule.onNodeWithTag("screen-workouts").assertDoesNotExist()
        rule.onNodeWithTag("root-nav-workouts").assertIsSelected()
        rule.onNodeWithTag("navigate-up").performClick()
        rule.onNodeWithTag("screen-workouts").assertIsDisplayed()
        rule.onNodeWithTag("screen-plans").assertDoesNotExist()
        rule.onNodeWithTag("root-nav-workouts").assertIsSelected()
    }

    @Test fun calendarUpReturnsToSelectedWorkoutsRoot() {
        ensureLocalProfile()
        rule.onNodeWithTag("root-nav-workouts").performClick()
        rule.onNodeWithTag("workouts-open-calendar").performClick()
        rule.onNodeWithTag("screen-calendar").assertIsDisplayed()
        rule.onNodeWithTag("screen-workouts").assertDoesNotExist()
        rule.onNodeWithTag("root-nav-workouts").assertIsSelected()
        rule.onNodeWithTag("navigate-up").performClick()
        rule.onNodeWithTag("screen-workouts").assertIsDisplayed()
        rule.onNodeWithTag("screen-calendar").assertDoesNotExist()
        rule.onNodeWithTag("root-nav-workouts").assertIsSelected()
    }

    @Test fun locationsUpReturnsToSelectedProfileRoot() {
        ensureLocalProfile()
        rule.onNodeWithTag("root-nav-profile").performClick()
        rule.onNodeWithTag("profile-open-locations").performClick()
        rule.onNodeWithTag("screen-locations").assertIsDisplayed()
        rule.onNodeWithTag("screen-profile").assertDoesNotExist()
        rule.onNodeWithTag("root-nav-profile").assertIsSelected()
        rule.onNodeWithTag("navigate-up").performClick()
        rule.onNodeWithTag("screen-profile").assertIsDisplayed()
        rule.onNodeWithTag("screen-locations").assertDoesNotExist()
        rule.onNodeWithTag("root-nav-profile").assertIsSelected()
    }

    @Test fun privacyUpReturnsToSelectedProfileRoot() {
        ensureLocalProfile()
        rule.onNodeWithTag("root-nav-profile").performClick()
        rule.onNodeWithTag("profile-open-privacy").performClick()
        rule.onNodeWithTag("screen-privacy").assertIsDisplayed()
        rule.onNodeWithTag("screen-profile").assertDoesNotExist()
        rule.onNodeWithTag("root-nav-profile").assertIsSelected()
        rule.onNodeWithTag("navigate-up").performClick()
        rule.onNodeWithTag("screen-profile").assertIsDisplayed()
        rule.onNodeWithTag("screen-privacy").assertDoesNotExist()
        rule.onNodeWithTag("root-nav-profile").assertIsSelected()
    }

    @Test fun catalogUpReturnsToSelectedExercisesRoot() {
        ensureLocalProfile()
        rule.onNodeWithTag("root-nav-exercises").performClick()
        rule.onNodeWithTag("exercise-section-1").performClick()
        rule.onNodeWithTag("screen-catalog").assertIsDisplayed()
        rule.onNodeWithTag("screen-exercises").assertDoesNotExist()
        rule.onNodeWithTag("root-nav-exercises").assertIsSelected()
        rule.onNodeWithTag("navigate-up").performClick()
        rule.onNodeWithTag("screen-exercises").assertIsDisplayed()
        rule.onNodeWithTag("screen-catalog").assertDoesNotExist()
        rule.onNodeWithTag("root-nav-exercises").assertIsSelected()
    }
}
