@file:Suppress("MatchingDeclarationName")

package at.fitnessplatform.feature.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import at.fitnessplatform.core.designsystem.MomentumCard
import at.fitnessplatform.core.designsystem.MomentumScreen
import at.fitnessplatform.core.designsystem.MomentumSkeletonLine
import at.fitnessplatform.core.designsystem.MomentumTheme

internal object Routes {
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
        "custom-exercise-edit", "conflicts", "conflict" -> Routes.EXERCISES
        "profile", "privacy", "guest-recovery", "settings", "locations" -> Routes.PROFILE
        else -> null
    }

internal fun showsUpNavigation(route: String?): Boolean =
    route != null && rootDestinations.none { it.route == route }

internal fun routeTitle(route: String?): Int = when (route?.substringBefore('/')) {
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

@OptIn(ExperimentalMaterial3Api::class)
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
                                    onWorkouts = { navController.navigateRoot(Routes.WORKOUTS) },
                                    onConflicts = { navController.navigate(Routes.CONFLICTS) },
                                    onLocations = { navController.navigate(Routes.LOCATIONS) },
                                    onPlans = { navController.navigate(Routes.PLANS) },
                                    onOpen = { navController.navigate(workoutDetailRoute(it)) },
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
                                ExerciseEditorScreen(exercise, state.operationInProgress) { name, description, muscle, equipment, type, notes ->
                                    viewModel.saveExercise(id, name, description, muscle, equipment, type, notes) { navController.popBackStack() }
                                }
                            }
                            composable(Routes.CONFLICTS) {
                                ConflictListScreen(state.conflicts, onOpen = { navController.navigate("conflict/$it") })
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
                                    workoutId = id,
                                    state = state,
                                    onStart = viewModel::startWorkout,
                                    onComplete = viewModel::completeWorkout,
                                    onRepeat = { viewModel.repeatWorkout(id) { newId -> navController.navigate(workoutDetailRoute(newId)) { launchSingleTop = true } } },
                                )
                            }
                            composable(Routes.CATALOG) { CatalogRoute(onOpen = { navController.navigate("catalog/$it") }) }
                            composable(Routes.PRIVACY) { PrivacyScreen() }
                            composable(Routes.LOCATIONS) { TrainingLocationsRoute() }
                            composable(Routes.PLANS) { TrainingPlansRoute(profile.id) }
                            composable(Routes.CALENDAR) { TrainingCalendarRoute() }
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
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
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
