package at.fitnessplatform.core.model

sealed interface DomainEvent {
    val eventId: String
    val occurredAtEpochMs: Long
}

data class GuestProfileCreated(
    override val eventId: String,
    override val occurredAtEpochMs: Long,
    val profileId: String,
) : DomainEvent

data class ExerciseCreated(
    override val eventId: String,
    override val occurredAtEpochMs: Long,
    val exerciseId: String,
) : DomainEvent

data class WorkoutCreated(
    override val eventId: String,
    override val occurredAtEpochMs: Long,
    val workoutId: String,
) : DomainEvent

data class WorkoutStarted(
    override val eventId: String,
    override val occurredAtEpochMs: Long,
    val workoutId: String,
) : DomainEvent

data class WorkoutCompleted(
    override val eventId: String,
    override val occurredAtEpochMs: Long,
    val workoutId: String,
) : DomainEvent

data class SyncOperationQueued(
    override val eventId: String,
    override val occurredAtEpochMs: Long,
    val operationId: String,
) : DomainEvent

data class SyncOperationCompleted(
    override val eventId: String,
    override val occurredAtEpochMs: Long,
    val operationId: String,
) : DomainEvent
