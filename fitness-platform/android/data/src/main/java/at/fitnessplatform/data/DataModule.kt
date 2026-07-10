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
    @Binds @Singleton abstract fun bindEventDispatcher(impl: LocalEventDispatcher): DomainEventDispatcher
}

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {
    @Provides fun observeProfile(repository: GuestProfileRepository) = ObserveProfileUseCase(repository)
    @Provides fun createProfile(repository: GuestProfileRepository) = CreateGuestProfileUseCase(repository)
    @Provides fun updateProfile(repository: GuestProfileRepository) = UpdateGuestProfileUseCase(repository)
    @Provides fun observeExercises(repository: ExerciseRepository) = ObserveExercisesUseCase(repository)
    @Provides fun createExercise(repository: ExerciseRepository) = CreateExerciseUseCase(repository)
    @Provides fun updateExercise(repository: ExerciseRepository) = UpdateExerciseUseCase(repository)
    @Provides fun deleteExercise(repository: ExerciseRepository) = DeleteExerciseUseCase(repository)
    @Provides fun observeWorkouts(repository: WorkoutRepository) = ObserveWorkoutsUseCase(repository)
    @Provides fun createWorkout(repository: WorkoutRepository) = CreateWorkoutUseCase(repository)
    @Provides fun startWorkout(repository: WorkoutRepository) = StartWorkoutUseCase(repository)
    @Provides fun completeWorkout(repository: WorkoutRepository) = CompleteWorkoutUseCase(repository)
}
