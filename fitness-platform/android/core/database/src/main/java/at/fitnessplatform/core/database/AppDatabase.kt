package at.fitnessplatform.core.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [
        GuestProfileEntity::class,
        CustomExerciseEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
        OutboxEntity::class,
        ExerciseConflictEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun guestProfileDao(): GuestProfileDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun outboxDao(): OutboxDao
    abstract fun exerciseConflictDao(): ExerciseConflictDao
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `exercise_conflicts` (" +
                "`id` TEXT NOT NULL, `exerciseId` TEXT NOT NULL, `conflictType` TEXT NOT NULL, " +
                "`localRevision` INTEGER, `remoteRevision` INTEGER NOT NULL, `localSnapshotJson` TEXT NOT NULL, " +
                "`remoteSnapshotJson` TEXT NOT NULL, `detectedAtEpochMs` INTEGER NOT NULL, " +
                "`resolutionStatus` TEXT NOT NULL, `resolvedAtEpochMs` INTEGER, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`exerciseId`) REFERENCES `custom_exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_exercise_conflicts_exerciseId` ON `exercise_conflicts` (`exerciseId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_conflicts_resolutionStatus` ON `exercise_conflicts` (`resolutionStatus`)")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "fitness-platform.db")
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides fun provideProfileDao(db: AppDatabase): GuestProfileDao = db.guestProfileDao()
    @Provides fun provideExerciseDao(db: AppDatabase): ExerciseDao = db.exerciseDao()
    @Provides fun provideWorkoutDao(db: AppDatabase): WorkoutDao = db.workoutDao()
    @Provides fun provideOutboxDao(db: AppDatabase): OutboxDao = db.outboxDao()
    @Provides fun provideExerciseConflictDao(db: AppDatabase): ExerciseConflictDao = db.exerciseConflictDao()
}
