package at.fitnessplatform.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview

fun Modifier.momentumContentWidth(maxWidth: androidx.compose.ui.unit.Dp = 960.dp): Modifier =
    fillMaxWidth().widthIn(max = maxWidth)

@Composable
fun MomentumScreen(
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.momentumContentWidth(),
            contentPadding = PaddingValues(MomentumSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MomentumSpacing.md),
            content = content,
        )
    }
}

@Composable
fun MomentumSectionHeader(title: String, supporting: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(MomentumSpacing.xs)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        supporting?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun MomentumCard(
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(
        modifier,
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (emphasized) MomentumElevation.emphasized else MomentumElevation.card,
        ),
    ) {
        Column(
            Modifier.padding(MomentumSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MomentumSpacing.sm),
            content = content,
        )
    }
}

@Composable
fun MomentumEmptyState(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    MomentumCard(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionLabel != null && onAction != null) {
            OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun MomentumSkeletonLine(modifier: Modifier = Modifier) {
    Box(
        modifier.height(18.dp).background(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.shapes.small,
        ),
    )
}

@Preview(name = "Momentum components light", widthDp = 411, heightDp = 640, showBackground = true)
@Preview(name = "Momentum components dark", widthDp = 600, heightDp = 640, uiMode = 0x20)
@Composable
@Suppress("UnusedPrivateMember")
private fun MomentumComponentsPreview() {
    MomentumTheme {
        MomentumScreen {
            item { MomentumSectionHeader("Today", "Synthetic preview data") }
            item {
                MomentumCard(Modifier.fillMaxWidth(), emphasized = true) {
                    Text("Strength session", style = MaterialTheme.typography.titleLarge)
                    MomentumSkeletonLine(Modifier.fillMaxWidth(0.7f))
                }
            }
            item { MomentumEmptyState("No recent workouts", "Your finished sessions will appear here.") }
        }
    }
}
