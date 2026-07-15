package at.fitnessplatform.feature.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import at.fitnessplatform.core.designsystem.MomentumCard
import at.fitnessplatform.core.designsystem.MomentumEmptyState
import at.fitnessplatform.core.designsystem.MomentumHeroCard
import at.fitnessplatform.core.designsystem.MomentumInlineBanner
import at.fitnessplatform.core.designsystem.MomentumBannerKind
import at.fitnessplatform.core.designsystem.MomentumActionTile
import at.fitnessplatform.core.designsystem.MomentumListCard
import at.fitnessplatform.core.designsystem.MomentumScreen
import at.fitnessplatform.core.designsystem.MomentumSectionHeader
import at.fitnessplatform.core.designsystem.MomentumSpacing
import at.fitnessplatform.core.designsystem.MomentumStatusChip
import at.fitnessplatform.core.designsystem.MomentumStatusVariant
import at.fitnessplatform.core.model.WorkoutStatus
import at.fitnessplatform.domain.GuestCredentialStatus

internal fun homeHeroActionLabel(
    hasActiveWorkout: Boolean,
    hasPlannedWorkout: Boolean,
): Int = when {
    hasActiveWorkout -> R.string.home_continue_workout
    hasPlannedWorkout -> R.string.home_view_workout
    else -> R.string.home_start_workout
}

@Composable
@Suppress("CyclomaticComplexMethod")
internal fun HomeScreen(
    state: PlatformUiState,
    onCatalog: () -> Unit,
    onPrivacy: () -> Unit,
    onWorkouts: () -> Unit,
    onConflicts: () -> Unit,
    onLocations: () -> Unit,
    onPlans: () -> Unit,
    onOpen: (String) -> Unit = {},
) = MomentumScreen(Modifier.fillMaxSize()) {
    item {
        Text(
            stringResource(R.string.home_greeting, state.profile?.displayName.orEmpty()),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.home_today_context),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    item {
        val active = state.activeWorkout
        val planned = state.nextPlannedWorkout
        val heroWorkout = active ?: planned
        MomentumHeroCard(Modifier.fillMaxWidth()) {
            Text(
                stringResource(
                    when {
                        active != null -> R.string.home_active_workout
                        planned != null -> R.string.workout_planned
                        else -> R.string.home_workout_title
                    },
                    heroWorkout?.title ?: "",
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                when {
                    active != null -> active.title
                    planned != null -> planned.title
                    else -> stringResource(R.string.home_no_active_workout)
                },
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Button(
                onClick = {
                    if (heroWorkout != null) onOpen(heroWorkout.id) else onWorkouts()
                },
            ) {
                Text(
                    stringResource(homeHeroActionLabel(active != null, planned != null)),
                )
            }
        }
    }
    item { MomentumSectionHeader(stringResource(R.string.home_quick_actions)) }
    item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MomentumSpacing.md)) {
            MomentumActionTile(
                painterResource(R.drawable.ic_workouts),
                stringResource(R.string.workout_create),
                stringResource(R.string.home_start_workout),
                Modifier.weight(1f),
                onClick = onWorkouts,
            )
            MomentumActionTile(
                painterResource(R.drawable.ic_plans),
                stringResource(R.string.plans_title),
                stringResource(R.string.plans_create),
                Modifier.weight(1f),
                onClick = onPlans,
            )
        }
    }
    item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MomentumSpacing.md)) {
            MomentumActionTile(
                painterResource(R.drawable.ic_exercises),
                stringResource(R.string.nav_exercises),
                stringResource(R.string.home_open_catalog),
                Modifier.weight(1f),
                onClick = onCatalog,
            )
            MomentumActionTile(
                painterResource(R.drawable.ic_locations),
                stringResource(R.string.locations_title),
                stringResource(R.string.locations_switch),
                Modifier.weight(1f),
                onClick = onLocations,
            )
        }
    }
    item { ActiveLocationCard(onManage = onLocations) }
    item { MomentumSectionHeader(stringResource(R.string.home_recent_title)) }
    if (state.recentWorkouts.isEmpty()) {
        item {
            MomentumEmptyState(
                stringResource(R.string.home_recent_empty_title),
                stringResource(R.string.home_recent_empty),
                stringResource(R.string.home_start_workout),
                onWorkouts,
            )
        }
    } else {
        items(state.recentWorkouts, key = { it.id }) { workout ->
            MomentumListCard(Modifier.fillMaxWidth(), onClick = { onOpen(workout.id) }) {
                Text(workout.title, style = MaterialTheme.typography.titleMedium)
                Text(workoutStatusLabel(workout.status))
            }
        }
    }
    item {
        MomentumCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.home_library_title), style = MaterialTheme.typography.titleLarge)
            Text(pluralStringResource(R.plurals.home_custom_count, state.exercises.size, state.exercises.size))
            OutlinedButton(onClick = onCatalog) { Text(stringResource(R.string.home_open_catalog)) }
        }
    }
    if (state.conflicts.isNotEmpty()) {
        item {
            MomentumInlineBanner(
                MomentumBannerKind.CONFLICT,
                pluralStringResource(R.plurals.home_conflicts, state.conflicts.size, state.conflicts.size),
                actionLabel = stringResource(R.string.resolve),
                onAction = onConflicts,
            )
        }
    }
    item {
        val blocked = state.credentialStatus != GuestCredentialStatus.READY
        MomentumCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.home_sync_title), style = MaterialTheme.typography.titleLarge)
            val syncDesc = when {
                blocked -> R.string.home_sync_blocked
                state.syncEnabled -> R.string.home_sync_enabled
                else -> R.string.home_sync_disabled
            }
            MomentumInlineBanner(
                if (blocked) MomentumBannerKind.WARNING else if (state.syncEnabled) MomentumBannerKind.INFO else MomentumBannerKind.OFFLINE,
                stringResource(syncDesc),
            )
            Spacer(Modifier.height(MomentumSpacing.sm))
            Text(pluralStringResource(R.plurals.home_sync_pending, state.pendingSyncCount, state.pendingSyncCount))
            Text(stringResource(R.string.home_offline_ready))
            TextButton(onClick = onPrivacy) { Text(stringResource(R.string.home_manage_privacy)) }
        }
    }
}

@Composable
internal fun CreateGuestScreen(modifier: Modifier, busy: Boolean, onCreate: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.guest_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.guest_description))
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            name,
            { name = it },
            label = { Text(stringResource(R.string.display_name)) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onCreate(name) },
            enabled = !busy && name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.guest_create)) }
    }
}
