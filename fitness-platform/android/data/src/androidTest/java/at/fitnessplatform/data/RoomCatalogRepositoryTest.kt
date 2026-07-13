package at.fitnessplatform.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import at.fitnessplatform.core.database.AppDatabase
import at.fitnessplatform.core.model.CatalogFilter
import at.fitnessplatform.core.network.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomCatalogRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var api: FakeFitnessApi
    private lateinit var repository: RoomCatalogRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        api = FakeFitnessApi()
        repository = RoomCatalogRepository(context, database, database.catalogDao(), api, Json { ignoreUnknownKeys = true })
    }
    @After fun tearDown() = database.close()

    @Test fun seedSupportsOfflineFirstStartAndCombinedFilters() = runTest {
        repository.seedIfEmpty()
        assertEquals(3, repository.observeCatalog().first().size)
        assertEquals(1, repository.observeCatalog(CatalogFilter(muscle = "core", equipment = "mat")).first().size)
        assertTrue(repository.observeCatalog(CatalogFilter(muscle = "unknown")).first().isEmpty())
        repository.seedIfEmpty()
        assertEquals(3, repository.observeCatalog().first().size)
    }

    @Test fun failedRefreshIsAtomicAndKeepsSavedCatalog() = runTest {
        repository.seedIfEmpty()
        api.fail = true
        runCatching { repository.refresh() }
        assertEquals(3, repository.observeCatalog().first().size)
    }

    @Test fun invalidSnapshotHashKeepsSavedCatalog() = runTest {
        repository.seedIfEmpty()
        api.tamper = true

        runCatching { repository.refresh() }

        assertEquals(3, repository.observeCatalog().first().size)
    }
}

private class FakeFitnessApi : FitnessApi {
    var fail = false
    var tamper = false
    override suspend fun catalogExercises(muscle: String?, equipment: String?): List<CatalogExerciseDto> {
        if (fail) error("offline")
        return emptyList()
    }
    override suspend fun catalogSnapshot(): CatalogSnapshotDto {
        if (fail) error("offline")
        val snapshot = CatalogSnapshotDto(
            schemaVersion = "1",
            catalogVersion = "test",
            contentHash = "sha256:" + "0".repeat(64),
            publishedAt = "2026-07-12T00:00:00Z",
            batchId = "test-batch",
            muscles = emptyList(),
            equipment = emptyList(),
            exercises = emptyList(),
        )
        val valid = snapshot.copy(contentHash = snapshot.canonicalContentHash(Json))
        return if (tamper) valid.copy(catalogVersion = "tampered") else valid
    }
    override suspend fun catalogMuscles() = emptyList<CatalogFacetDto>()
    override suspend fun catalogEquipment() = emptyList<CatalogFacetDto>()
    override suspend fun createGuestSession(request: GuestSessionRequest): GuestSessionResponse = error("unused")
    override suspend fun pushSync(authorization: String, idempotencyKey: String, request: SyncPushRequest): SyncPushResponse = error("unused")
    override suspend fun pullExercises(authorization: String, cursor: Long, limit: Int): SyncPullResponse = error("unused")
}
