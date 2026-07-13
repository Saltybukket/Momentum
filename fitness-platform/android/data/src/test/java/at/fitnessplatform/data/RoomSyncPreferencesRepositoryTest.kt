package at.fitnessplatform.data

import at.fitnessplatform.core.database.OutboxDao
import at.fitnessplatform.core.datastore.GuestSessionStore
import at.fitnessplatform.domain.SyncEnqueuer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RoomSyncPreferencesRepositoryTest {
    @Test
    fun `explicit identity reset re-enqueues sync only after credentials are replaced`() = runTest {
        val sessionStore = mockk<GuestSessionStore>()
        val outboxDao = mockk<OutboxDao>()
        val enqueuer = mockk<SyncEnqueuer>(relaxed = true)
        every { sessionStore.syncEnabled } returns flowOf(true)
        every { outboxDao.observePendingCount() } returns flowOf(3)
        coEvery { sessionStore.resetCredentialsForNewIdentity() } returns Unit
        coEvery { sessionStore.isSyncEnabled() } returns true
        val repository = RoomSyncPreferencesRepository(sessionStore, outboxDao, enqueuer)

        repository.resetCredentialsForNewIdentity()

        coVerify(exactly = 1) { sessionStore.resetCredentialsForNewIdentity() }
        verify(exactly = 1) { enqueuer.enqueue() }
        coVerifyOrder {
            sessionStore.resetCredentialsForNewIdentity()
            sessionStore.isSyncEnabled()
            enqueuer.enqueue()
        }
    }

    @Test
    fun `identity reset does not opt a user into sync`() = runTest {
        val sessionStore = mockk<GuestSessionStore>()
        val outboxDao = mockk<OutboxDao>()
        val enqueuer = mockk<SyncEnqueuer>(relaxed = true)
        every { sessionStore.syncEnabled } returns flowOf(false)
        every { outboxDao.observePendingCount() } returns flowOf(0)
        coEvery { sessionStore.resetCredentialsForNewIdentity() } returns Unit
        coEvery { sessionStore.isSyncEnabled() } returns false
        val repository = RoomSyncPreferencesRepository(sessionStore, outboxDao, enqueuer)

        repository.resetCredentialsForNewIdentity()

        verify(exactly = 0) { enqueuer.enqueue() }
    }
}
