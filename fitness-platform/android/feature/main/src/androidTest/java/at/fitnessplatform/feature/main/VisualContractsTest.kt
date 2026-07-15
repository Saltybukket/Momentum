package at.fitnessplatform.feature.main

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import at.fitnessplatform.core.designsystem.MomentumBannerKind
import at.fitnessplatform.core.designsystem.MomentumInlineBanner
import at.fitnessplatform.core.designsystem.MomentumSegmentedControl
import at.fitnessplatform.core.designsystem.MomentumStatusChip
import at.fitnessplatform.core.designsystem.MomentumStatusVariant
import at.fitnessplatform.core.designsystem.MomentumTheme
import at.fitnessplatform.core.model.ConflictResolutionStatus
import at.fitnessplatform.core.model.CustomExercise
import at.fitnessplatform.core.model.ExerciseConflict
import at.fitnessplatform.core.model.ExerciseConflictResolution
import at.fitnessplatform.core.model.ExerciseConflictType
import at.fitnessplatform.core.model.TrackingType
import at.fitnessplatform.core.model.Workout
import at.fitnessplatform.core.model.WorkoutExercise
import at.fitnessplatform.core.model.WorkoutStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisualContractsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun conflictConfirmationPrecedesMutationAndCancelDoesNothing() {
        var resolutions = 0
        rule.setContent {
            MomentumTheme {
                ConflictResolverScreen(conflict(), false, { _, _ -> resolutions++ }, {})
            }
        }

        rule.onNodeWithTag("conflict-keep-local").performClick()
        rule.onNodeWithText("Queue this version for server confirmation?").assertIsDisplayed()
        assertEquals(0, resolutions)
        rule.onNodeWithTag("conflict-confirm-cancel").performClick()
        assertEquals(0, resolutions)
    }

    @Test fun takeServerUsesReplacementCopyAndResolverWaitsForParentSuccess() {
        var resolutions = 0
        rule.setContent {
            MomentumTheme {
                ConflictResolverScreen(conflict(), false, { _, _ -> resolutions++ }, {})
            }
        }

        rule.onNodeWithTag("conflict-take-server").performClick()
        rule.onNodeWithText("Replace the local version with the server version?").assertIsDisplayed()
        rule.onNodeWithTag("conflict-confirm").performClick()

        assertEquals(1, resolutions)
        rule.onNodeWithText("Resolve sync conflict").assertIsDisplayed()
        rule.onNodeWithText("Replace the local version with the server version?").assertIsDisplayed()
    }

    @Test fun mergeForwardsEditedValueAfterConfirmation() {
        var merged: CustomExercise? = null
        rule.setContent {
            MomentumTheme {
                ConflictResolverScreen(conflict(), false, { _, value -> merged = value }, {})
            }
        }

        rule.onNodeWithTag("conflict-merge-name").performTextReplacement("Merged name")
        rule.onNodeWithTag("conflict-merge").performClick()
        rule.onNodeWithTag("conflict-confirm").performClick()

        assertEquals("Merged name", merged?.name)
    }

    @Test fun busyConflictResolverDisablesEveryMutationAction() {
        rule.setContent {
            MomentumTheme { ConflictResolverScreen(conflict(), true, { _, _ -> }, {}) }
        }

        rule.onNodeWithTag("conflict-keep-local").assertIsNotEnabled()
        rule.onNodeWithTag("conflict-take-server").assertIsNotEnabled()
        rule.onNodeWithTag("conflict-merge").assertIsNotEnabled()
    }

    @Test fun bannerActionInvokesExactlyOnce() {
        var calls = 0
        rule.setContent {
            MomentumTheme {
                MomentumInlineBanner(
                    MomentumBannerKind.INFO,
                    "Message",
                    actionLabel = "Resolve",
                    onAction = { calls++ },
                )
            }
        }

        rule.onNodeWithText("Resolve").performClick()
        assertEquals(1, calls)
    }

    @Test fun statusChipIsStateOnlyAndHasNoClickAction() {
        rule.setContent {
            MomentumTheme { MomentumStatusChip(MomentumStatusVariant.COMPLETED, "Completed") }
        }

        rule.onNodeWithText("Completed")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
    }

    @Test fun segmentedControlExposesExactlyOneSelectedOption() {
        var selected by mutableStateOf(0)
        rule.setContent {
            MomentumTheme {
                MomentumSegmentedControl(listOf("One", "Two", "Three"), selected, { selected = it })
            }
        }

        rule.onNodeWithText("One").assertIsSelected()
        rule.onNodeWithText("Two").assertIsNotSelected().performClick().assertIsSelected()
        rule.onNodeWithText("One").assertIsNotSelected()
        rule.onNodeWithText("Three").assertIsNotSelected()
    }

    @Test fun trackingTypesRemainReachableAtCompactWidthAndLargeFont() {
        var selected by mutableStateOf(TrackingType.REPS)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MomentumTheme {
                    TrackingTypeSelector(selected, false, { selected = it }, Modifier.width(360.dp))
                }
            }
        }

        TrackingType.entries.forEach { type ->
            rule.onNodeWithTag("tracking-type-${type.name}").assertIsDisplayed().assertIsEnabled()
        }
        rule.onNodeWithTag("tracking-type-REPS").assertIsSelected()
        rule.onNodeWithTag("tracking-type-DISTANCE_DURATION").performClick().assertIsSelected()
    }

    @Test fun workoutDetailShowsLoadingState() {
        rule.setContent {
            MomentumTheme {
                WorkoutDetailScreen("loading", PlatformUiState(isLoading = true), {}, {}, {})
            }
        }

        rule.onNodeWithText("Loading workout").assertIsDisplayed()
    }

    @Test fun workoutDetailShowsNotFoundState() {
        rule.setContent {
            MomentumTheme {
                WorkoutDetailScreen("missing", PlatformUiState(isLoading = false), {}, {}, {})
            }
        }

        rule.onNodeWithText("Workout not found").assertIsDisplayed()
    }

    @Test fun missingExerciseHasExplicitSemanticsAndDisablesRepeat() {
        val workout = workout(WorkoutStatus.COMPLETED)
        rule.setContent {
            MomentumTheme {
                WorkoutDetailScreen(
                    workout.id,
                    PlatformUiState(isLoading = false, workouts = listOf(workout)),
                    {},
                    {},
                    {},
                )
            }
        }

        rule.onNodeWithContentDescription("Referenced exercise is unavailable").assertIsDisplayed()
        rule.onNodeWithText("Repeat is unavailable because a referenced exercise is missing.")
            .assertIsNotEnabled()
    }

    @Test fun plannedWorkoutStartIsDisabledWhileBusy() {
        assertWorkoutActionDisabled(WorkoutStatus.PLANNED, "Start")
    }

    @Test fun activeWorkoutCompleteIsDisabledWhileBusy() {
        assertWorkoutActionDisabled(WorkoutStatus.IN_PROGRESS, "Complete")
    }

    @Test fun completedWorkoutRepeatIsDisabledWhileBusy() {
        assertWorkoutActionDisabled(WorkoutStatus.COMPLETED, "Repeat workout", includeExercise = true)
    }

    private fun assertWorkoutActionDisabled(
        status: WorkoutStatus,
        action: String,
        includeExercise: Boolean = false,
    ) {
        val workout = workout(status)
        val exercises = if (includeExercise) listOf(exercise()) else emptyList()
        rule.setContent {
            MomentumTheme {
                WorkoutDetailScreen(
                    workout.id,
                    PlatformUiState(
                        isLoading = false,
                        workouts = listOf(workout),
                        exercises = exercises,
                        operationInProgress = true,
                    ),
                    {},
                    {},
                    {},
                )
            }
        }
        rule.onNodeWithText(action).assertIsNotEnabled()
    }

    private fun exercise() = CustomExercise(
        id = "exercise",
        ownerProfileId = "profile",
        name = "Synthetic exercise",
        primaryMuscleGroup = "Full body",
        requiredEquipment = "None",
        trackingType = TrackingType.REPS,
        createdAtEpochMs = 1,
        updatedAtEpochMs = 1,
    )

    private fun workout(status: WorkoutStatus) = Workout(
        id = "workout",
        ownerProfileId = "profile",
        title = "Synthetic workout",
        status = status,
        exercises = listOf(WorkoutExercise("link", "workout", "exercise", 0)),
        createdAtEpochMs = 1,
        updatedAtEpochMs = 1,
    )

    private fun conflict(): ExerciseConflict {
        val local = exercise().copy(name = "Local exercise")
        return ExerciseConflict(
            id = "conflict",
            exerciseId = local.id,
            type = ExerciseConflictType.BOTH_MODIFIED,
            localRevision = 1,
            remoteRevision = 2,
            localSnapshot = local,
            remoteSnapshot = local.copy(name = "Server exercise"),
            detectedAtEpochMs = 2,
            resolutionStatus = ConflictResolutionStatus.OPEN,
        )
    }
}
