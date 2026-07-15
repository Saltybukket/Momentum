package at.fitnessplatform.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview

fun Modifier.momentumContentWidth(maxWidth: Dp = 960.dp): Modifier =
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
fun MomentumFormScreen(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.momentumContentWidth().verticalScroll(rememberScrollState())
                .padding(MomentumSpacing.lg),
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
fun MomentumHeroCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier,
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            Modifier.padding(MomentumSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(MomentumSpacing.sm),
            content = content,
        )
    }
}

@Composable
fun MomentumListCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (onClick != null) {
        ElevatedCard(
            onClick = onClick,
            modifier = modifier,
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = MomentumElevation.card),
        ) {
            Column(
                Modifier.padding(MomentumSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(MomentumSpacing.sm),
                content = content,
            )
        }
    } else {
        MomentumCard(modifier, content = content)
    }
}

@Composable
fun MomentumActionTile(
    icon: Painter,
    label: String,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    ElevatedCard(
        modifier = modifier,
        onClick = onClick,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = MomentumElevation.card),
    ) {
        Column(
            Modifier.padding(MomentumSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MomentumSpacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(MomentumSpacing.xxl),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(label, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

enum class MomentumBannerKind { OFFLINE, CONFLICT, WARNING, INFO }

@Composable
fun MomentumInlineBanner(
    kind: MomentumBannerKind,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val (bg, fg) = when (kind) {
        MomentumBannerKind.CONFLICT -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        MomentumBannerKind.WARNING -> MomentumThemeValues.semanticColors.warning to MaterialTheme.colorScheme.onSecondary
        MomentumBannerKind.INFO -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        MomentumBannerKind.OFFLINE -> MomentumThemeValues.semanticColors.offline to Color.White
    }
    Surface(modifier.fillMaxWidth(), color = bg, shape = MaterialTheme.shapes.small) {
        Row(
            Modifier.padding(MomentumSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, Modifier.weight(1f), color = fg, style = MaterialTheme.typography.bodyMedium)
            if (actionLabel != null && onAction != null) {
                Text(
                    actionLabel,
                    color = fg,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = MomentumSpacing.sm).semantics { },
                )
            }
        }
    }
}

enum class MomentumStatusVariant { PLANNED, ACTIVE, PAUSED, COMPLETED, CANCELLED, CONFLICT }

@Composable
fun MomentumStatusChip(variant: MomentumStatusVariant, text: String, modifier: Modifier = Modifier) {
    val fg = when (variant) {
        MomentumStatusVariant.ACTIVE -> MomentumThemeValues.semanticColors.activeWorkout
        MomentumStatusVariant.COMPLETED -> MomentumThemeValues.semanticColors.success
        MomentumStatusVariant.CONFLICT -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    AssistChip(
        onClick = {},
        label = { Text(text, color = fg, style = MaterialTheme.typography.labelMedium) },
        modifier = modifier.semantics {
            stateDescription = text
        },
        colors = AssistChipDefaults.assistChipColors(
            labelColor = fg,
            leadingIconContentColor = fg,
        ),
    )
}

@Composable
fun MomentumSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MomentumSpacing.sm)) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            FilterChip(
                selected = selected,
                onClick = { onSelect(index) },
                label = { Text(label) },
                modifier = Modifier
                    .weight(1f)
                    .semantics { if (selected) this.selected = true },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
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

@Preview(name = "Momentum components light", widthDp = 411, heightDp = 800, showBackground = true)
@Preview(name = "Momentum components dark", widthDp = 600, heightDp = 800, uiMode = 0x20)
@Composable
@Suppress("UnusedPrivateMember")
private fun MomentumComponentsPreview() {
    MomentumTheme {
        MomentumScreen {
            item { MomentumSectionHeader("Today", "Synthetic preview data") }
            item {
                MomentumHeroCard(Modifier.fillMaxWidth()) {
                    Text("Active: Full Body Strength", style = MaterialTheme.typography.titleLarge)
                    MomentumSkeletonLine(Modifier.fillMaxWidth(0.7f))
                }
            }
            item {
                MomentumSegmentedControl(
                    listOf("Today", "History"),
                    0,
                    onSelect = {},
                )
            }
            item {
                MomentumListCard(Modifier.fillMaxWidth(), onClick = {}) {
                    Text("Quick Upper Body", style = MaterialTheme.typography.titleMedium)
                    MomentumStatusChip(MomentumStatusVariant.PLANNED, "Planned")
                }
            }
            item {
                MomentumInlineBanner(
                    MomentumBannerKind.OFFLINE,
                    "Private-data sync is off. Your data stays local.",
                )
            }
            item { MomentumEmptyState("No recent workouts", "Your finished sessions will appear here.") }
        }
    }
}
