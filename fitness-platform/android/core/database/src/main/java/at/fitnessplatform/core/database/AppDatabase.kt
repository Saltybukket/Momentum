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

interface DatabaseStartupProbe {
    fun verifyStartup()
}

@Database(
    entities = [
        GuestProfileEntity::class,
        CustomExerciseEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
        OutboxEntity::class,
        ExerciseConflictEntity::class,
        CatalogExerciseEntity::class,
        CatalogMuscleEntity::class,
        CatalogEquipmentEntity::class,
        CatalogExerciseMuscleEntity::class,
        CatalogExerciseEquipmentEntity::class,
        CatalogMetadataEntity::class,
        SyncStateEntity::class,
        TrainingLocationEntity::class,
        TrainingLocationEquipmentEntity::class,
        TrainingPlanEntity::class,
        PlanWeekEntity::class,
        PlanDayEntity::class,
        PlanBlockEntity::class,
        PlanExerciseEntity::class,
        PlanSetPrescriptionEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun guestProfileDao(): GuestProfileDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun outboxDao(): OutboxDao
    abstract fun exerciseConflictDao(): ExerciseConflictDao
    abstract fun catalogDao(): CatalogDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun trainingLocationDao(): TrainingLocationDao
    abstract fun trainingPlanDao(): TrainingPlanDao
}

private class RoomDatabaseStartupProbe(private val database: AppDatabase) : DatabaseStartupProbe {
    override fun verifyStartup() {
        database.openHelper.writableDatabase
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
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

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `catalog_exercises` (`id` TEXT NOT NULL,
                `externalId` TEXT NOT NULL, `source` TEXT NOT NULL, `provenance` TEXT NOT NULL,
                `licenseName` TEXT NOT NULL, `licenseUrl` TEXT NOT NULL, `version` TEXT NOT NULL,
                `status` TEXT NOT NULL, `reviewed` INTEGER NOT NULL, `name` TEXT NOT NULL,
                `description` TEXT NOT NULL, `trackingType` TEXT NOT NULL, PRIMARY KEY(`id`))""",
        )
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_catalog_exercises_source_externalId` ON `catalog_exercises` (`source`, `externalId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_catalog_exercises_name` ON `catalog_exercises` (`name`)")
        database.execSQL("CREATE TABLE IF NOT EXISTS `catalog_muscles` (`slug` TEXT NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`slug`))")
        database.execSQL("CREATE TABLE IF NOT EXISTS `catalog_equipment` (`slug` TEXT NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`slug`))")
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `catalog_exercise_muscles` (`exerciseId` TEXT NOT NULL,
                `muscleSlug` TEXT NOT NULL, `role` TEXT NOT NULL,
                PRIMARY KEY(`exerciseId`, `muscleSlug`), FOREIGN KEY(`exerciseId`)
                REFERENCES `catalog_exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`muscleSlug`) REFERENCES `catalog_muscles`(`slug`)
                ON UPDATE NO ACTION ON DELETE RESTRICT)""",
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_catalog_exercise_muscles_exerciseId` ON `catalog_exercise_muscles` (`exerciseId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_catalog_exercise_muscles_muscleSlug` ON `catalog_exercise_muscles` (`muscleSlug`)")
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `catalog_exercise_equipment` (`exerciseId` TEXT NOT NULL,
                `equipmentSlug` TEXT NOT NULL, PRIMARY KEY(`exerciseId`, `equipmentSlug`),
                FOREIGN KEY(`exerciseId`) REFERENCES `catalog_exercises`(`id`)
                ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`equipmentSlug`)
                REFERENCES `catalog_equipment`(`slug`) ON UPDATE NO ACTION ON DELETE RESTRICT)""",
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_catalog_exercise_equipment_exerciseId` ON `catalog_exercise_equipment` (`exerciseId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_catalog_exercise_equipment_equipmentSlug` ON `catalog_exercise_equipment` (`equipmentSlug`)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `sync_outbox` ADD COLUMN `claimOwner` TEXT")
        database.execSQL("ALTER TABLE `sync_outbox` ADD COLUMN `claimExpiresAtEpochMs` INTEGER")
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `catalog_metadata` " +
                "(`singletonId` INTEGER NOT NULL, `schemaVersion` TEXT NOT NULL, " +
                "`catalogVersion` TEXT NOT NULL, `contentHash` TEXT NOT NULL, " +
                "`retrievedAtEpochMs` INTEGER NOT NULL, `source` TEXT NOT NULL, " +
                "PRIMARY KEY(`singletonId`))",
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `sync_state` " +
                "(`singletonId` INTEGER NOT NULL, `exerciseCursor` INTEGER NOT NULL, " +
                "`updatedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`singletonId`))",
        )
        // The legacy DataStore cursor may be ahead of committed Room data. A full,
        // idempotent replay is the only checkpoint that cannot skip remote changes.
        database.execSQL(
            "INSERT OR REPLACE INTO `sync_state` " +
                "(`singletonId`, `exerciseCursor`, `updatedAtEpochMs`) VALUES (1, 0, 0)",
        )
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `training_locations` (
                `id` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL,
                `isActive` INTEGER NOT NULL, `activeSlot` INTEGER,
                `createdAtEpochMs` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL,
                `revision` INTEGER NOT NULL, `deletedAtEpochMs` INTEGER, PRIMARY KEY(`id`))""",
        )
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_training_locations_activeSlot` ON `training_locations` (`activeSlot`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_training_locations_isActive_deletedAtEpochMs` ON `training_locations` (`isActive`, `deletedAtEpochMs`)")
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `training_location_equipment` (
                `locationId` TEXT NOT NULL, `equipmentSlug` TEXT NOT NULL,
                PRIMARY KEY(`locationId`, `equipmentSlug`),
                FOREIGN KEY(`locationId`) REFERENCES `training_locations`(`id`)
                ON UPDATE NO ACTION ON DELETE CASCADE)""",
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_training_location_equipment_locationId` ON `training_location_equipment` (`locationId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_training_location_equipment_equipmentSlug` ON `training_location_equipment` (`equipmentSlug`)")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `training_plans` (
                `id` TEXT NOT NULL, `ownerProfileId` TEXT NOT NULL, `name` TEXT NOT NULL,
                `description` TEXT NOT NULL, `goal` TEXT NOT NULL, `isActive` INTEGER NOT NULL,
                `activeSlot` TEXT, `isArchived` INTEGER NOT NULL, `sourceTemplateId` TEXT,
                `createdAtEpochMs` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL,
                `revision` INTEGER NOT NULL, `deletedAtEpochMs` INTEGER, PRIMARY KEY(`id`),
                FOREIGN KEY(`ownerProfileId`) REFERENCES `guest_profile`(`id`)
                ON UPDATE NO ACTION ON DELETE CASCADE)""",
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_training_plans_ownerProfileId` ON `training_plans` (`ownerProfileId`)")
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_training_plans_activeSlot` ON `training_plans` (`activeSlot`)")
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_training_plans_ownerProfileId_isArchived_deletedAtEpochMs` " +
                "ON `training_plans` (`ownerProfileId`, `isArchived`, `deletedAtEpochMs`)",
        )
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `plan_weeks` (
                `id` TEXT NOT NULL, `planId` TEXT NOT NULL, `position` INTEGER NOT NULL,
                `title` TEXT NOT NULL, `weekIndex` INTEGER NOT NULL,
                PRIMARY KEY(`id`), FOREIGN KEY(`planId`)
                REFERENCES `training_plans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""",
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_plan_weeks_planId` ON `plan_weeks` (`planId`)")
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_plan_weeks_planId_position` ON `plan_weeks` (`planId`, `position`)")
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `plan_days` (
                `id` TEXT NOT NULL, `weekId` TEXT NOT NULL, `position` INTEGER NOT NULL,
                `title` TEXT NOT NULL, `relativeDayIndex` INTEGER NOT NULL,
                `estimatedDurationMinutes` INTEGER, `notes` TEXT NOT NULL,
                PRIMARY KEY(`id`), FOREIGN KEY(`weekId`)
                REFERENCES `plan_weeks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""",
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_plan_days_weekId` ON `plan_days` (`weekId`)")
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_plan_days_weekId_position` ON `plan_days` (`weekId`, `position`)")
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `plan_blocks` (
                `id` TEXT NOT NULL, `dayId` TEXT NOT NULL, `position` INTEGER NOT NULL,
                `type` TEXT NOT NULL, `title` TEXT NOT NULL, `rounds` INTEGER,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`dayId`) REFERENCES `plan_days`(`id`)
                ON UPDATE NO ACTION ON DELETE CASCADE)""",
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_plan_blocks_dayId` ON `plan_blocks` (`dayId`)")
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_plan_blocks_dayId_position` ON `plan_blocks` (`dayId`, `position`)")
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `plan_exercises` (
                `id` TEXT NOT NULL, `blockId` TEXT NOT NULL, `position` INTEGER NOT NULL,
                `referenceKind` TEXT NOT NULL, `customExerciseId` TEXT, `catalogSource` TEXT,
                `catalogExternalId` TEXT, `catalogExerciseId` TEXT, `snapshotName` TEXT NOT NULL,
                `snapshotTrackingType` TEXT NOT NULL, `snapshotEquipment` TEXT NOT NULL,
                `snapshotPrimaryMuscle` TEXT, `resolutionStatus` TEXT NOT NULL,
                `optional` INTEGER NOT NULL, `notes` TEXT NOT NULL,
                PRIMARY KEY(`id`), FOREIGN KEY(`blockId`) REFERENCES `plan_blocks`(`id`)
                ON UPDATE NO ACTION ON DELETE CASCADE)""",
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_plan_exercises_blockId` ON `plan_exercises` (`blockId`)")
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_plan_exercises_blockId_position` ON `plan_exercises` (`blockId`, `position`)")
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `plan_set_prescriptions` (
                `id` TEXT NOT NULL, `planExerciseId` TEXT NOT NULL, `position` INTEGER NOT NULL,
                `setType` TEXT NOT NULL, `repsMin` INTEGER, `repsMax` INTEGER,
                `durationSeconds` INTEGER, `distanceMeters` REAL, `targetWeightKg` REAL,
                `targetRpe` REAL, `targetRir` INTEGER, `restSeconds` INTEGER,
                `tempoEccentric` TEXT, `tempoBottomPause` TEXT, `tempoConcentric` TEXT,
                `tempoTopPause` TEXT, PRIMARY KEY(`id`),
                FOREIGN KEY(`planExerciseId`) REFERENCES `plan_exercises`(`id`)
                ON UPDATE NO ACTION ON DELETE CASCADE)""",
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_plan_set_prescriptions_planExerciseId` " +
                "ON `plan_set_prescriptions` (`planExerciseId`)",
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_plan_set_prescriptions_planExerciseId_position` " +
                "ON `plan_set_prescriptions` (`planExerciseId`, `position`)",
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "fitness-platform.db")
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
            )
            .build()

    @Provides fun provideProfileDao(db: AppDatabase): GuestProfileDao = db.guestProfileDao()
    @Provides fun provideExerciseDao(db: AppDatabase): ExerciseDao = db.exerciseDao()
    @Provides fun provideWorkoutDao(db: AppDatabase): WorkoutDao = db.workoutDao()
    @Provides fun provideOutboxDao(db: AppDatabase): OutboxDao = db.outboxDao()
    @Provides fun provideExerciseConflictDao(db: AppDatabase): ExerciseConflictDao = db.exerciseConflictDao()
    @Provides fun provideCatalogDao(db: AppDatabase): CatalogDao = db.catalogDao()
    @Provides fun provideSyncStateDao(db: AppDatabase): SyncStateDao = db.syncStateDao()
    @Provides fun provideTrainingLocationDao(db: AppDatabase): TrainingLocationDao = db.trainingLocationDao()
    @Provides fun provideTrainingPlanDao(db: AppDatabase): TrainingPlanDao = db.trainingPlanDao()
    @Provides fun provideDatabaseStartupProbe(db: AppDatabase): DatabaseStartupProbe = RoomDatabaseStartupProbe(db)
}
