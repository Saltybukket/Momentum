package at.fitnessplatform.core.network

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Query

interface FitnessApi {
    @POST("api/v1/guest-sessions")
    suspend fun createGuestSession(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: GuestSessionRequest,
    ): GuestSessionResponse

    @POST("api/v1/sync/push")
    suspend fun pushSync(
        @Header("Authorization") authorization: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: SyncPushRequest,
    ): SyncPushResponse

    @GET("api/v1/sync/exercises")
    suspend fun pullExercises(
        @Header("Authorization") authorization: String,
        @Query("cursor") cursor: Long,
        @Query("limit") limit: Int = 100,
    ): SyncPullResponse
}
