package at.fitnessplatform.data

import at.fitnessplatform.domain.*
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryBindings {
    @Binds @Singleton abstract fun bindProfileRepository(impl: RoomGuestProfileRepository): GuestProfileRepository
    @Binds @Singleton abstract fun bindExerciseRepository(impl: RoomExerciseRepository): ExerciseRepository
    @Binds @Singleton abstract fun bindWorkoutRepository(impl: RoomWorkoutRepository): WorkoutRepository
    @Binds @Singleton abstract fun bindCatalogRepository(impl: RoomCatalogRepository): CatalogRepository
    @Binds @Singleton abstract fun bindTrainingLocationRepository(impl: RoomTrainingLocationRepository): TrainingLocationRepository
    @Binds @Singleton abstract fun bindTrainingPlanRepository(impl: RoomTrainingPlanRepository): TrainingPlanRepository
    @Binds @Singleton abstract fun bindTrainingCalendarRepository(impl: RoomTrainingCalendarRepository): TrainingCalendarRepository
    @Binds @Singleton abstract fun bindTrainingPlanCalendarCoordinator(
        impl: RoomTrainingPlanCalendarCoordinator,
    ): TrainingPlanCalendarCoordinator
    @Binds @Singleton abstract fun bindSyncPreferencesRepository(impl: RoomSyncPreferencesRepository): SyncPreferencesRepository
    @Binds @Singleton abstract fun bindEventDispatcher(impl: LocalEventDispatcher): DomainEventDispatcher
}

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {
    @Provides fun observeProfile(repository: GuestProfileRepository) = ObserveProfileUseCase(repository)
    @Provides fun createProfile(repository: GuestProfileRepository) = CreateGuestProfileUseCase(repository)
    @Provides fun updateProfile(repository: GuestProfileRepository) = UpdateGuestProfileUseCase(repository)
    @Provides fun observeExercises(repository: ExerciseRepository) = ObserveExercisesUseCase(repository)
    @Provides fun observeExerciseConflicts(repository: ExerciseRepository) = ObserveExerciseConflictsUseCase(repository)
    @Provides fun createExercise(repository: ExerciseRepository) = CreateExerciseUseCase(repository)
    @Provides fun updateExercise(repository: ExerciseRepository) = UpdateExerciseUseCase(repository)
    @Provides fun deleteExercise(repository: ExerciseRepository) = DeleteExerciseUseCase(repository)
    @Provides fun resolveExerciseConflict(repository: ExerciseRepository) = ResolveExerciseConflictUseCase(repository)
    @Provides fun observeWorkouts(repository: WorkoutRepository) = ObserveWorkoutsUseCase(repository)
    @Provides fun createWorkout(repository: WorkoutRepository) = CreateWorkoutUseCase(repository)
    @Provides fun startWorkout(repository: WorkoutRepository) = StartWorkoutUseCase(repository)
    @Provides fun completeWorkout(repository: WorkoutRepository) = CompleteWorkoutUseCase(repository)
}

@Module
@InstallIn(SingletonComponent::class)
object TrainingLocationUseCaseModule {
    @Provides fun observeTrainingLocations(repository: TrainingLocationRepository) = ObserveTrainingLocationsUseCase(repository)
    @Provides fun createTrainingLocation(repository: TrainingLocationRepository) = CreateTrainingLocationUseCase(repository)
    @Provides fun updateTrainingLocation(repository: TrainingLocationRepository) = UpdateTrainingLocationUseCase(repository)
    @Provides fun selectActiveTrainingLocation(repository: TrainingLocationRepository) = SelectActiveTrainingLocationUseCase(repository)
    @Provides fun updateLocationEquipment(repository: TrainingLocationRepository) = UpdateLocationEquipmentUseCase(repository)
    @Provides fun deleteTrainingLocation(repository: TrainingLocationRepository) = DeleteTrainingLocationUseCase(repository)
    @Provides fun observeCompatibleCatalog(catalog: CatalogRepository, locations: TrainingLocationRepository) =
        ObserveCompatibleCatalogUseCase(catalog, locations)
    @Provides fun findCompatibleAlternatives() = FindCompatibleAlternativesUseCase()
}

@Module
@InstallIn(SingletonComponent::class)
object TrainingPlanUseCaseModule {
    @Provides fun observeTrainingPlans(repository: TrainingPlanRepository) = ObserveTrainingPlansUseCase(repository)
    @Provides fun observeTrainingPlan(repository: TrainingPlanRepository) = ObserveTrainingPlanUseCase(repository)
    @Provides fun observeActiveTrainingPlan(repository: TrainingPlanRepository) = ObserveActiveTrainingPlanUseCase(repository)
    @Provides fun saveTrainingPlan(repository: TrainingPlanRepository) = SaveTrainingPlanUseCase(repository)
    @Provides fun copyTrainingPlan(repository: TrainingPlanRepository) = CopyTrainingPlanUseCase(repository)
    @Provides fun adaptTrainingPlanCopy(
        repository: TrainingPlanRepository,
        alternatives: FindCompatibleAlternativesUseCase,
    ) = AdaptTrainingPlanCopyUseCase(repository, alternatives)
    @Provides fun editTrainingPlan(repository: TrainingPlanRepository, ids: at.fitnessplatform.core.model.UuidProvider) =
        EditTrainingPlanUseCase(repository, ids)
    @Provides fun setActiveTrainingPlan(
        repository: TrainingPlanRepository,
        calendarRepository: TrainingCalendarRepository,
    ) = SetActiveTrainingPlanUseCase(repository, calendarRepository)
    @Provides fun archiveTrainingPlan(
        coordinator: TrainingPlanCalendarCoordinator,
    ) = ArchiveTrainingPlanUseCase(coordinator)
    @Provides fun deleteTrainingPlan(
        coordinator: TrainingPlanCalendarCoordinator,
    ) = DeleteTrainingPlanUseCase(coordinator)
    @Provides fun seedStarterTrainingPlans(repository: TrainingPlanRepository) = SeedStarterTrainingPlansUseCase(repository)
}

@Module
@InstallIn(SingletonComponent::class)
object TrainingCalendarUseCaseModule {
    @Provides fun ensureCalendarHorizon(repository: TrainingCalendarRepository) = EnsureCalendarHorizonUseCase(repository)
    @Provides fun moveOccurrence(repository: TrainingCalendarRepository) = MoveOccurrenceUseCase(repository)
    @Provides fun changeOccurrenceStatus(repository: TrainingCalendarRepository) = ChangeOccurrenceStatusUseCase(repository)
    @Provides fun detectCalendarConflicts() = DetectCalendarConflictsUseCase()
    @Provides fun previewPlanSchedule(clock: at.fitnessplatform.core.model.Clock) =
        at.fitnessplatform.domain.PreviewPlanScheduleUseCase(clock)
    @Provides fun createPlanSchedule(
        repository: TrainingCalendarRepository,
        ids: at.fitnessplatform.core.model.UuidProvider,
        clock: at.fitnessplatform.core.model.Clock,
    ) = CreatePlanScheduleUseCase(repository, ids, clock)
    @Provides fun activatePlanWithSchedule(
        coordinator: TrainingPlanCalendarCoordinator,
        ids: at.fitnessplatform.core.model.UuidProvider,
        clock: at.fitnessplatform.core.model.Clock,
    ) = ActivatePlanWithScheduleUseCase(coordinator, ids, clock)
    @Provides fun updateScheduleRule(repository: TrainingCalendarRepository) = UpdateScheduleRuleUseCase(repository)
}
