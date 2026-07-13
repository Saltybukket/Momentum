package at.fitnessplatform.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GuestProfileDao {
    @Query("SELECT * FROM guest_profile LIMIT 1")
    fun observe(): Flow<GuestProfileEntity?>

    @Query("SELECT * FROM guest_profile LIMIT 1")
    suspend fun get(): GuestProfileEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: GuestProfileEntity)

    @Update
    suspend fun update(entity: GuestProfileEntity)

    @Query("UPDATE guest_profile SET syncStatus = 'SYNCED', serverId = COALESCE(:serverId, serverId) WHERE id = :id")
    suspend fun markSynced(id: String, serverId: String? = null)
}

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM custom_exercises WHERE deletedAtEpochMs IS NULL ORDER BY updatedAtEpochMs DESC")
    fun observeActive(): Flow<List<CustomExerciseEntity>>

    @Query("SELECT * FROM custom_exercises WHERE id = :id LIMIT 1")
    suspend fun get(id: String): CustomExerciseEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: CustomExerciseEntity)

    @Update
    suspend fun update(entity: CustomExerciseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replace(entity: CustomExerciseEntity)

    @Query("UPDATE custom_exercises SET syncStatus = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("UPDATE custom_exercises SET syncStatus = 'CONFLICT' WHERE id IN (:ids)")
    suspend fun markConflict(ids: List<String>)
}

@Dao
interface ExerciseConflictDao {
    @Query("SELECT * FROM exercise_conflicts WHERE resolutionStatus != 'RESOLVED' ORDER BY detectedAtEpochMs DESC")
    fun observeOpen(): Flow<List<ExerciseConflictEntity>>

    @Query("SELECT * FROM exercise_conflicts WHERE exerciseId = :exerciseId AND resolutionStatus != 'RESOLVED' LIMIT 1")
    suspend fun getOpenForExercise(exerciseId: String): ExerciseConflictEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ExerciseConflictEntity)

    @Query("UPDATE exercise_conflicts SET resolutionStatus = :status, resolvedAtEpochMs = :resolvedAtEpochMs WHERE exerciseId = :exerciseId")
    suspend fun updateStatus(exerciseId: String, status: String, resolvedAtEpochMs: Long?)

    @Query("UPDATE exercise_conflicts SET resolutionStatus = 'RESOLVED', resolvedAtEpochMs = :resolvedAtEpochMs WHERE exerciseId = :exerciseId AND resolutionStatus = 'PENDING_CONFIRMATION'")
    suspend fun markResolutionConfirmed(exerciseId: String, resolvedAtEpochMs: Long)
}

data class WorkoutWithExercises(
    @androidx.room.Embedded val workout: WorkoutEntity,
    @androidx.room.Relation(parentColumn = "id", entityColumn = "workoutId")
    val exercises: List<WorkoutExerciseEntity>,
)

@Dao
interface WorkoutDao {
    @Transaction
    @Query("SELECT * FROM workouts ORDER BY updatedAtEpochMs DESC")
    fun observeAll(): Flow<List<WorkoutWithExercises>>

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id LIMIT 1")
    suspend fun get(id: String): WorkoutWithExercises?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWorkout(entity: WorkoutEntity)

    @Update
    suspend fun updateWorkout(entity: WorkoutEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(entities: List<WorkoutExerciseEntity>)

    @Query("UPDATE workouts SET syncStatus = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)
}

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: OutboxEntity): Long

    @Query("SELECT * FROM sync_outbox WHERE status IN ('PENDING','FAILED') ORDER BY createdAtEpochMs ASC")
    suspend fun pending(): List<OutboxEntity>

    @Query("""SELECT id FROM sync_outbox
        WHERE status IN ('PENDING','FAILED') OR (status = 'SYNCING' AND claimExpiresAtEpochMs <= :now)
        ORDER BY createdAtEpochMs ASC LIMIT :limit""")
    suspend fun claimableIds(now: Long, limit: Int): List<String>

    @Query("""UPDATE sync_outbox SET status = 'SYNCING', claimOwner = :owner,
        claimExpiresAtEpochMs = :expiresAt WHERE id IN (:ids)
        AND (status IN ('PENDING','FAILED') OR (status = 'SYNCING' AND claimExpiresAtEpochMs <= :now))""")
    suspend fun claim(ids: List<String>, owner: String, now: Long, expiresAt: Long)

    @Query("SELECT * FROM sync_outbox WHERE claimOwner = :owner AND status = 'SYNCING' ORDER BY createdAtEpochMs")
    suspend fun claimedBy(owner: String): List<OutboxEntity>

    @Query("""UPDATE sync_outbox SET status = 'PENDING', lastError = NULL,
        claimOwner = NULL, claimExpiresAtEpochMs = NULL
        WHERE status = 'SYNCING' AND claimOwner = :owner""")
    suspend fun releaseClaims(owner: String): Int

    @Transaction
    suspend fun claimBatch(owner: String, now: Long, expiresAt: Long, limit: Int = 100): List<OutboxEntity> {
        val ids = claimableIds(now, limit)
        if (ids.isEmpty()) return emptyList()
        claim(ids, owner, now, expiresAt)
        return claimedBy(owner)
    }

    @Query("UPDATE sync_outbox SET status = 'SYNCED', lastError = NULL, claimOwner = NULL, claimExpiresAtEpochMs = NULL WHERE id IN (:ids) AND status = 'SYNCING' AND claimOwner = :owner")
    suspend fun markSynced(ids: List<String>, owner: String): Int

    @Query("UPDATE sync_outbox SET status = 'FAILED', retryCount = retryCount + 1, lastError = :error, claimOwner = NULL, claimExpiresAtEpochMs = NULL WHERE id IN (:ids) AND status = 'SYNCING' AND claimOwner = :owner")
    suspend fun markFailed(ids: List<String>, owner: String, error: String): Int

    @Query("UPDATE sync_outbox SET status = 'CONFLICT', lastError = 'Conflict requires resolution', claimOwner = NULL, claimExpiresAtEpochMs = NULL WHERE id IN (:ids) AND status = 'SYNCING' AND claimOwner = :owner")
    suspend fun markConflict(ids: List<String>, owner: String): Int

    @Query("DELETE FROM sync_outbox WHERE aggregateId = :aggregateId AND status IN ('PENDING', 'FAILED', 'SYNCING', 'CONFLICT')")
    suspend fun deleteUnacknowledgedForAggregate(aggregateId: String)

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE status IN ('PENDING','FAILED','SYNCING','CONFLICT')")
    fun observePendingCount(): Flow<Int>
}

data class CatalogExerciseWithRelations(
    @androidx.room.Embedded val exercise: CatalogExerciseEntity,
    @androidx.room.Relation(parentColumn = "id", entityColumn = "exerciseId")
    val muscles: List<CatalogExerciseMuscleEntity>,
    @androidx.room.Relation(parentColumn = "id", entityColumn = "exerciseId")
    val equipment: List<CatalogExerciseEquipmentEntity>,
)

@Dao
interface CatalogDao {
    @Transaction
    @Query(
        """SELECT DISTINCT c.* FROM catalog_exercises c
        LEFT JOIN catalog_exercise_muscles m ON m.exerciseId = c.id
        LEFT JOIN catalog_exercise_equipment e ON e.exerciseId = c.id
        WHERE (:query = '' OR c.name LIKE '%' || :query || '%' COLLATE NOCASE)
          AND (:muscle IS NULL OR m.muscleSlug = :muscle)
          AND (:equipment IS NULL OR e.equipmentSlug = :equipment)
        ORDER BY c.name"""
    )
    fun observe(query: String, muscle: String?, equipment: String?): Flow<List<CatalogExerciseWithRelations>>

    @Transaction @Query("SELECT * FROM catalog_exercises WHERE id = :id LIMIT 1")
    fun observeOne(id: String): Flow<CatalogExerciseWithRelations?>

    @Query("SELECT * FROM catalog_muscles ORDER BY name") fun observeMuscles(): Flow<List<CatalogMuscleEntity>>
    @Query("SELECT * FROM catalog_equipment ORDER BY name") fun observeEquipment(): Flow<List<CatalogEquipmentEntity>>
    @Query("SELECT COUNT(*) FROM catalog_exercises") suspend fun count(): Int
    @Query("SELECT * FROM catalog_metadata WHERE singletonId = 1") suspend fun metadata(): CatalogMetadataEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putMetadata(row: CatalogMetadataEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertExercises(rows: List<CatalogExerciseEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertMuscles(rows: List<CatalogMuscleEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertEquipment(rows: List<CatalogEquipmentEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertExerciseMuscles(rows: List<CatalogExerciseMuscleEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertExerciseEquipment(rows: List<CatalogExerciseEquipmentEntity>)
    @Query("DELETE FROM catalog_exercises") suspend fun deleteExercises()
    @Query("DELETE FROM catalog_muscles") suspend fun deleteMuscles()
    @Query("DELETE FROM catalog_equipment") suspend fun deleteEquipment()
    @Query("DELETE FROM catalog_metadata") suspend fun deleteMetadata()
}

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_state WHERE singletonId = 1")
    suspend fun get(): SyncStateEntity?

    @Query("SELECT COALESCE((SELECT exerciseCursor FROM sync_state WHERE singletonId = 1), 0)")
    suspend fun exerciseCursor(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(state: SyncStateEntity)
}

data class TrainingLocationWithEquipment(
    @androidx.room.Embedded val location: TrainingLocationEntity,
    @androidx.room.Relation(parentColumn = "id", entityColumn = "locationId")
    val equipment: List<TrainingLocationEquipmentEntity>,
)

@Dao
interface TrainingLocationDao {
    @Transaction
    @Query("SELECT * FROM training_locations WHERE deletedAtEpochMs IS NULL ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<TrainingLocationWithEquipment>>

    @Transaction
    @Query("SELECT * FROM training_locations WHERE activeSlot = 1 AND deletedAtEpochMs IS NULL LIMIT 1")
    fun observeActive(): Flow<TrainingLocationWithEquipment?>

    @Transaction
    @Query("SELECT * FROM training_locations WHERE activeSlot = 1 AND deletedAtEpochMs IS NULL LIMIT 1")
    suspend fun active(): TrainingLocationWithEquipment?

    @Transaction
    @Query("SELECT * FROM training_locations WHERE id = :id LIMIT 1")
    suspend fun get(id: String): TrainingLocationWithEquipment?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(location: TrainingLocationEntity)

    @Update
    suspend fun update(location: TrainingLocationEntity): Int

    @Upsert
    suspend fun upsert(location: TrainingLocationEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEquipment(rows: List<TrainingLocationEquipmentEntity>)

    @Query("DELETE FROM training_location_equipment WHERE locationId = :id")
    suspend fun deleteEquipment(id: String)

    @Query("UPDATE training_locations SET isActive = 0, activeSlot = NULL WHERE activeSlot = 1")
    suspend fun clearActive()

    @Query("UPDATE training_locations SET isActive = 1, activeSlot = 1, updatedAtEpochMs = :now, revision = revision + 1 WHERE id = :id AND deletedAtEpochMs IS NULL")
    suspend fun activate(id: String, now: Long): Int

    @Query("SELECT id FROM training_locations WHERE deletedAtEpochMs IS NULL AND id != :excludedId ORDER BY createdAtEpochMs, id LIMIT 1")
    suspend fun replacementId(excludedId: String): String?

    @Query("UPDATE training_locations SET deletedAtEpochMs = :now, updatedAtEpochMs = :now, revision = revision + 1, isActive = 0, activeSlot = NULL WHERE id = :id AND deletedAtEpochMs IS NULL")
    suspend fun softDeleteRow(id: String, now: Long): Int

    @Query("SELECT COUNT(*) FROM training_locations WHERE deletedAtEpochMs IS NULL")
    suspend fun countActiveRows(): Int

    @Query("SELECT COUNT(*) FROM training_location_equipment WHERE locationId = :id")
    suspend fun equipmentCount(id: String): Int

    @Transaction
    suspend fun replaceEquipment(id: String, slugs: Set<String>) {
        checkNotNull(get(id)) { "Training location does not exist." }
        deleteEquipment(id)
        val rows = slugs.distinct().map { TrainingLocationEquipmentEntity(id, it) }
        if (rows.isNotEmpty()) insertEquipment(rows)
    }

    @Transaction
    suspend fun setActive(id: String, now: Long) {
        checkNotNull(get(id)?.takeIf { it.location.deletedAtEpochMs == null }) { "Training location does not exist." }
        clearActive()
        check(activate(id, now) == 1) { "Training location could not be activated." }
    }

    @Transaction
    suspend fun softDelete(id: String, now: Long) {
        val current = get(id) ?: return
        if (current.location.deletedAtEpochMs != null) return
        val wasActive = current.location.isActive
        check(softDeleteRow(id, now) == 1)
        if (wasActive) replacementId(id)?.let { activate(it, now) }
    }
}
