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
import org.json.JSONArray

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
        PlanScheduleEntity::class,
        PlanDayScheduleRuleEntity::class,
        ScheduledWorkoutOccurrenceEntity::class,
        AvailabilityRuleEntity::class,
        ScheduleOverrideEntity::class,
    ],
    version = 9,
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
    abstract fun calendarDao(): CalendarDao
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

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Room 7 stored a single equipment slug. Room 8 stores a canonical JSON array so a
        // catalog snapshot cannot silently lose additional requirements.
        database.execSQL(
            "UPDATE plan_exercises SET snapshotEquipment = " +
                "CASE WHEN substr(snapshotEquipment, 1, 1) = '[' THEN snapshotEquipment " +
                "ELSE '[\"' || snapshotEquipment || '\"]' END",
        )
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `plan_schedules` (
                `id` TEXT NOT NULL, `ownerProfileId` TEXT NOT NULL, `planId` TEXT NOT NULL,
                `startDate` TEXT NOT NULL, `timeZoneId` TEXT NOT NULL, `isActive` INTEGER NOT NULL,
                `activeSlot` TEXT, `createdAtEpochMs` INTEGER NOT NULL,
                `updatedAtEpochMs` INTEGER NOT NULL, `revision` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`ownerProfileId`) REFERENCES `guest_profile`(`id`)
                ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`planId`) REFERENCES `training_plans`(`id`)
                ON UPDATE NO ACTION ON DELETE CASCADE)""",
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_plan_schedules_ownerProfileId` ON `plan_schedules` (`ownerProfileId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_plan_schedules_planId` ON `plan_schedules` (`planId`)")
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_plan_schedules_activeSlot` ON `plan_schedules` (`activeSlot`)")
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `plan_day_schedule_rules` (
                `id` TEXT NOT NULL, `scheduleId` TEXT NOT NULL, `planDayId` TEXT NOT NULL,
                `dayOfWeek` INTEGER NOT NULL, `defaultStartTime` TEXT,
                `defaultDurationMinutes` INTEGER NOT NULL, `preferredLocationId` TEXT,
                `position` INTEGER NOT NULL, PRIMARY KEY(`id`),
                FOREIGN KEY(`scheduleId`) REFERENCES `plan_schedules`(`id`)
                ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`planDayId`) REFERENCES `plan_days`(`id`)
                ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`preferredLocationId`) REFERENCES `training_locations`(`id`)
                ON UPDATE NO ACTION ON DELETE SET NULL)""",
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_plan_day_schedule_rules_scheduleId` ON `plan_day_schedule_rules` (`scheduleId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_plan_day_schedule_rules_planDayId` ON `plan_day_schedule_rules` (`planDayId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_plan_day_schedule_rules_preferredLocationId` ON `plan_day_schedule_rules` (`preferredLocationId`)")
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_plan_day_schedule_rules_scheduleId_planDayId` ON `plan_day_schedule_rules` (`scheduleId`, `planDayId`)")
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_plan_day_schedule_rules_scheduleId_position` ON `plan_day_schedule_rules` (`scheduleId`, `position`)")
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `scheduled_workout_occurrences` (
                `id` TEXT NOT NULL, `ownerProfileId` TEXT NOT NULL, `scheduleId` TEXT,
                `planId` TEXT, `planDayId` TEXT, `titleSnapshot` TEXT NOT NULL,
                `scheduledLocalDate` TEXT NOT NULL, `scheduledLocalStartTime` TEXT,
                `timeZoneId` TEXT NOT NULL, `plannedDurationMinutes` INTEGER NOT NULL,
                `trainingLocationId` TEXT, `status` TEXT NOT NULL,
                `originalScheduledDate` TEXT, `movedFromOccurrenceId` TEXT,
                `notes` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL,
                `updatedAtEpochMs` INTEGER NOT NULL, `revision` INTEGER NOT NULL,
                `deletedAtEpochMs` INTEGER, PRIMARY KEY(`id`),
                FOREIGN KEY(`ownerProfileId`) REFERENCES `guest_profile`(`id`)
                ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`scheduleId`) REFERENCES `plan_schedules`(`id`)
                ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(`planId`) REFERENCES `training_plans`(`id`)
                ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(`planDayId`) REFERENCES `plan_days`(`id`)
                ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(`trainingLocationId`) REFERENCES `training_locations`(`id`)
                ON UPDATE NO ACTION ON DELETE SET NULL)""",
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_scheduled_workout_occurrences_ownerProfileId` ON `scheduled_workout_occurrences` (`ownerProfileId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_scheduled_workout_occurrences_scheduleId` ON `scheduled_workout_occurrences` (`scheduleId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_scheduled_workout_occurrences_planId` ON `scheduled_workout_occurrences` (`planId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_scheduled_workout_occurrences_planDayId` ON `scheduled_workout_occurrences` (`planDayId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_scheduled_workout_occurrences_trainingLocationId` ON `scheduled_workout_occurrences` (`trainingLocationId`)")
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS " +
                "`index_scheduled_workout_occurrences_ownerProfileId_scheduledLocalDate` " +
                "ON `scheduled_workout_occurrences` (`ownerProfileId`, `scheduledLocalDate`)",
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_scheduled_workout_occurrences_scheduleId_planDayId_scheduledLocalDate` " +
                "ON `scheduled_workout_occurrences` (`scheduleId`, `planDayId`, `scheduledLocalDate`)",
        )
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `availability_rules` (
                `id` TEXT NOT NULL, `ownerProfileId` TEXT NOT NULL, `dayOfWeek` INTEGER NOT NULL,
                `earliestLocalTime` TEXT, `latestLocalTime` TEXT, `maxDurationMinutes` INTEGER,
                `preferredLocationId` TEXT, `enabled` INTEGER NOT NULL, PRIMARY KEY(`id`),
                FOREIGN KEY(`ownerProfileId`) REFERENCES `guest_profile`(`id`)
                ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`preferredLocationId`) REFERENCES `training_locations`(`id`)
                ON UPDATE NO ACTION ON DELETE SET NULL)""",
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_availability_rules_ownerProfileId` ON `availability_rules` (`ownerProfileId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_availability_rules_preferredLocationId` ON `availability_rules` (`preferredLocationId`)")
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_availability_rules_ownerProfileId_dayOfWeek` ON `availability_rules` (`ownerProfileId`, `dayOfWeek`)")
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `schedule_overrides` (
                `id` TEXT NOT NULL, `ownerProfileId` TEXT NOT NULL, `localDate` TEXT NOT NULL,
                `unavailable` INTEGER NOT NULL, `earliestLocalTime` TEXT, `latestLocalTime` TEXT,
                `maxDurationMinutes` INTEGER, `locationId` TEXT, `note` TEXT, PRIMARY KEY(`id`),
                FOREIGN KEY(`ownerProfileId`) REFERENCES `guest_profile`(`id`)
                ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`locationId`) REFERENCES `training_locations`(`id`)
                ON UPDATE NO ACTION ON DELETE SET NULL)""",
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_overrides_ownerProfileId` ON `schedule_overrides` (`ownerProfileId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_overrides_locationId` ON `schedule_overrides` (`locationId`)")
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_schedule_overrides_ownerProfileId_localDate` ON `schedule_overrides` (`ownerProfileId`, `localDate`)")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `plan_day_schedule_rules` RENAME TO `plan_day_schedule_rules_room8`")
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `plan_day_schedule_rules` (
                `id` TEXT NOT NULL, `scheduleId` TEXT NOT NULL, `planDayId` TEXT NOT NULL,
                `dayOfWeek` INTEGER NOT NULL, `defaultStartTime` TEXT,
                `defaultDurationMinutes` INTEGER NOT NULL, `preferredLocationId` TEXT,
                `position` INTEGER NOT NULL, PRIMARY KEY(`id`),
                FOREIGN KEY(`scheduleId`) REFERENCES `plan_schedules`(`id`)
                ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`planDayId`) REFERENCES `plan_days`(`id`)
                ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`preferredLocationId`) REFERENCES `training_locations`(`id`)
                ON UPDATE NO ACTION ON DELETE SET NULL)""",
        )
        database.execSQL(
            "INSERT INTO `plan_day_schedule_rules` SELECT * FROM `plan_day_schedule_rules_room8`",
        )
        database.execSQL("DROP TABLE `plan_day_schedule_rules_room8`")
        database.execSQL("CREATE INDEX `index_plan_day_schedule_rules_scheduleId` ON `plan_day_schedule_rules` (`scheduleId`)")
        database.execSQL("CREATE INDEX `index_plan_day_schedule_rules_planDayId` ON `plan_day_schedule_rules` (`planDayId`)")
        database.execSQL("CREATE INDEX `index_plan_day_schedule_rules_preferredLocationId` ON `plan_day_schedule_rules` (`preferredLocationId`)")
        database.execSQL("CREATE UNIQUE INDEX `index_plan_day_schedule_rules_scheduleId_planDayId` ON `plan_day_schedule_rules` (`scheduleId`, `planDayId`)")
        database.execSQL("CREATE UNIQUE INDEX `index_plan_day_schedule_rules_scheduleId_position` ON `plan_day_schedule_rules` (`scheduleId`, `position`)")

        database.execSQL("ALTER TABLE `scheduled_workout_occurrences` ADD COLUMN `planDayIdSnapshot` TEXT")
        database.execSQL("ALTER TABLE `scheduled_workout_occurrences` ADD COLUMN `planRevisionSnapshot` INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE `scheduled_workout_occurrences` ADD COLUMN `planWeekIndexSnapshot` INTEGER")
        database.execSQL("ALTER TABLE `scheduled_workout_occurrences` ADD COLUMN `requiredEquipmentSnapshotJson` TEXT NOT NULL DEFAULT '[]'")
        database.execSQL("ALTER TABLE `scheduled_workout_occurrences` ADD COLUMN `hasUnavailableExerciseSnapshot` INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE `scheduled_workout_occurrences` ADD COLUMN `originalScheduledStartTime` TEXT")
        database.execSQL("ALTER TABLE `scheduled_workout_occurrences` ADD COLUMN `originType` TEXT NOT NULL DEFAULT 'GENERATED'")
        database.execSQL("ALTER TABLE `scheduled_workout_occurrences` ADD COLUMN `isDetachedOverride` INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE `scheduled_workout_occurrences` ADD COLUMN `sourceOccurrenceId` TEXT")
        database.execSQL(
            "UPDATE `scheduled_workout_occurrences` SET " +
                "`planDayIdSnapshot` = `planDayId`, " +
                "`planRevisionSnapshot` = COALESCE((SELECT p.revision FROM training_plans p " +
                "WHERE p.id = scheduled_workout_occurrences.planId), 0), " +
                "`planWeekIndexSnapshot` = (SELECT w.weekIndex FROM plan_days d " +
                "JOIN plan_weeks w ON w.id = d.weekId WHERE d.id = scheduled_workout_occurrences.planDayId), " +
                "`hasUnavailableExerciseSnapshot` = CASE WHEN EXISTS(SELECT 1 FROM plan_blocks b " +
                "JOIN plan_exercises e ON e.blockId = b.id WHERE b.dayId = scheduled_workout_occurrences.planDayId " +
                "AND e.resolutionStatus != 'RESOLVED') THEN 1 ELSE 0 END, " +
                "`originType` = CASE WHEN originalScheduledDate IS NOT NULL THEN 'MOVED_ONCE' " +
                "WHEN scheduleId IS NULL AND movedFromOccurrenceId IS NOT NULL THEN 'COPIED' " +
                "WHEN scheduleId IS NULL THEN 'AD_HOC' ELSE 'GENERATED' END, " +
                "`isDetachedOverride` = CASE WHEN originalScheduledDate IS NOT NULL OR scheduleId IS NULL THEN 1 ELSE 0 END, " +
                "`sourceOccurrenceId` = CASE WHEN scheduleId IS NULL THEN movedFromOccurrenceId ELSE NULL END",
        )
        val occurrenceDays = mutableListOf<Pair<String, String>>()
        database.query(
            "SELECT id, planDayId FROM scheduled_workout_occurrences WHERE planDayId IS NOT NULL",
        ).use { cursor ->
            while (cursor.moveToNext()) occurrenceDays += cursor.getString(0) to cursor.getString(1)
        }
        occurrenceDays.forEach { (occurrenceId, planDayId) ->
            val equipment = sortedSetOf<String>()
            database.query(
                "SELECT e.snapshotEquipment FROM plan_blocks b " +
                    "JOIN plan_exercises e ON e.blockId = b.id WHERE b.dayId = ?",
                arrayOf(planDayId),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val values = JSONArray(cursor.getString(0))
                    repeat(values.length()) { index -> equipment += values.getString(index) }
                }
            }
            database.execSQL(
                "UPDATE scheduled_workout_occurrences " +
                    "SET requiredEquipmentSnapshotJson = ? WHERE id = ?",
                arrayOf(JSONArray(equipment.toList()).toString(), occurrenceId),
            )
        }
        database.execSQL(
            "DROP INDEX `index_scheduled_workout_occurrences_scheduleId_planDayId_scheduledLocalDate`",
        )
        database.execSQL(
            "CREATE UNIQUE INDEX `index_scheduled_workout_occurrences_scheduleId_planDayIdSnapshot_scheduledLocalDate` " +
                "ON `scheduled_workout_occurrences` (`scheduleId`, `planDayIdSnapshot`, `scheduledLocalDate`)",
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
                MIGRATION_7_8,
                MIGRATION_8_9,
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
    @Provides fun provideCalendarDao(db: AppDatabase): CalendarDao = db.calendarDao()
    @Provides fun provideDatabaseStartupProbe(db: AppDatabase): DatabaseStartupProbe = RoomDatabaseStartupProbe(db)
}
