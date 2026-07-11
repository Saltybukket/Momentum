package at.fitnessplatform.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.room.withTransaction
import at.fitnessplatform.core.database.AppDatabase
import at.fitnessplatform.core.database.ExerciseDao
import at.fitnessplatform.core.database.ExerciseConflictDao
import at.fitnessplatform.core.database.ExerciseConflictEntity
import at.fitnessplatform.core.database.ExerciseConflictSnapshot
import at.fitnessplatform.core.database.GuestProfileDao
import at.fitnessplatform.core.database.GuestProfileEntity
import at.fitnessplatform.core.database.OutboxDao
import at.fitnessplatform.core.database.OutboxEntity
import at.fitnessplatform.core.database.WorkoutDao
import at.fitnessplatform.core.database.toConflictSnapshot
import at.fitnessplatform.core.datastore.GuestSessionStore
import at.fitnessplatform.core.model.OutboxOperationType
import at.fitnessplatform.core.model.Clock
import at.fitnessplatform.core.model.ConflictResolutionStatus
import at.fitnessplatform.core.model.ExerciseConflictType
import at.fitnessplatform.core.network.FitnessApi
import at.fitnessplatform.core.network.GuestSessionRequest
import at.fitnessplatform.core.network.SyncOperationDto
import at.fitnessplatform.core.network.SyncPushRequest
import at.fitnessplatform.core.network.ExerciseChangeDto
import at.fitnessplatform.core.network.ExerciseDto
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.encodeToString
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.time.Instant

@HiltWorker
@Suppress("LongParameterList", "TooGenericExceptionCaught", "SwallowedException")
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val database: AppDatabase,
    private val outboxDao: OutboxDao,
    private val profileDao: GuestProfileDao,
    private val exerciseDao: ExerciseDao,
    private val conflictDao: ExerciseConflictDao,
    private val workoutDao: WorkoutDao,
    private val sessionStore: GuestSessionStore,
    private val api: FitnessApi,
    private val json: Json,
    private val clock: Clock,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!sessionStore.isSyncEnabled()) return Result.success()
        val pending = outboxDao.pending()
        return when {
            pending.isEmpty() -> pullOnly()
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
        markSuccessful(pending, response.results.filter { it.status == "SYNCED" }.map { it.operationId }.toSet())
        val conflicts = response.results.filter { it.status == "CONFLICT" }
        if (conflicts.isNotEmpty()) recordConflicts(conflicts)
        pullExercises(token)
        Result.success()
    } catch (exception: Exception) {
        outboxDao.markFailed(ids, exception.message?.take(500) ?: exception::class.java.simpleName)
        if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
    }

    private suspend fun pullOnly(): Result = try {
        val profile = profileDao.get() ?: return Result.success()
        pullExercises(tokenFor(profile))
        Result.success()
    } catch (exception: Exception) {
        if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
    }

    private suspend fun pullExercises(token: String) {
        var cursor = sessionStore.exerciseCursor()
        do {
            val page = api.pullExercises("Bearer $token", cursor)
            database.withTransaction {
                for (change in page.changes) applyRemoteChange(change)
                sessionStore.saveExerciseCursor(page.nextCursor)
            }
            cursor = page.nextCursor
        } while (page.hasMore)
    }

    private suspend fun applyRemoteChange(change: ExerciseChangeDto) {
        val remote = change.exercise
        val local = exerciseDao.get(remote.id)
        if (local?.syncStatus in setOf("PENDING", "SYNCING", "CONFLICT")) return
        exerciseDao.replace(
            at.fitnessplatform.core.database.CustomExerciseEntity(
                id = remote.id,
                ownerProfileId = local?.ownerProfileId ?: profileDao.get()?.id ?: return,
                name = remote.name,
                description = remote.description,
                primaryMuscleGroup = remote.primaryMuscleGroup,
                requiredEquipment = remote.equipment,
                trackingType = remote.trackingType,
                notes = remote.notes,
                createdAtEpochMs = Instant.parse(remote.createdAt).toEpochMilli(),
                updatedAtEpochMs = Instant.parse(remote.updatedAt).toEpochMilli(),
                syncStatus = "SYNCED",
                serverId = remote.id,
                conflictVersion = remote.revision,
                deletedAtEpochMs = remote.deletedAt?.let { Instant.parse(it).toEpochMilli() },
            ),
        )
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
            exercises.forEach { conflictDao.markResolutionConfirmed(it, clock.nowEpochMs()) }
            if (workouts.isNotEmpty()) workoutDao.markSynced(workouts)
        }
    }

    private suspend fun recordConflicts(results: List<at.fitnessplatform.core.network.SyncResultDto>) {
        database.withTransaction {
            val aggregateIds = results.map { it.aggregateId }
            outboxDao.markConflict(aggregateIds)
            exerciseDao.markConflict(aggregateIds)
            results.forEach { result ->
                val remote = result.remoteExercise ?: return@forEach
                val local = exerciseDao.get(result.aggregateId) ?: return@forEach
                val type = when {
                    remote.deletedAt != null && local.deletedAtEpochMs == null -> ExerciseConflictType.REMOTE_DELETED_LOCAL_MODIFIED
                    remote.deletedAt == null && local.deletedAtEpochMs != null -> ExerciseConflictType.LOCAL_DELETED_REMOTE_MODIFIED
                    local.conflictVersion != null -> ExerciseConflictType.BOTH_MODIFIED
                    else -> ExerciseConflictType.REVISION_MISMATCH
                }
                conflictDao.upsert(
                    ExerciseConflictEntity(
                        id = "exercise-conflict-${result.aggregateId}",
                        exerciseId = result.aggregateId,
                        conflictType = type.name,
                        localRevision = local.conflictVersion,
                        remoteRevision = remote.revision,
                        localSnapshotJson = json.encodeToString(local.toConflictSnapshot()),
                        remoteSnapshotJson = json.encodeToString(remote.toConflictSnapshot(local.ownerProfileId)),
                        detectedAtEpochMs = Instant.parse(remote.updatedAt).toEpochMilli(),
                        resolutionStatus = ConflictResolutionStatus.OPEN.name,
                        resolvedAtEpochMs = null,
                    ),
                )
            }
        }
    }

    private fun ExerciseDto.toConflictSnapshot(ownerProfileId: String) = ExerciseConflictSnapshot(
        id = id,
        ownerProfileId = ownerProfileId,
        name = name,
        description = description,
        primaryMuscleGroup = primaryMuscleGroup,
        requiredEquipment = equipment,
        trackingType = trackingType,
        notes = notes,
        createdAtEpochMs = Instant.parse(createdAt).toEpochMilli(),
        updatedAtEpochMs = Instant.parse(updatedAt).toEpochMilli(),
        revision = revision,
        deletedAtEpochMs = deletedAt?.let { Instant.parse(it).toEpochMilli() },
    )

    private companion object { const val MAX_RETRIES = 5 }
}
