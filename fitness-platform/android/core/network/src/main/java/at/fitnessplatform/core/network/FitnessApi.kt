package at.fitnessplatform.core.network

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Query

interface FitnessApi {
    @GET("api/v1/catalog/snapshot")
    suspend fun catalogSnapshot(): CatalogSnapshotDto
    @GET("api/v1/catalog/exercises")
    suspend fun catalogExercises(
        @Query("muscle") muscle: String? = null,
        @Query("equipment") equipment: String? = null,
    ): List<CatalogExerciseDto>

    @GET("api/v1/catalog/muscles") suspend fun catalogMuscles(): List<CatalogFacetDto>
    @GET("api/v1/catalog/equipment") suspend fun catalogEquipment(): List<CatalogFacetDto>

    @POST("api/v1/guest-sessions")
    suspend fun createGuestSession(
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
