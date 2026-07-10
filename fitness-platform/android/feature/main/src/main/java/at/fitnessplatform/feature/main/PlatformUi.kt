@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package at.fitnessplatform.feature.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import at.fitnessplatform.core.model.TrackingType
import at.fitnessplatform.core.model.WorkoutStatus

private object Routes {
    const val HOME = "home"
    const val PROFILE = "profile"
    const val EXERCISES = "exercises"
    const val EXERCISE = "exercise/{exerciseId}"
    const val WORKOUTS = "workouts"
}

@Composable
fun FitnessPlatformRoot(viewModel: PlatformViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val profile = state.profile
    val navController = rememberNavController()
    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Fitness Platform Scaffold") }) },
            snackbarHost = {
                val message = state.errorMessage
                if (message != null) {
                    LaunchedEffect(message) { viewModel.clearError() }
                }
            },
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
                NavHost(navController, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
                    composable(Routes.HOME) {
                        HomeScreen(
                            displayName = profile.displayName,
                            exerciseCount = state.exercises.size,
                            workoutCount = state.workouts.size,
                            onProfile = { navController.navigate(Routes.PROFILE) },
                            onExercises = { navController.navigate(Routes.EXERCISES) },
                            onWorkouts = { navController.navigate(Routes.WORKOUTS) },
                        )
                    }
                    composable(Routes.PROFILE) {
                        ProfileScreen(profile.displayName, state.operationInProgress, viewModel::renameGuest) { navController.popBackStack() }
                    }
                    composable(Routes.EXERCISES) {
                        ExerciseListScreen(
                            state.exercises,
                            onNew = { navController.navigate("exercise/new") },
                            onEdit = { navController.navigate("exercise/$it") },
                            onDelete = viewModel::deleteExercise,
                            onBack = { navController.popBackStack() },
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
                    composable(Routes.WORKOUTS) {
                        WorkoutScreen(
                            state = state,
                            onCreate = viewModel::createWorkout,
                            onStart = viewModel::startWorkout,
                            onComplete = viewModel::completeWorkout,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateGuestScreen(modifier: Modifier, busy: Boolean, onCreate: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("Guest") }
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Offline guest profile", style = MaterialTheme.typography.headlineSmall)
        Text("Onboarding is optional. This local profile can later be linked to an account.")
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(name, { name = it }, label = { Text("Display name") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Button(onClick = { onCreate(name) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Create guest profile") }
    }
}

@Composable
private fun HomeScreen(displayName: String, exerciseCount: Int, workoutCount: Int, onProfile: () -> Unit, onExercises: () -> Unit, onWorkouts: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Hello, $displayName", style = MaterialTheme.typography.headlineMedium)
        Text("$exerciseCount custom exercises · $workoutCount workouts")
        Button(onClick = onProfile, modifier = Modifier.fillMaxWidth()) { Text("Edit profile") }
        Button(onClick = onExercises, modifier = Modifier.fillMaxWidth()) { Text("Custom exercises") }
        Button(onClick = onWorkouts, modifier = Modifier.fillMaxWidth()) { Text("Workouts") }
        HorizontalDivider()
        Text("XP, quests, integrations, commerce, social features and AI are architectural extension points only in this scaffold.")
    }
}

@Composable
private fun ProfileScreen(currentName: String, busy: Boolean, onSave: (String) -> Unit, onBack: () -> Unit) {
    var name by rememberSaveable(currentName) { mutableStateOf(currentName) }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Guest profile", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(name, { name = it }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth())
        Button({ onSave(name) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Save") }
        TextButton(onClick = onBack) { Text("Back") }
    }
}

@Composable
private fun ExerciseListScreen(
    exercises: List<at.fitnessplatform.core.model.CustomExercise>,
    onNew: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Custom exercises", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = onNew) { Text("Add") }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(exercises, key = { it.id }) { exercise ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                        Text("${exercise.primaryMuscleGroup} · ${exercise.requiredEquipment}")
                        Row { TextButton({ onEdit(exercise.id) }) { Text("Edit") }; TextButton({ onDelete(exercise.id) }) { Text("Delete") } }
                    }
                }
            }
        }
        TextButton(onClick = onBack) { Text("Back") }
    }
}

@Composable
private fun ExerciseEditorScreen(
    exercise: at.fitnessplatform.core.model.CustomExercise?,
    busy: Boolean,
    onSave: (String, String, String, String, TrackingType, String) -> Unit,
) {
    var name by rememberSaveable(exercise?.id) { mutableStateOf(exercise?.name.orEmpty()) }
    var description by rememberSaveable(exercise?.id) { mutableStateOf(exercise?.description.orEmpty()) }
    var muscle by rememberSaveable(exercise?.id) { mutableStateOf(exercise?.primaryMuscleGroup ?: "Full body") }
    var equipment by rememberSaveable(exercise?.id) { mutableStateOf(exercise?.requiredEquipment ?: "None") }
    var notes by rememberSaveable(exercise?.id) { mutableStateOf(exercise?.notes.orEmpty()) }
    var type by rememberSaveable(exercise?.id) { mutableStateOf(exercise?.trackingType ?: TrackingType.REPS_WEIGHT) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(if (exercise == null) "New exercise" else "Edit exercise", style = MaterialTheme.typography.headlineSmall) }
        item { OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)) }
        item { OutlinedTextField(description, { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(muscle, { muscle = it }, label = { Text("Primary muscle") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(equipment, { equipment = it }, label = { Text("Equipment") }, modifier = Modifier.fillMaxWidth()) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(TrackingType.REPS_WEIGHT, TrackingType.REPS, TrackingType.DURATION).forEach { option ->
                    if (type == option) {
                        Button(onClick = { type = option }, modifier = Modifier.weight(1f)) { Text(option.name) }
                    } else {
                        OutlinedButton(onClick = { type = option }, modifier = Modifier.weight(1f)) { Text(option.name) }
                    }
                }
            }
        }
        item { OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth()) }
        item { Button({ onSave(name, description, muscle, equipment, type, notes) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Save locally") } }
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
    var title by rememberSaveable { mutableStateOf("Quick workout") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Workouts", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(title, { title = it }, label = { Text("Workout title") }, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = { onCreate(title, state.exercises.firstOrNull()?.let { listOf(it.id) } ?: emptyList()) },
            enabled = !state.operationInProgress,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Create workout") }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.workouts, key = { it.id }) { workout ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(workout.title, style = MaterialTheme.typography.titleMedium)
                        Text(workout.status.name)
                        when (workout.status) {
                            WorkoutStatus.PLANNED -> Button({ onStart(workout.id) }) { Text("Start") }
                            WorkoutStatus.IN_PROGRESS -> Button({ onComplete(workout.id) }) { Text("Complete") }
                            else -> Unit
                        }
                    }
                }
            }
        }
        TextButton(onClick = onBack) { Text("Back") }
    }
}
