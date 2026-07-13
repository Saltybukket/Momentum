package at.fitnessplatform.core.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import at.fitnessplatform.core.database.AppDatabase
import at.fitnessplatform.core.database.ExerciseConflictDao
import at.fitnessplatform.core.database.ExerciseDao
import at.fitnessplatform.core.database.GuestProfileDao
import at.fitnessplatform.core.database.GuestProfileEntity
import at.fitnessplatform.core.database.OutboxDao
import at.fitnessplatform.core.database.OutboxEntity
import at.fitnessplatform.core.database.SyncStateDao
import at.fitnessplatform.core.database.WorkoutDao
import at.fitnessplatform.core.datastore.GuestCredentialBlockedException
import at.fitnessplatform.core.datastore.GuestCredentialState
import at.fitnessplatform.core.datastore.GuestSessionStore
import at.fitnessplatform.core.network.FitnessApi
import at.fitnessplatform.core.network.GuestSessionResponse
import at.fitnessplatform.core.network.ProfileDto
import at.fitnessplatform.core.testing.FakeClock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.HttpException
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SyncWorkerTest {
    private lateinit var context: Context
    private lateinit var outboxDao: OutboxDao
    private lateinit var profileDao: GuestProfileDao
    private lateinit var sessionStore: GuestSessionStore
    private lateinit var api: FitnessApi

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        outboxDao = mockk(relaxed = true)
        profileDao = mockk(relaxed = true)
        sessionStore = mockk(relaxed = true)
        api = mockk(relaxed = true)
        coEvery { sessionStore.isSyncEnabled() } returns true
        coEvery { profileDao.get() } returns profile()
    }

    @Test
    fun rejectedBearerClearsOnlyTokenAndRetries() = runTest {
        coEvery { outboxDao.claimBatch(any(), any(), any(), any()) } returns listOf(outbox())
        coEvery { sessionStore.tokenOrNull() } returns "expired-token"
        coEvery { api.pushSync(any(), any(), any()) } throws httpError(401)

        val result = worker().doWork()

        assertResult(ListenableWorker.Result.retry(), result)
        coVerify(exactly = 1) { sessionStore.clearToken() }
        coVerify(exactly = 0) { sessionStore.markRecoveryRejected() }
        coVerify { outboxDao.markFailed(listOf("operation-1"), "AUTH_REJECTED") }
    }

    @Test
    fun missingBearerRecoversWithExistingProofAndStoresNewToken() = runTest {
        coEvery { outboxDao.claimBatch(any(), any(), any(), any()) } returns emptyList()
        coEvery { sessionStore.isSyncEnabled() } returnsMany listOf(true, true, true, true, false)
        coEvery { sessionStore.tokenOrNull() } returns null
        coEvery { sessionStore.bootstrapCredentials() } returns ("stable-installation" to "stable-proof")
        coEvery { api.createGuestSession(any()) } returns guestSession()

        val result = worker().doWork()

        assertResult(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { api.createGuestSession(match { it.installationId == "stable-installation" && it.recoverySecret == "stable-proof" }) }
        coVerify(exactly = 1) { sessionStore.saveToken("recovered-token") }
    }

    @Test
    fun rejectedRecoveryProofFailsWithoutRetryAndPersistsBlockedState() = runTest {
        coEvery { outboxDao.claimBatch(any(), any(), any(), any()) } returns emptyList()
        coEvery { sessionStore.tokenOrNull() } returns null
        coEvery { sessionStore.bootstrapCredentials() } returns ("known-installation" to "wrong-proof")
        coEvery { api.createGuestSession(any()) } throws httpError(401)

        val result = worker().doWork()

        assertResult(ListenableWorker.Result.failure(), result)
        coVerify(exactly = 1) { sessionStore.markRecoveryRejected() }
        coVerify(exactly = 0) { sessionStore.clearToken() }
    }

    @Test
    fun repeatedWorkerDoesNotRetryBlockedRecoveryOverNetwork() = runTest {
        coEvery { outboxDao.claimBatch(any(), any(), any(), any()) } returns emptyList()
        coEvery { sessionStore.tokenOrNull() } returns null
        coEvery { sessionStore.bootstrapCredentials() } throws
            GuestCredentialBlockedException(GuestCredentialState.RECOVERY_REJECTED)

        val result = worker().doWork()

        assertResult(ListenableWorker.Result.failure(), result)
        coVerify(exactly = 0) { api.createGuestSession(any()) }
    }

    @Test
    fun consentRevokedAfterClaimReleasesOnlyWorkersClaims() = runTest {
        coEvery { outboxDao.claimBatch(any(), any(), any(), any()) } returns listOf(outbox())
        coEvery { sessionStore.isSyncEnabled() } returnsMany listOf(true, false)

        val result = worker().doWork()

        assertResult(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { outboxDao.releaseClaims(any()) }
        coVerify(exactly = 0) { api.pushSync(any(), any(), any()) }
    }

    @Test
    fun consentRevokedDuringBootstrapReleasesClaimWithoutRetryPenalty() = runTest {
        coEvery { outboxDao.claimBatch(any(), any(), any(), any()) } returns listOf(outbox())
        coEvery { sessionStore.isSyncEnabled() } returnsMany listOf(true, true, true, false)
        coEvery { sessionStore.tokenOrNull() } returns null
        coEvery { sessionStore.bootstrapCredentials() } returns ("stable-installation" to "stable-proof")

        val result = worker().doWork()

        assertResult(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { outboxDao.releaseClaims(any()) }
        coVerify(exactly = 0) { outboxDao.markFailed(any(), any()) }
        coVerify(exactly = 0) { api.createGuestSession(any()) }
    }

    @Test
    fun cancellationDuringPushReleasesClaimWithoutMarkingFailure() = runTest {
        coEvery { outboxDao.claimBatch(any(), any(), any(), any()) } returns listOf(outbox())
        coEvery { sessionStore.tokenOrNull() } returns "current-token"
        coEvery { api.pushSync(any(), any(), any()) } coAnswers { awaitCancellation() }
        val worker = worker()

        val running = launch { worker.doWork() }
        advanceUntilIdle()
        running.cancelAndJoin()

        coVerify(atLeast = 1) { outboxDao.releaseClaims(any()) }
        coVerify(exactly = 0) { outboxDao.markFailed(any(), any()) }
    }

    private fun worker(): SyncWorker {
        val database = mockk<AppDatabase>(relaxed = true)
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker = SyncWorker(
                appContext,
                workerParameters,
                database,
                outboxDao,
                profileDao,
                mockk<ExerciseDao>(relaxed = true),
                mockk<ExerciseConflictDao>(relaxed = true),
                mockk<WorkoutDao>(relaxed = true),
                mockk<SyncStateDao>(relaxed = true),
                sessionStore,
                api,
                Json,
                FakeClock(),
            )
        }
        return TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(factory)
            .build()
    }

    private fun assertResult(expected: ListenableWorker.Result, actual: ListenableWorker.Result) {
        assertEquals(expected.toString(), actual.toString())
    }

    private fun profile() = GuestProfileEntity(
        "profile-1",
        "Guest",
        1,
        "METRIC",
        "COMPLETED",
        "PENDING",
        null,
        null,
    )

    private fun outbox() = OutboxEntity(
        "operation-1",
        "profile-1",
        "UPSERT_PROFILE",
        "{}",
        1,
        "PENDING",
        0,
        null,
    )

    private fun guestSession() = GuestSessionResponse(
        "recovered-token",
        "bearer",
        ProfileDto(
            "server-profile",
            "Guest",
            "METRIC",
            "COMPLETED",
            "SYNCED",
            Instant.EPOCH.toString(),
            Instant.EPOCH.toString(),
        ),
        3600,
        true,
        recovered = true,
    )

    private fun httpError(code: Int) = HttpException(
        Response.error<Any>(code, "rejected".toResponseBody()),
    )
}
