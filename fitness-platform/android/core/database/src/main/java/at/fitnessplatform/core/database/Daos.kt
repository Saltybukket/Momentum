package at.fitnessplatform.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
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

    @Query("SELECT * FROM sync_outbox WHERE status IN ('PENDING','FAILED') ORDER BY createdAtEpochMs ASC LIMIT :limit")
    suspend fun pending(limit: Int = 100): List<OutboxEntity>

    @Query("UPDATE sync_outbox SET status = 'SYNCING' WHERE id IN (:ids)")
    suspend fun markSyncing(ids: List<String>)

    @Query("UPDATE sync_outbox SET status = 'SYNCED', lastError = NULL WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("UPDATE sync_outbox SET status = 'FAILED', retryCount = retryCount + 1, lastError = :error WHERE id IN (:ids)")
    suspend fun markFailed(ids: List<String>, error: String)

    @Query("UPDATE sync_outbox SET status = 'CONFLICT', lastError = 'Conflict requires resolution' WHERE aggregateId IN (:aggregateIds) AND status = 'SYNCING'")
    suspend fun markConflict(aggregateIds: List<String>)

    @Query("DELETE FROM sync_outbox WHERE aggregateId = :aggregateId AND status IN ('PENDING', 'FAILED', 'SYNCING', 'CONFLICT')")
    suspend fun deleteUnacknowledgedForAggregate(aggregateId: String)

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE status IN ('PENDING','FAILED')")
    fun observePendingCount(): Flow<Int>
}
