@file:Suppress("UnusedPrivateMember")

package at.fitnessplatform.feature.main

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Modifier
import at.fitnessplatform.core.designsystem.MomentumCard
import at.fitnessplatform.core.designsystem.MomentumEmptyState
import at.fitnessplatform.core.designsystem.MomentumScreen
import at.fitnessplatform.core.designsystem.MomentumSectionHeader
import at.fitnessplatform.core.designsystem.MomentumTheme

@Composable
private fun SyntheticScreenPreview(title: String, status: String, empty: Boolean = false) {
    MomentumTheme {
        MomentumScreen {
            item { MomentumSectionHeader(title, "Synthetic preview data — never shown as user metrics") }
            if (empty) {
                item { MomentumEmptyState("Nothing here yet", status) }
            } else {
                item {
                    MomentumCard(Modifier.fillMaxWidth(), emphasized = true) {
                        Text(status, style = MaterialTheme.typography.titleLarge)
                        Text("Offline-ready content")
                    }
                }
            }
        }
    }
}

@Preview(name = "Home loaded", widthDp = 360, heightDp = 800)
@Preview(name = "Home expanded", widthDp = 840, heightDp = 960)
@Preview(name = "Home offline", widthDp = 411, heightDp = 891)
@Preview(name = "Home action needed", widthDp = 600, heightDp = 960)
@Preview(name = "Home large text", widthDp = 411, heightDp = 891, fontScale = 2f)
@Composable private fun HomePreview() = SyntheticScreenPreview("Today", "Workout ready")

@Preview(name = "Workouts empty", widthDp = 411, heightDp = 891)
@Preview(name = "Workouts active planned history", widthDp = 840, heightDp = 600)
@Composable private fun WorkoutsPreview() = SyntheticScreenPreview("Workouts", "Create an honest empty workout", true)

@Preview(name = "Exercises conflict", widthDp = 411, heightDp = 891)
@Preview(name = "Exercises list", widthDp = 600, heightDp = 960)
@Preview(name = "Exercises empty", widthDp = 360, heightDp = 800)
@Composable private fun ExercisesPreview() = SyntheticScreenPreview("My exercises", "Conflict requires attention")

@Preview(name = "Catalog location required", widthDp = 411, heightDp = 891)
@Preview(name = "Catalog compatible", widthDp = 600, heightDp = 960)
@Preview(name = "Catalog missing equipment", widthDp = 840, heightDp = 600)
@Composable private fun CatalogPreview() = SyntheticScreenPreview("Public catalog", "Choose a training location")

@Preview(name = "Locations saving", widthDp = 600, heightDp = 960)
@Preview(name = "Locations list", widthDp = 411, heightDp = 891)
@Preview(name = "Locations empty", widthDp = 360, heightDp = 800)
@Preview(name = "Locations error", widthDp = 411, heightDp = 891)
@Composable private fun LocationsPreview() = SyntheticScreenPreview("Training locations", "Saving locally…")

@Preview(name = "Equipment editor", widthDp = 411, heightDp = 891)
@Composable private fun EquipmentPreview() = SyntheticScreenPreview("Available equipment", "2 selected items")

@Preview(name = "Profile dark", widthDp = 411, heightDp = 891, uiMode = 0x20)
@Preview(name = "Profile and privacy expanded", widthDp = 840, heightDp = 600)
@Composable private fun ProfilePreview() = SyntheticScreenPreview("Profile", "Private sync is off")
