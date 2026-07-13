@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package at.fitnessplatform.feature.main

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import at.fitnessplatform.core.model.TrackingType
import at.fitnessplatform.core.model.CustomExercise
import at.fitnessplatform.core.model.ExerciseConflict
import at.fitnessplatform.core.model.ExerciseConflictType
import at.fitnessplatform.core.model.ExerciseConflictResolution
import at.fitnessplatform.core.model.WorkoutStatus
import at.fitnessplatform.domain.GuestCredentialStatus

private object Routes {
    const val HOME = "home"
    const val PROFILE = "profile"
    const val EXERCISES = "exercises"
    const val EXERCISE = "exercise/{exerciseId}"
    const val CONFLICTS = "conflicts"
    const val CONFLICT = "conflict/{exerciseId}"
    const val WORKOUTS = "workouts"
    const val CATALOG = "catalog"
    const val CATALOG_EXERCISE = "catalog/{catalogId}"
    const val PRIVACY = "privacy"
}

private data class RootDestination(
    val route: String,
    val label: Int,
)

private val rootDestinations = listOf(
    RootDestination(Routes.HOME, R.string.nav_home),
    RootDestination(Routes.WORKOUTS, R.string.nav_workouts),
    RootDestination(Routes.EXERCISES, R.string.nav_exercises),
    RootDestination(Routes.PROFILE, R.string.nav_profile),
)

internal fun usesNavigationRail(width: Dp): Boolean = width >= 840.dp

private val MomentumLightColors = lightColorScheme(
    primary = Color(0xFF006C4C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF89F8C7),
    onPrimaryContainer = Color(0xFF002116),
    secondary = Color(0xFF4D6358),
    tertiary = Color(0xFF3D6374),
    surface = Color(0xFFF7FBF7),
)

private val MomentumDarkColors = darkColorScheme(
    primary = Color(0xFF6CDBAC),
    onPrimary = Color(0xFF003827),
    primaryContainer = Color(0xFF005139),
    onPrimaryContainer = Color(0xFF89F8C7),
    secondary = Color(0xFFB4CCBE),
    tertiary = Color(0xFFA4CDDF),
)

@Composable
private fun MomentumTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) MomentumDarkColors else MomentumLightColors,
        typography = Typography(
            headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        ),
        shapes = Shapes(
            small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}

@Composable
fun FitnessPlatformRoot(viewModel: PlatformViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val profile = state.profile
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val operationError = stringResource(R.string.operation_failed)
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(operationError)
            viewModel.clearError()
        }
    }
    MomentumTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            if (state.isLoading) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (profile == null) {
                CreateGuestScreen(
                    modifier = Modifier.padding(padding),
                    busy = state.operationInProgress,
                    onCreate = viewModel::createGuest,
                )
            } else {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route
                BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
                    val rail = usesNavigationRail(maxWidth)
                    AdaptiveRootLayout(
                        useRail = rail,
                        navigation = {
                            if (rail) {
                            RootNavigationRail(currentRoute) { destination ->
                                navController.navigateRoot(destination.route)
                            }
                            } else {
                                RootNavigationBar(currentRoute) { destination ->
                                    navController.navigateRoot(destination.route)
                                }
                            }
                        },
                    ) {
                        NavHost(
                            navController,
                            startDestination = Routes.HOME,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                    composable(Routes.HOME) {
                        HomeScreen(
                            state = state,
                            onCatalog = { navController.navigate(Routes.CATALOG) },
                            onPrivacy = { navController.navigate(Routes.PRIVACY) },
                            onWorkouts = { navController.navigate(Routes.WORKOUTS) },
                            onConflicts = { navController.navigate(Routes.CONFLICTS) },
                        )
                    }
                    composable(Routes.PROFILE) {
                        ProfileScreen(profile.displayName, state.operationInProgress, viewModel::renameGuest) { navController.popBackStack() }
                    }
                    composable(Routes.EXERCISES) {
                        ExerciseListScreen(
                            state.exercises,
                            state.conflicts,
                            onNew = { navController.navigate("exercise/new") },
                            onEdit = { navController.navigate("exercise/$it") },
                            onDelete = viewModel::deleteExercise,
                            onResolve = { navController.navigate("conflict/$it") },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(
                        Routes.EXERCISE,
                        arguments = listOf(navArgument("exerciseId") { type = NavType.StringType }),
                    ) { entry ->
                        val id = entry.arguments?.getString("exerciseId")?.takeUnless { it == "new" }
                        val exercise = state.exercises.firstOrNull { it.id == id }
                        ExerciseEditorScreen(exercise, state.operationInProgress, onResolveConflict = { navController.navigate("conflict/$it") }) { name, description, muscle, equipment, type, notes ->
                            viewModel.saveExercise(id, name, description, muscle, equipment, type, notes) { navController.popBackStack() }
                        }
                    }
                    composable(Routes.CONFLICTS) {
                        ConflictListScreen(state.conflicts, onOpen = { navController.navigate("conflict/$it") }, onBack = { navController.popBackStack() })
                    }
                    composable(
                        Routes.CONFLICT,
                        arguments = listOf(navArgument("exerciseId") { type = NavType.StringType }),
                    ) { entry ->
                        val conflict = state.conflicts.firstOrNull { it.exerciseId == entry.arguments?.getString("exerciseId") }
                        if (conflict == null) {
                            LaunchedEffect(Unit) { navController.popBackStack() }
                        } else {
                            ConflictResolverScreen(
                                conflict = conflict,
                                busy = state.operationInProgress,
                                onResolve = { resolution, merged ->
                                    viewModel.resolveConflict(conflict.exerciseId, resolution, merged)
                                    navController.popBackStack()
                                },
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                    composable(Routes.WORKOUTS) {
                        WorkoutScreen(
                            state = state,
                            onCreate = viewModel::createWorkout,
                            onStart = viewModel::startWorkout,
                            onComplete = viewModel::completeWorkout,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(Routes.CATALOG) {
                        CatalogRoute(
                            onOpen = { navController.navigate("catalog/$it") },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(Routes.PRIVACY) {
                        PrivacyScreen(onBack = { navController.popBackStack() })
                    }
                    composable(
                        Routes.CATALOG_EXERCISE,
                        arguments = listOf(navArgument("catalogId") { type = NavType.StringType }),
                    ) { entry ->
                        CatalogDetailRoute(
                            id = entry.arguments?.getString("catalogId").orEmpty(),
                            onBack = { navController.popBackStack() },
                        )
                    }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdaptiveRootLayout(
    useRail: Boolean,
    navigation: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    if (useRail) {
        Row(Modifier.fillMaxSize()) {
            navigation()
            Box(Modifier.weight(1f)) { content() }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) { content() }
            navigation()
        }
    }
}

private fun androidx.navigation.NavHostController.navigateRoot(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun RootNavigationBar(currentRoute: String?, onSelect: (RootDestination) -> Unit) {
    NavigationBar {
        rootDestinations.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = { onSelect(destination) },
                icon = { Box(Modifier.size(1.dp)) },
                label = { Text(stringResource(destination.label)) },
                alwaysShowLabel = true,
            )
        }
    }
}

@Composable
private fun RootNavigationRail(currentRoute: String?, onSelect: (RootDestination) -> Unit) {
    NavigationRail {
        Spacer(Modifier.height(12.dp))
        rootDestinations.forEach { destination ->
            NavigationRailItem(
                selected = currentRoute == destination.route,
                onClick = { onSelect(destination) },
                icon = { Box(Modifier.size(1.dp)) },
                label = { Text(stringResource(destination.label)) },
            )
        }
    }
}

@Composable
private fun CreateGuestScreen(modifier: Modifier, busy: Boolean, onCreate: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
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

@Composable
private fun HomeScreen(
    state: PlatformUiState,
    onCatalog: () -> Unit,
    onPrivacy: () -> Unit,
    onWorkouts: () -> Unit,
    onConflicts: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                stringResource(R.string.home_greeting, state.profile?.displayName.orEmpty()),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.home_local_summary, state.exercises.size, state.workouts.size))
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.home_workout_title), style = MaterialTheme.typography.titleLarge)
                    val active = state.activeWorkout
                    Text(
                        if (active == null) {
                            stringResource(R.string.home_no_active_workout)
                        } else {
                            stringResource(R.string.home_active_workout, active.title)
                        },
                    )
                    Button(onClick = onWorkouts) {
                        Text(
                            stringResource(
                                if (active == null) R.string.home_start_workout else R.string.home_continue_workout,
                            ),
                        )
                    }
                }
            }
        }
        item {
            Text(stringResource(R.string.home_recent_title), style = MaterialTheme.typography.titleLarge)
        }
        if (state.recentWorkouts.isEmpty()) {
            item { Text(stringResource(R.string.home_recent_empty)) }
        } else {
            items(state.recentWorkouts, key = { it.id }) { workout ->
                ListItem(
                    headlineContent = { Text(workout.title) },
                    supportingContent = { Text(workoutStatusLabel(workout.status)) },
                )
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.home_library_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.home_custom_count, state.exercises.size))
                    OutlinedButton(onClick = onCatalog) { Text(stringResource(R.string.home_open_catalog)) }
                }
            }
        }
        if (state.conflicts.isNotEmpty()) {
            item {
                Button(onClick = onConflicts, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_conflicts, state.conflicts.size))
                }
            }
        }
        item {
            val blocked = state.credentialStatus != GuestCredentialStatus.READY
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.home_sync_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(
                            when {
                                blocked -> R.string.home_sync_blocked
                                state.syncEnabled -> R.string.home_sync_enabled
                                else -> R.string.home_sync_disabled
                            },
                        ),
                    )
                    Text(stringResource(R.string.home_sync_pending, state.pendingSyncCount))
                    Text(stringResource(R.string.home_offline_ready))
                    TextButton(onClick = onPrivacy) { Text(stringResource(R.string.home_manage_privacy)) }
                }
            }
        }
    }
}

@Composable
private fun ProfileScreen(currentName: String, busy: Boolean, onSave: (String) -> Unit, onBack: () -> Unit) {
    var name by rememberSaveable(currentName) { mutableStateOf(currentName) }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.display_name)) }, modifier = Modifier.fillMaxWidth())
        Button({ onSave(name) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save)) }
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
    }
}

@Composable
private fun ExerciseListScreen(
    exercises: List<at.fitnessplatform.core.model.CustomExercise>,
    conflicts: List<ExerciseConflict>,
    onNew: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onResolve: (String) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.custom_exercises_title), style = MaterialTheme.typography.headlineSmall)
            Button(onClick = onNew) { Text(stringResource(R.string.add)) }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(exercises, key = { it.id }) { exercise ->
                val conflict = conflicts.any { it.exerciseId == exercise.id }
                val conflictDescription = stringResource(R.string.sync_conflict_description, exercise.name)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                            if (conflict) AssistChip(
                                onClick = { onResolve(exercise.id) },
                                label = { Text(stringResource(R.string.sync_conflict)) },
                                modifier = Modifier.semantics {
                                    contentDescription = conflictDescription
                                },
                            )
                        }
                        Text(stringResource(R.string.exercise_summary, exercise.primaryMuscleGroup, exercise.requiredEquipment))
                        Row {
                            TextButton({ if (conflict) onResolve(exercise.id) else onEdit(exercise.id) }) {
                                Text(stringResource(if (conflict) R.string.resolve else R.string.edit))
                            }
                            TextButton({ onDelete(exercise.id) }, enabled = !conflict) { Text(stringResource(R.string.delete)) }
                        }
                    }
                }
            }
        }
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
    }
}

@Composable
private fun ExerciseEditorScreen(
    exercise: at.fitnessplatform.core.model.CustomExercise?,
    busy: Boolean,
    onResolveConflict: (String) -> Unit,
    onSave: (String, String, String, String, TrackingType, String) -> Unit,
) {
    val defaultMuscle = stringResource(R.string.exercise_default_muscle)
    val defaultEquipment = stringResource(R.string.exercise_default_equipment)
    var name by rememberSaveable(exercise?.id) { mutableStateOf(exercise?.name.orEmpty()) }
    var description by rememberSaveable(exercise?.id) { mutableStateOf(exercise?.description.orEmpty()) }
    var muscle by rememberSaveable(exercise?.id) { mutableStateOf(exercise?.primaryMuscleGroup ?: defaultMuscle) }
    var equipment by rememberSaveable(exercise?.id) { mutableStateOf(exercise?.requiredEquipment ?: defaultEquipment) }
    var notes by rememberSaveable(exercise?.id) { mutableStateOf(exercise?.notes.orEmpty()) }
    var type by rememberSaveable(exercise?.id) { mutableStateOf(exercise?.trackingType ?: TrackingType.REPS_WEIGHT) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(stringResource(if (exercise == null) R.string.exercise_new else R.string.exercise_edit), style = MaterialTheme.typography.headlineSmall) }
        if (exercise?.syncStatus == at.fitnessplatform.core.model.SyncStatus.CONFLICT) item {
            AssistChip(onClick = { onResolveConflict(exercise.id) }, label = { Text(stringResource(R.string.exercise_resolve_before_editing)) })
        }
        item {
            OutlinedTextField(
                name,
                { name = it },
                label = { Text(stringResource(R.string.name)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        }
        item { OutlinedTextField(description, { description = it }, label = { Text(stringResource(R.string.description)) }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(muscle, { muscle = it }, label = { Text(stringResource(R.string.primary_muscle)) }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(equipment, { equipment = it }, label = { Text(stringResource(R.string.equipment)) }, modifier = Modifier.fillMaxWidth()) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(TrackingType.REPS_WEIGHT, TrackingType.REPS, TrackingType.DURATION).forEach { option ->
                    if (type == option) {
                        Button(onClick = { type = option }, modifier = Modifier.weight(1f)) { Text(trackingTypeLabel(option)) }
                    } else {
                        OutlinedButton(onClick = { type = option }, modifier = Modifier.weight(1f)) { Text(trackingTypeLabel(option)) }
                    }
                }
            }
        }
        item { OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.notes)) }, modifier = Modifier.fillMaxWidth()) }
        item {
            Button(
                { onSave(name, description, muscle, equipment, type, notes) },
                enabled = !busy && exercise?.syncStatus != at.fitnessplatform.core.model.SyncStatus.CONFLICT,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.save_locally)) }
        }
    }
}

@Composable
private fun ConflictListScreen(conflicts: List<ExerciseConflict>, onOpen: (String) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.conflicts_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.conflicts_description))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(conflicts, key = { it.id }) { conflict ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(conflict.localSnapshot.name, style = MaterialTheme.typography.titleMedium)
                        Text(conflictTypeLabel(conflict))
                        TextButton(onClick = { onOpen(conflict.exerciseId) }) { Text(stringResource(R.string.compare_versions)) }
                    }
                }
            }
        }
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
    }
}

@Composable
private fun ConflictResolverScreen(
    conflict: ExerciseConflict,
    busy: Boolean,
    onResolve: (ExerciseConflictResolution, CustomExercise?) -> Unit,
    onBack: () -> Unit,
) {
    var name by rememberSaveable(conflict.id) { mutableStateOf(conflict.localSnapshot.name) }
    var description by rememberSaveable(conflict.id) { mutableStateOf(conflict.localSnapshot.description) }
    var notes by rememberSaveable(conflict.id) { mutableStateOf(conflict.localSnapshot.notes) }
    var confirmation by rememberSaveable { mutableStateOf<ExerciseConflictResolution?>(null) }
    val local = conflict.localSnapshot
    val remote = conflict.remoteSnapshot
    val conflictType = conflictTypeLabel(conflict)
    val conflictTypeDescription = stringResource(R.string.conflict_type, conflictType)
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(stringResource(R.string.conflict_resolve_title), style = MaterialTheme.typography.headlineSmall) }
        item { Text(conflictType, modifier = Modifier.semantics { contentDescription = conflictTypeDescription }) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.local_version, conflict.localRevision?.toString() ?: stringResource(R.string.unknown)), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.field_name, local.name))
                    Text(stringResource(R.string.field_description, local.description))
                    Text(stringResource(R.string.field_notes, local.notes))
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.server_version, conflict.remoteRevision), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.field_name, remote.name))
                    Text(stringResource(R.string.field_description, remote.description))
                    Text(stringResource(R.string.field_notes, remote.notes))
                }
            }
        }
        item { Text(stringResource(R.string.manual_merge), style = MaterialTheme.typography.titleMedium) }
        item { OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name)) }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(description, { description = it }, label = { Text(stringResource(R.string.description)) }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.notes)) }, modifier = Modifier.fillMaxWidth()) }
        item { Button(onClick = { confirmation = ExerciseConflictResolution.KEEP_LOCAL }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.keep_local_version)) } }
        item { OutlinedButton(onClick = { confirmation = ExerciseConflictResolution.TAKE_SERVER }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.use_server_version)) } }
        item { Button(onClick = { confirmation = ExerciseConflictResolution.MERGE }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save_manual_merge)) } }
        item { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } }
    }
    confirmation?.let { resolution ->
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(stringResource(R.string.confirm_resolution)) },
            text = { Text(stringResource(if (resolution == ExerciseConflictResolution.TAKE_SERVER) R.string.confirm_take_server else R.string.confirm_queue_version)) },
            confirmButton = {
                TextButton(onClick = {
                    val merged = if (resolution == ExerciseConflictResolution.MERGE) {
                        local.copy(name = name, description = description, notes = notes)
                    } else null
                    confirmation = null
                    onResolve(resolution, merged)
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun WorkoutScreen(
    state: PlatformUiState,
    onCreate: (String, List<String>) -> Unit,
    onStart: (String) -> Unit,
    onComplete: (String) -> Unit,
    onBack: () -> Unit,
) {
    val defaultTitle = stringResource(R.string.workout_default_title)
    var title by rememberSaveable { mutableStateOf(defaultTitle) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.workouts_title), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.workout_title)) }, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = { onCreate(title, state.exercises.firstOrNull()?.let { listOf(it.id) } ?: emptyList()) },
            enabled = !state.operationInProgress,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.workout_create)) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.workouts, key = { it.id }) { workout ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(workout.title, style = MaterialTheme.typography.titleMedium)
                        Text(workoutStatusLabel(workout.status))
                        when (workout.status) {
                            WorkoutStatus.PLANNED -> Button({ onStart(workout.id) }) { Text(stringResource(R.string.start)) }
                            WorkoutStatus.IN_PROGRESS -> Button({ onComplete(workout.id) }) { Text(stringResource(R.string.complete)) }
                            else -> Unit
                        }
                    }
                }
            }
        }
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
    }
}

@Composable
private fun trackingTypeLabel(type: TrackingType): String = stringResource(
    when (type) {
        TrackingType.REPS_WEIGHT -> R.string.tracking_reps_weight
        TrackingType.REPS -> R.string.tracking_reps
        TrackingType.DURATION -> R.string.tracking_duration
        TrackingType.DISTANCE_DURATION -> R.string.tracking_distance_duration
        TrackingType.MANUAL -> R.string.tracking_manual
    },
)

@Composable
private fun conflictTypeLabel(conflict: ExerciseConflict): String = stringResource(
    when (conflict.type) {
        ExerciseConflictType.BOTH_MODIFIED -> R.string.conflict_both_modified
        ExerciseConflictType.REVISION_MISMATCH -> R.string.conflict_revision_mismatch
        ExerciseConflictType.REMOTE_DELETED_LOCAL_MODIFIED -> R.string.conflict_remote_deleted
        ExerciseConflictType.LOCAL_DELETED_REMOTE_MODIFIED -> R.string.conflict_local_deleted
    },
)

@Composable
private fun workoutStatusLabel(status: WorkoutStatus): String = stringResource(
    when (status) {
        WorkoutStatus.PLANNED -> R.string.workout_planned
        WorkoutStatus.IN_PROGRESS -> R.string.workout_in_progress
        WorkoutStatus.PAUSED -> R.string.workout_paused
        WorkoutStatus.COMPLETED -> R.string.workout_completed
        WorkoutStatus.CANCELLED -> R.string.workout_cancelled
    },
)
