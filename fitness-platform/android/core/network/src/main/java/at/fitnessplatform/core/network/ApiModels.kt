package at.fitnessplatform.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable data class GuestSessionRequest(@SerialName("display_name") val displayName: String)
@Serializable data class ProfileDto(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("unit_system") val unitSystem: String,
    @SerialName("onboarding_status") val onboardingStatus: String,
    @SerialName("sync_status") val syncStatus: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)
@Serializable data class GuestSessionResponse(
    @SerialName("guest_token") val guestToken: String,
    @SerialName("token_type") val tokenType: String,
    val profile: ProfileDto,
    @SerialName("expires_in_seconds") val expiresInSeconds: Int,
    @SerialName("development_only") val developmentOnly: Boolean,
)
@Serializable data class SyncOperationDto(
    @SerialName("operation_id") val operationId: String,
    @SerialName("entity_type") val entityType: String,
    val action: String,
    val payload: JsonObject,
)
@Serializable data class SyncPushRequest(val operations: List<SyncOperationDto>)
@Serializable data class SyncResultDto(
    @SerialName("operation_id") val operationId: String,
    @SerialName("aggregate_id") val aggregateId: String,
    val status: String,
    @SerialName("server_updated_at") val serverUpdatedAt: String,
    val revision: Long? = null,
    @SerialName("remote_exercise") val remoteExercise: ExerciseDto? = null,
)
@Serializable data class SyncPushResponse(val results: List<SyncResultDto>)
@Serializable data class ExerciseDto(
    val id: String,
    @SerialName("owner_user_id") val ownerUserId: String,
    val name: String, val description: String,
    @SerialName("primary_muscle_group") val primaryMuscleGroup: String,
    val equipment: String,
    @SerialName("tracking_type") val trackingType: String,
    val notes: String, @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    val revision: Long, @SerialName("deleted_at") val deletedAt: String? = null,
)
@Serializable data class ExerciseChangeDto(val cursor: Long, val deleted: Boolean, val exercise: ExerciseDto)
@Serializable data class SyncPullResponse(
    val changes: List<ExerciseChangeDto>,
    @SerialName("next_cursor") val nextCursor: Long,
    @SerialName("has_more") val hasMore: Boolean,
)
