package at.fitnessplatform.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.room.withTransaction
import at.fitnessplatform.core.database.AppDatabase
import at.fitnessplatform.core.database.ExerciseDao
import at.fitnessplatform.core.database.GuestProfileDao
import at.fitnessplatform.core.database.GuestProfileEntity
import at.fitnessplatform.core.database.OutboxDao
import at.fitnessplatform.core.database.OutboxEntity
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
        return when {
            pending.isEmpty() -> Result.success()
            else -> {
                val profile = profileDao.get()
                if (profile == null) {
                    Result.failure()
                } else {
                    val ids = pending.map { it.id }
                    outboxDao.markSyncing(ids)
                    synchronize(profile, pending, ids)
                }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun synchronize(
        profile: GuestProfileEntity,
        pending: List<OutboxEntity>,
        ids: List<String>,
    ): Result = try {
        val token = tokenFor(profile)
        val operations = pending.map(::toSyncOperation)
        val batchKey = UUID.nameUUIDFromBytes(
            pending.joinToString("|") { it.id }.toByteArray(StandardCharsets.UTF_8),
        ).toString()
        val response = api.pushSync("Bearer $token", batchKey, SyncPushRequest(operations))
        markSuccessful(pending, response.results.map { it.operationId }.toSet())
        Result.success()
    } catch (exception: Exception) {
        outboxDao.markFailed(ids, exception.message?.take(500) ?: exception::class.java.simpleName)
        if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
    }

    private suspend fun tokenFor(profile: GuestProfileEntity): String = sessionStore.tokenOrNull()
        ?: api.createGuestSession(
            idempotencyKey = "guest-session-${profile.id}",
            request = GuestSessionRequest(profile.displayName),
        ).let { session ->
            sessionStore.saveToken(session.guestToken)
            profileDao.markSynced(profile.id, session.profile.userId)
            session.guestToken
        }

    private fun toSyncOperation(row: OutboxEntity): SyncOperationDto {
        val (entity, action) = when (OutboxOperationType.valueOf(row.operationType)) {
            OutboxOperationType.UPSERT_PROFILE -> "profile" to "UPSERT"
            OutboxOperationType.UPSERT_EXERCISE -> "exercise" to "UPSERT"
            OutboxOperationType.DELETE_EXERCISE -> "exercise" to "DELETE"
            OutboxOperationType.UPSERT_WORKOUT -> "workout" to "UPSERT"
        }
        return SyncOperationDto(
            operationId = row.id,
            entityType = entity,
            action = action,
            payload = json.parseToJsonElement(row.payloadJson) as JsonObject,
        )
    }

    private suspend fun markSuccessful(pending: List<OutboxEntity>, successfulIds: Set<String>) {
        val successfulRows = pending.filter { it.id in successfulIds }
        database.withTransaction {
            outboxDao.markSynced(successfulRows.map { it.id })
            val profiles = successfulRows
                .filter { it.operationType == OutboxOperationType.UPSERT_PROFILE.name }
                .map { it.aggregateId }
            val exercises = successfulRows.filter {
                it.operationType == OutboxOperationType.UPSERT_EXERCISE.name ||
                    it.operationType == OutboxOperationType.DELETE_EXERCISE.name
            }.map { it.aggregateId }
            val workouts = successfulRows
                .filter { it.operationType == OutboxOperationType.UPSERT_WORKOUT.name }
                .map { it.aggregateId }
            profiles.forEach { profileDao.markSynced(it) }
            if (exercises.isNotEmpty()) exerciseDao.markSynced(exercises)
            if (workouts.isNotEmpty()) workoutDao.markSynced(workouts)
        }
    }

    private companion object { const val MAX_RETRIES = 5 }
}
