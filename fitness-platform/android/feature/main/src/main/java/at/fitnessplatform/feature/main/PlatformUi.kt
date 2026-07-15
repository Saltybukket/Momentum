@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package at.fitnessplatform.feature.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import at.fitnessplatform.core.model.Workout
import at.fitnessplatform.core.model.WorkoutExercise
import at.fitnessplatform.domain.GuestCredentialStatus
import at.fitnessplatform.core.designsystem.MomentumCard
import at.fitnessplatform.core.designsystem.MomentumEmptyState
import at.fitnessplatform.core.designsystem.MomentumScreen
import at.fitnessplatform.core.designsystem.MomentumSectionHeader
import at.fitnessplatform.core.designsystem.MomentumSpacing
import at.fitnessplatform.core.designsystem.MomentumTheme
import at.fitnessplatform.core.designsystem.MomentumSkeletonLine
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private object Routes {
    const val HOME = "home"
    const val PROFILE = "profile"
    const val EXERCISES = "exercises"
    const val EXERCISE = "exercise/{exerciseId}"
    const val CONFLICTS = "conflicts"
    const val CONFLICT = "conflict/{exerciseId}"
    const val WORKOUTS = "workouts"
    const val WORKOUT_DETAIL = "workout-detail/{workoutId}"
    const val CATALOG = "catalog"
    const val CATALOG_EXERCISE = "catalog/{catalogId}"
    const val PRIVACY = "privacy"
    const val LOCATIONS = "locations"
    const val PLANS = "plans"
    const val CALENDAR = "calendar"
}

private data class RootDestination(
    val route: String,
    val label: Int,
    val icon: Int,
)

private val rootDestinations = listOf(
    RootDestination(Routes.HOME, R.string.nav_home, R.drawable.ic_home),
    RootDestination(Routes.WORKOUTS, R.string.nav_workouts, R.drawable.ic_workouts),
    RootDestination(Routes.EXERCISES, R.string.nav_exercises, R.drawable.ic_exercises),
    RootDestination(Routes.PROFILE, R.string.nav_profile, R.drawable.ic_profile),
)

internal fun usesNavigationRail(width: Dp): Boolean = width >= 840.dp

internal fun rootRouteFor(route: String?): String? =
    when (route?.substringBefore('/')) {
        "home" -> Routes.HOME
        "workouts", "workout-detail", "active-workout", "workout-summary", "plans", "calendar" -> Routes.WORKOUTS
        "exercises", "exercise", "catalog", "catalog-detail", "custom-exercise",
        "custom-exercise-edit", "conflicts", "conflict",
        -> Routes.EXERCISES
        "profile", "privacy", "guest-recovery", "settings", "locations" -> Routes.PROFILE
        else -> null
    }

internal fun showsUpNavigation(route: String?): Boolean =
    route != null && rootDestinations.none { it.route == route }

internal fun workoutDetailRoute(workoutId: String): String {
    require(workoutId.isNotBlank() && '/' !in workoutId) { "Workout ID is not route-safe." }
    return "workout-detail/$workoutId"
}

private fun routeTitle(route: String?): Int = when (route?.substringBefore('/')) {
    "workouts", "workout-detail" -> R.string.workouts_title
    "exercises", "exercise", "conflicts", "conflict" -> R.string.custom_exercises_title
    "catalog" -> R.string.catalog_title
    "privacy" -> R.string.privacy_title
    "locations" -> R.string.locations_title
    "plans" -> R.string.plans_title
    "calendar" -> R.string.calendar_title
    "profile" -> R.string.nav_profile
    else -> R.string.app_name
}

@Composable
@Suppress("LongMethod")
fun FitnessPlatformRoot(viewModel: PlatformViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val profile = state.profile
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
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
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        if (showsUpNavigation(currentRoute)) {
                            IconButton(
                                onClick = {
                                    if (!navController.popBackStack()) {
                                        navController.navigateRoot(rootRouteFor(currentRoute) ?: Routes.HOME)
                                    }
                                },
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_back),
                                    contentDescription = stringResource(R.string.navigate_up),
                                )
                            }
                        }
                    },
                    title = {
                        Column {
                            Text(stringResource(routeTitle(currentRoute)))
                            if (currentRoute == Routes.HOME) {
                                Text(
                                    stringResource(R.string.app_tagline),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            if (state.isLoading) {
                MomentumScreen(Modifier.fillMaxSize().padding(padding)) {
                    item { MomentumSkeletonLine(Modifier.fillMaxWidth(0.55f)) }
                    repeat(3) {
                        item { MomentumCard(Modifier.fillMaxWidth()) { MomentumSkeletonLine(Modifier.fillMaxWidth()) } }
                    }
                }
            } else if (profile == null) {
                CreateGuestScreen(
                    modifier = Modifier.padding(padding),
                    busy = state.operationInProgress,
                    onCreate = viewModel::createGuest,
                )
            } else {
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
                            onLocations = { navController.navigate(Routes.LOCATIONS) },
                            onPlans = { navController.navigate(Routes.PLANS) },
                        )
                    }
                    composable(Routes.PROFILE) {
                        ProfileScreen(
                            profile.displayName,
                            state.operationInProgress,
                            viewModel::renameGuest,
                            onLocations = { navController.navigate(Routes.LOCATIONS) },
                            onPrivacy = { navController.navigate(Routes.PRIVACY) },
                        )
                    }
                    composable(Routes.EXERCISES) {
                        ExerciseListScreen(
                            state.exercises,
                            state.conflicts,
                            onNew = { navController.navigate("exercise/new") },
                            onEdit = { navController.navigate("exercise/$it") },
                            onDelete = viewModel::deleteExercise,
                            onResolve = { navController.navigate("conflict/$it") },
                            onCatalog = { navController.navigate(Routes.CATALOG) },
                            onConflicts = { navController.navigate(Routes.CONFLICTS) },
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
                            onOpen = { navController.navigate(workoutDetailRoute(it)) },
                            onPlans = { navController.navigate(Routes.PLANS) },
                            onCalendar = { navController.navigate(Routes.CALENDAR) },
                        )
                    }
                    composable(
                        Routes.WORKOUT_DETAIL,
                        arguments = listOf(navArgument("workoutId") { type = NavType.StringType }),
                    ) { entry ->
                        val id = entry.arguments?.getString("workoutId").orEmpty()
                        WorkoutDetailScreen(
                            detailState = workoutDetailState(id, state),
                            busy = state.operationInProgress,
                            onStart = viewModel::startWorkout,
                            onComplete = viewModel::completeWorkout,
                            onRepeat = {
                                viewModel.repeatWorkout(id) { newId ->
                                    navController.navigate(workoutDetailRoute(newId)) {
                                        launchSingleTop = true
                                    }
                                }
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(Routes.CATALOG) {
                        CatalogRoute(
                            onOpen = { navController.navigate("catalog/$it") },
                        )
                    }
                    composable(Routes.PRIVACY) {
                        PrivacyScreen()
                    }
                    composable(Routes.LOCATIONS) {
                        TrainingLocationsRoute()
                    }
                    composable(Routes.PLANS) {
                        TrainingPlansRoute(profile.id)
                    }
                    composable(Routes.CALENDAR) {
                        TrainingCalendarRoute()
                    }
                    composable(
                        Routes.CATALOG_EXERCISE,
                        arguments = listOf(navArgument("catalogId") { type = NavType.StringType }),
                    ) { entry ->
                        CatalogDetailRoute(
                            id = entry.arguments?.getString("catalogId").orEmpty(),
                            onOpen = { navController.navigate("catalog/$it") },
                            onSelectLocation = { navController.navigate(Routes.LOCATIONS) },
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
    val selectedRoot = rootRouteFor(currentRoute)
    NavigationBar {
        rootDestinations.forEach { destination ->
            NavigationBarItem(
                selected = selectedRoot == destination.route,
                onClick = { onSelect(destination) },
                icon = {
                    Icon(
                        painter = painterResource(destination.icon),
                        contentDescription = stringResource(destination.label),
                    )
                },
                label = { Text(stringResource(destination.label)) },
                alwaysShowLabel = true,
                modifier = Modifier.testTag("root-nav-${destination.route}"),
            )
        }
    }
}

@Composable
private fun RootNavigationRail(currentRoute: String?, onSelect: (RootDestination) -> Unit) {
    val selectedRoot = rootRouteFor(currentRoute)
    NavigationRail {
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = MomentumSpacing.sm, vertical = MomentumSpacing.md),
        )
        rootDestinations.forEach { destination ->
            NavigationRailItem(
                selected = selectedRoot == destination.route,
                onClick = { onSelect(destination) },
                icon = {
                    Icon(
                        painter = painterResource(destination.icon),
                        contentDescription = stringResource(destination.label),
                    )
                },
                label = { Text(stringResource(destination.label)) },
                modifier = Modifier.testTag("root-nav-${destination.route}"),
            )
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

@Composable
internal fun HomeScreen(
    state: PlatformUiState,
    onCatalog: () -> Unit,
    onPrivacy: () -> Unit,
    onWorkouts: () -> Unit,
    onConflicts: () -> Unit,
    onLocations: () -> Unit,
    onPlans: () -> Unit,
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
            MomentumCard(Modifier.fillMaxWidth(), emphasized = true) {
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
        item {
            MomentumSectionHeader(stringResource(R.string.home_quick_actions))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MomentumSpacing.sm)) {
                OutlinedButton(onClick = onLocations, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.locations_switch))
                }
                OutlinedButton(onClick = onCatalog, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.nav_exercises))
                }
            }
        }
        item { ActiveLocationCard(onManage = onLocations) }
        item {
            MomentumCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.plans_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.plans_description))
                OutlinedButton(onClick = onPlans) { Text(stringResource(R.string.plans_create)) }
            }
        }
        item {
            MomentumSectionHeader(stringResource(R.string.home_recent_title))
        }
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
                ListItem(
                    headlineContent = { Text(workout.title) },
                    supportingContent = { Text(workoutStatusLabel(workout.status)) },
                )
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
                Button(onClick = onConflicts, modifier = Modifier.fillMaxWidth()) {
                    Text(pluralStringResource(R.plurals.home_conflicts, state.conflicts.size, state.conflicts.size))
                }
            }
        }
        item {
            val blocked = state.credentialStatus != GuestCredentialStatus.READY
            MomentumCard(Modifier.fillMaxWidth()) {
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
                    Text(pluralStringResource(R.plurals.home_sync_pending, state.pendingSyncCount, state.pendingSyncCount))
                    Text(stringResource(R.string.home_offline_ready))
                    TextButton(onClick = onPrivacy) { Text(stringResource(R.string.home_manage_privacy)) }
            }
        }
}

@Composable
internal fun ProfileScreen(
    currentName: String,
    busy: Boolean,
    onSave: (String) -> Unit,
    onLocations: () -> Unit,
    onPrivacy: () -> Unit,
) {
    var name by rememberSaveable(currentName) { mutableStateOf(currentName) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MomentumSectionHeader(stringResource(R.string.profile_identity), stringResource(R.string.profile_title))
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.display_name)) }, modifier = Modifier.fillMaxWidth())
        Button({ onSave(name) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save)) }
        OutlinedButton(onClick = onLocations, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.locations_manage))
        }
        MomentumSectionHeader(stringResource(R.string.profile_sync_privacy))
        OutlinedButton(onClick = onPrivacy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.home_manage_privacy))
        }
    }
}

@Composable
internal fun ExerciseListScreen(
    exercises: List<at.fitnessplatform.core.model.CustomExercise>,
    conflicts: List<ExerciseConflict>,
    onNew: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onResolve: (String) -> Unit,
    onCatalog: () -> Unit,
    onConflicts: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val visibleExercises = exercises.filter { it.name.contains(query.trim(), ignoreCase = true) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = true,
                onClick = {},
                label = { Text(stringResource(R.string.exercises_mine)) },
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onCatalog, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.exercises_public)) }
            if (conflicts.isNotEmpty()) {
                OutlinedButton(onClick = onConflicts, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.exercises_conflicts)) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = onNew) { Text(stringResource(R.string.add)) }
        }
        OutlinedTextField(
            query,
            { query = it },
            label = { Text(stringResource(R.string.exercises_search)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visibleExercises, key = { it.id }) { exercise ->
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
internal fun WorkoutScreen(
    state: PlatformUiState,
    onCreate: (String, List<String>) -> Unit,
    onStart: (String) -> Unit,
    onComplete: (String) -> Unit,
    onOpen: (String) -> Unit,
    onPlans: () -> Unit,
    onCalendar: () -> Unit,
) {
    val defaultTitle = stringResource(R.string.workout_default_title)
    var title by rememberSaveable { mutableStateOf(defaultTitle) }
    val active = state.workouts.filter { it.status == WorkoutStatus.IN_PROGRESS || it.status == WorkoutStatus.PAUSED }
    val planned = state.workouts.filter { it.status == WorkoutStatus.PLANNED }
    val history = state.workouts.filter { it.status == WorkoutStatus.COMPLETED || it.status == WorkoutStatus.CANCELLED }
    var section by rememberSaveable { mutableStateOf(WorkoutListSection.TODAY) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            FilterChip(
                selected = section == WorkoutListSection.TODAY,
                onClick = { section = WorkoutListSection.TODAY },
                label = { Text(stringResource(R.string.calendar_today)) },
            )
            FilterChip(selected = false, onClick = onCalendar, label = { Text(stringResource(R.string.calendar_title)) })
            FilterChip(selected = false, onClick = onPlans, label = { Text(stringResource(R.string.plans_title)) })
            FilterChip(
                selected = section == WorkoutListSection.HISTORY,
                onClick = { section = WorkoutListSection.HISTORY },
                label = { Text(stringResource(R.string.workouts_history)) },
            )
        }
        OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.workout_title)) }, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = { onCreate(title, newWorkoutExerciseIds()) },
            enabled = !state.operationInProgress,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.workout_create)) }
        Text(
            stringResource(R.string.workout_empty_creation_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (section == WorkoutListSection.TODAY) {
                workoutSection(R.string.workouts_active, active, state.operationInProgress, onStart, onComplete, onOpen)
                workoutSection(R.string.workouts_planned, planned, state.operationInProgress, onStart, onComplete, onOpen)
            } else {
                workoutSection(R.string.workouts_history, history, state.operationInProgress, onStart, onComplete, onOpen)
            }
        }
    }
}

private enum class WorkoutListSection { TODAY, HISTORY }

internal fun newWorkoutExerciseIds(): List<String> = emptyList()

private fun androidx.compose.foundation.lazy.LazyListScope.workoutSection(
    title: Int,
    workouts: List<at.fitnessplatform.core.model.Workout>,
    busy: Boolean,
    onStart: (String) -> Unit,
    onComplete: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    if (workouts.isEmpty()) return
    item { MomentumSectionHeader(stringResource(title)) }
    items(workouts, key = { it.id }) { workout ->
        Card(
            onClick = { onOpen(workout.id) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(workout.title, style = MaterialTheme.typography.titleMedium)
                Text(workoutStatusLabel(workout.status))
                when (workout.status) {
                    WorkoutStatus.PLANNED -> Button({ onStart(workout.id) }, enabled = !busy) { Text(stringResource(R.string.start)) }
                    WorkoutStatus.IN_PROGRESS -> Button({ onComplete(workout.id) }, enabled = !busy) { Text(stringResource(R.string.complete)) }
                    else -> Unit
                }
            }
        }
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

internal data class ResolvedWorkoutExercise(
    val link: WorkoutExercise,
    val exercise: CustomExercise?,
)

internal sealed interface WorkoutDetailUiState {
    data object Loading : WorkoutDetailUiState
    data object NotFound : WorkoutDetailUiState
    data class Content(
        val workout: Workout,
        val exercises: List<ResolvedWorkoutExercise>,
        val repeatAllowed: Boolean,
    ) : WorkoutDetailUiState
}

internal fun resolveWorkoutExercises(
    workout: Workout,
    exercises: List<CustomExercise>,
): List<ResolvedWorkoutExercise> {
    val byId = exercises.associateBy { it.id }
    return workout.exercises.sortedBy { it.position }.map { link ->
        ResolvedWorkoutExercise(link, byId[link.exerciseId])
    }
}

internal fun workoutDetailState(workoutId: String, state: PlatformUiState): WorkoutDetailUiState {
    return if (state.isLoading) {
        WorkoutDetailUiState.Loading
    } else {
        state.workouts.firstOrNull { it.id == workoutId }?.let { workout ->
            val resolved = resolveWorkoutExercises(workout, state.exercises)
            WorkoutDetailUiState.Content(
                workout = workout,
                exercises = resolved,
                repeatAllowed = workout.status == WorkoutStatus.COMPLETED &&
                    resolved.all { it.exercise != null },
            )
        } ?: WorkoutDetailUiState.NotFound
    }
}

internal fun formatWorkoutDateTime(
    epochMs: Long,
    locale: Locale = Locale.getDefault(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): String = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    .withLocale(locale)
    .format(Instant.ofEpochMilli(epochMs).atZone(zoneId))

@Composable
internal fun WorkoutDetailScreen(
    detailState: WorkoutDetailUiState,
    busy: Boolean,
    onStart: (String) -> Unit,
    onComplete: (String) -> Unit,
    onRepeat: (String) -> Unit,
    onBack: () -> Unit,
) {
    when (detailState) {
        WorkoutDetailUiState.Loading -> MomentumScreen(Modifier.fillMaxSize()) {
            item { MomentumSectionHeader(stringResource(R.string.workout_detail_loading)) }
            item { MomentumSkeletonLine(Modifier.fillMaxWidth()) }
        }
        WorkoutDetailUiState.NotFound -> MomentumScreen(Modifier.fillMaxSize()) {
            item {
                MomentumEmptyState(
                    stringResource(R.string.workout_detail_not_found_title),
                    stringResource(R.string.workout_detail_not_found_body),
                    stringResource(R.string.back),
                    onBack,
                )
            }
        }
        is WorkoutDetailUiState.Content -> WorkoutDetailContent(
            state = detailState,
            busy = busy,
            onStart = onStart,
            onComplete = onComplete,
            onRepeat = onRepeat,
        )
    }
}

@Composable
internal fun WorkoutDetailContent(
    state: WorkoutDetailUiState.Content,
    busy: Boolean,
    onStart: (String) -> Unit,
    onComplete: (String) -> Unit,
    onRepeat: (String) -> Unit,
) {
    val workout = state.workout
    MomentumScreen(Modifier.fillMaxSize()) {
        item { MomentumSectionHeader(workout.title, workoutStatusLabel(workout.status)) }
        val startMs = workout.startTimeEpochMs
        if (startMs != null) {
            item { MomentumCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.workout_started, formatWorkoutDateTime(startMs)))
            } }
        }
        val endMs = workout.endTimeEpochMs
        if (endMs != null) {
            item { MomentumCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.workout_ended, formatWorkoutDateTime(endMs)))
            } }
        }
        if (workout.notes.isNotBlank()) {
            item { MomentumCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.workout_notes_label))
                Text(workout.notes, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
        }
        if (state.exercises.isNotEmpty()) {
            item { MomentumSectionHeader(stringResource(R.string.workout_exercises_label)) }
            items(state.exercises, key = { it.link.id }) { resolved ->
                val exercise = resolved.exercise
                val exerciseDescription = exercise?.name
                    ?: stringResource(R.string.workout_exercise_missing)
                MomentumCard(Modifier.fillMaxWidth().semantics { contentDescription = exerciseDescription }) {
                    if (exercise != null) {
                        Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.exercise_summary, exercise.primaryMuscleGroup, exercise.requiredEquipment),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            stringResource(R.string.workout_exercise_missing),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
        when (workout.status) {
            WorkoutStatus.PLANNED -> item { Button({ onStart(workout.id) }, Modifier.fillMaxWidth(), enabled = !busy) {
                Text(stringResource(R.string.start))
            } }
            WorkoutStatus.IN_PROGRESS -> item { Button({ onComplete(workout.id) }, Modifier.fillMaxWidth(), enabled = !busy) {
                Text(stringResource(R.string.complete))
            } }
            WorkoutStatus.COMPLETED -> {
                item { Button(
                    { onRepeat(workout.id) },
                    Modifier.fillMaxWidth(),
                    enabled = !busy && state.repeatAllowed,
                ) { Text(stringResource(R.string.workout_repeat)) } }
                if (!state.repeatAllowed) {
                    item { Text(
                        stringResource(R.string.workout_repeat_unavailable_missing),
                        color = MaterialTheme.colorScheme.error,
                    ) }
                }
            }
            else -> Unit
        }
    }
}
