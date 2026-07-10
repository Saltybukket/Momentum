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
)
@Serializable data class SyncPushResponse(val results: List<SyncResultDto>)
