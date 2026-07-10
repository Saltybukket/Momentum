package at.fitnessplatform.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.room.withTransaction
import at.fitnessplatform.core.database.AppDatabase
import at.fitnessplatform.core.database.ExerciseDao
import at.fitnessplatform.core.database.GuestProfileDao
import at.fitnessplatform.core.database.OutboxDao
import at.fitnessplatform.core.database.WorkoutDao
import at.fitnessplatform.core.datastore.GuestSessionStore
import at.fitnessplatform.core.model.OutboxOperationType
import at.fitnessplatform.core.network.FitnessApi
import at.fitnessplatform.core.network.GuestSessionRequest
import at.fitnessplatform.core.network.SyncOperationDto
import at.fitnessplatform.core.network.SyncPushRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.nio.charset.StandardCharsets
import java.util.UUID

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val database: AppDatabase,
    private val outboxDao: OutboxDao,
    private val profileDao: GuestProfileDao,
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val sessionStore: GuestSessionStore,
    private val api: FitnessApi,
    private val json: Json,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!sessionStore.isSyncEnabled()) return Result.success()
        val pending = outboxDao.pending()
        if (pending.isEmpty()) return Result.success()
        val profile = profileDao.get() ?: return Result.failure()
        val ids = pending.map { it.id }
        outboxDao.markSyncing(ids)
        return try {
            val token = sessionStore.tokenOrNull() ?: api.createGuestSession(
                idempotencyKey = "guest-session-${profile.id}",
                request = GuestSessionRequest(profile.displayName),
            ).let { session ->
                sessionStore.saveToken(session.guestToken)
                profileDao.markSynced(profile.id, session.profile.userId)
                session.guestToken
            }
            val operations = pending.map { row ->
                val type = OutboxOperationType.valueOf(row.operationType)
                val (entity, action) = when (type) {
                    OutboxOperationType.UPSERT_PROFILE -> "profile" to "UPSERT"
                    OutboxOperationType.UPSERT_EXERCISE -> "exercise" to "UPSERT"
                    OutboxOperationType.DELETE_EXERCISE -> "exercise" to "DELETE"
                    OutboxOperationType.UPSERT_WORKOUT -> "workout" to "UPSERT"
                }
                SyncOperationDto(
                    operationId = row.id,
                    entityType = entity,
                    action = action,
                    payload = json.parseToJsonElement(row.payloadJson) as JsonObject,
                )
            }
            val batchKey = UUID.nameUUIDFromBytes(
                pending.joinToString("|") { it.id }.toByteArray(StandardCharsets.UTF_8),
            ).toString()
            val response = api.pushSync("Bearer $token", batchKey, SyncPushRequest(operations))
            val successfulIds = response.results.map { it.operationId }.toSet()
            val successfulRows = pending.filter { it.id in successfulIds }
            database.withTransaction {
                outboxDao.markSynced(successfulRows.map { it.id })
                val profiles = successfulRows.filter { it.operationType == OutboxOperationType.UPSERT_PROFILE.name }.map { it.aggregateId }
                val exercises = successfulRows.filter {
                    it.operationType == OutboxOperationType.UPSERT_EXERCISE.name ||
                        it.operationType == OutboxOperationType.DELETE_EXERCISE.name
                }.map { it.aggregateId }
                val workouts = successfulRows.filter { it.operationType == OutboxOperationType.UPSERT_WORKOUT.name }.map { it.aggregateId }
                profiles.forEach { profileDao.markSynced(it) }
                if (exercises.isNotEmpty()) exerciseDao.markSynced(exercises)
                if (workouts.isNotEmpty()) workoutDao.markSynced(workouts)
            }
            Result.success()
        } catch (exception: Exception) {
            outboxDao.markFailed(ids, exception.message?.take(500) ?: exception::class.java.simpleName)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    private companion object { const val MAX_RETRIES = 5 }
}
