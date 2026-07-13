@file:Suppress("UnusedPrivateMember")

package at.fitnessplatform.feature.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import at.fitnessplatform.core.designsystem.MomentumTheme
import at.fitnessplatform.core.model.CatalogExercise
import at.fitnessplatform.core.model.CatalogMuscle
import at.fitnessplatform.core.model.CatalogStatus
import at.fitnessplatform.core.model.Equipment
import at.fitnessplatform.core.model.GuestProfile
import at.fitnessplatform.core.model.Muscle
import at.fitnessplatform.core.model.MuscleRole
import at.fitnessplatform.core.model.TrackingType
import at.fitnessplatform.core.model.LocationType

private val previewProfile = GuestProfile("preview-profile", "Alex", 0L)
private val previewCatalogExercise = CatalogExercise(
    id = "preview-push-up",
    externalId = "momentum-demo-push-up",
    source = "Momentum demo catalog",
    provenance = "Self-authored synthetic preview content",
    licenseName = "CC0-1.0",
    licenseUrl = "https://creativecommons.org/publicdomain/zero/1.0/",
    version = "1",
    status = CatalogStatus.PUBLISHED,
    reviewed = true,
    name = "Push-up",
    description = "A controlled bodyweight press.",
    trackingType = TrackingType.REPS,
    muscles = listOf(CatalogMuscle("chest", MuscleRole.PRIMARY)),
    equipment = listOf("open_floor"),
)

private val previewPlatformState = PlatformUiState(isLoading = false, profile = previewProfile)

@Preview(name = "Home compact", widthDp = 360, heightDp = 800)
@Preview(name = "Home expanded", widthDp = 840, heightDp = 960)
@Preview(name = "Home dark", widthDp = 411, heightDp = 891, uiMode = 0x20)
@Preview(name = "Home 200 percent", widthDp = 411, heightDp = 891, fontScale = 2f)
@Composable
private fun HomePreview() = MomentumTheme {
    HomeScreen(previewPlatformState, {}, {}, {}, {}, {}, {})
}

@Preview(name = "Workouts empty", widthDp = 411, heightDp = 891)
@Preview(name = "Workouts expanded", widthDp = 840, heightDp = 600)
@Composable
private fun WorkoutsPreview() = MomentumTheme {
    WorkoutScreen(previewPlatformState, { _, _ -> }, {}, {}, {})
}

@Preview(name = "Exercises empty", widthDp = 360, heightDp = 800)
@Preview(name = "Exercises dark", widthDp = 411, heightDp = 891, uiMode = 0x20)
@Composable
private fun ExercisesPreview() = MomentumTheme {
    ExerciseListScreen(emptyList(), emptyList(), {}, {}, {}, {}, {}, {})
}

@Preview(name = "Catalog loaded", widthDp = 411, heightDp = 891)
@Preview(name = "Catalog expanded", widthDp = 840, heightDp = 600)
@Preview(name = "Catalog 200 percent", widthDp = 411, heightDp = 891, fontScale = 2f)
@Composable
private fun CatalogPreview() = MomentumTheme {
    CatalogScreen(
        state = CatalogUiState(
            loading = false,
            exercises = listOf(previewCatalogExercise),
            muscles = listOf(Muscle("chest", "Chest")),
            equipment = listOf(Equipment("open_floor", "Open floor")),
        ),
        onOpen = {},
        onRefresh = {},
        onShowAll = {},
        onQuery = {},
        onMuscle = {},
        onEquipment = {},
    )
}

@Preview(name = "Catalog detail", widthDp = 411, heightDp = 891)
@Preview(name = "Catalog detail 200 percent", widthDp = 411, heightDp = 891, fontScale = 2f)
@Composable
private fun CatalogDetailPreview() = MomentumTheme {
    CatalogDetail(
        CatalogDetailState.Loaded(
            previewCatalogExercise,
            CatalogCompatibility.COMPATIBLE,
            emptyList(),
            mapOf("chest" to "Chest"),
            mapOf("open_floor" to "Open floor"),
            emptyList(),
        ),
        {},
        {},
    )
}

@Preview(name = "Profile compact", widthDp = 411, heightDp = 891)
@Preview(name = "Profile dark expanded", widthDp = 840, heightDp = 600, uiMode = 0x20)
@Composable
private fun ProfilePreview() = MomentumTheme {
    ProfileScreen("Alex", false, {}, {}, {})
}

@Preview(name = "Guest creation 200 percent", widthDp = 411, heightDp = 891, fontScale = 2f)
@Composable
private fun GuestPreview() = MomentumTheme {
    CreateGuestScreen(Modifier, false, {})
}

@Preview(name = "Privacy local-only", widthDp = 411, heightDp = 891)
@Preview(name = "Privacy recovery 200 percent", widthDp = 411, heightDp = 891, fontScale = 2f)
@Composable
private fun PrivacyPreview() = MomentumTheme {
    PrivacyContent(PrivacyUiState(), {}, {})
}

@Preview(name = "Location editor saving", widthDp = 411, heightDp = 891)
@Composable
private fun LocationEditorPreview() = MomentumTheme {
    LocationEditorDialog(
        title = "Add training location",
        initialName = "Home",
        initialType = LocationType.HOME,
        showPresets = true,
        saving = true,
        error = null,
        onDismiss = {},
        onSave = { _, _, _ -> },
    )
}
